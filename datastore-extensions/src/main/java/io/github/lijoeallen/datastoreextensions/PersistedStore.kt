package io.github.lijoeallen.datastoreextensions

import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.reflect.KClass
import kotlin.reflect.KProperty0

/** 两个声明映射到同一存储 key（默认值不同）时抛出。编译期查重覆盖同模块，跨模块场景由本异常兜底 */
class PersistedKeyConflictException(key: String, first: Any?, second: Any?) :
    IllegalStateException(
        "存储 key 冲突：'$key' 同时被默认值 $first 与 $second 的两个声明使用；" +
            "请检查 @Persisted key/作用域（顶层不同文件、不同模块间无法编译期查重）"
    )

/**
 * @Persisted 持久化属性的 DataStore 后端。
 *
 * 设计：内存 MutableState 表（保证 Compose 同步读与重组）+ DataStore 异步落盘 +
 * Flow 收集器把磁盘数据同步回内存表。首次加载由 App 启动时 awaitFirstLoad() 完成，
 * 之后属性读写与 SharedPreferences 版一样是同步语义。
 *
 * 失败分类（issue #4）：
 * - 未 attach 即读写 → IllegalStateException（开发错误，快速失败）
 * - 磁盘数据损坏/IO 失败 → [attach] 的 onCorruption 回调；未传回调则原样抛出（默认 fail-fast）。
 *   回调模式下进入降级态（[isDegraded]）：内存表继续以默认值服务读，写操作失败并回调
 */
object PersistedStore {

    // key = 完整存储 key。保证委托/插件生成的访问器在每次重组后仍持有同一 MutableState 实例。
    private val states = mutableMapOf<String, MutableState<Any?>>()

    // key → 声明处默认值（首次访问时登记），reset 以此回默认值（issue #3）。
    // 经 write 路径先于 getter 创建的状态用 unsetDefault 占位，首次 getter 访问时补登记真实默认值
    private val defaults = mutableMapOf<String, Any?>()
    private val unsetDefault = Any()

    // 已请求删除、等待磁盘确认的 key：Flow 回灌时跳过，避免旧值短暂回灌
    private val pendingRemovals = mutableSetOf<String>()

    @Volatile
    private var store: DataStore<Preferences>? = null

    @Volatile
    private var scope: CoroutineScope? = null

    @Volatile
    private var lastPrefs: Preferences? = null

    @Volatile
    private var onCorruption: ((Throwable) -> Unit)? = null

    /** 磁盘读取/写入发生失败且回调模式下为 true：读继续走内存默认值，写会持续失败并回调 */
    @Volatile
    var isDegraded: Boolean = false
        private set

    /** 首批磁盘数据是否已加载完成（含降级场景）；UI 可据此做启动门闩 */
    @Volatile
    var firstLoadDone: Boolean = false
        private set

    /**
     * 初始化后端（App.onCreate 调用一次，重复调用抛错）。
     *
     * @param onCorruption 磁盘读取失败（如存储介质损坏导致 DataStore 反序列化失败）时的回调。
     *   传入即启用「降级重建」模式：失败不再抛出，库以默认值继续服务读（[isDegraded] 置真），
     *   由业务方决定是否删除损坏文件后重启进程重建。不传（默认）保持 fail-fast。
     */
    fun attach(
        dataStore: DataStore<Preferences>,
        coroutineScope: CoroutineScope,
        onCorruption: ((Throwable) -> Unit)? = null,
    ) {
        check(store == null) {
            "PersistedStore.attach 重复调用：后端只允许初始化一次"
        }
        this.onCorruption = onCorruption
        store = dataStore
        scope = coroutineScope
        coroutineScope.launch {
            try {
                dataStore.data.collect { prefs ->
                    lastPrefs = prefs
                    applyPrefs(prefs)
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                handleCorruption(t)
            }
        }
    }

    /** 等待首批磁盘数据加载完成（App.onCreate 调用一次）。主线程 runBlocking 仅适合小文件+快存储 */
    suspend fun awaitFirstLoad() {
        val s = store ?: failNotAttached()
        val prefs = try {
            s.data.first()
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            handleCorruption(t)
            return
        }
        lastPrefs = prefs
        applyPrefs(prefs)
        firstLoadDone = true
    }

    /**
     * 重置指定 key：内存立即回声明默认值（同步语义，无「磁盘已删、内存仍旧值」窗口），
     * 磁盘异步删除；Flow 回灌时跳过待删 key 直到磁盘确认 absent（issue #3）。
     */
    fun reset(vararg keys: String) {
        requireAttached()
        synchronized(states) {
            keys.forEach { k ->
                states[k]?.let { st -> defaults[k]?.let { d -> st.value = d } }
                pendingRemovals.add(k)
            }
        }
        launchDiskWrite { prefs ->
            keys.forEach { k ->
                // 按名字找到真实 Key 实例再删，避免对 Key 等值语义做假设
                prefs.asMap().keys.firstOrNull { it.name == k }?.let { prefs.remove(it) }
            }
        }
    }

    /** 按谓词重置，用于作用域级恢复：`reset { it.startsWith("demo-Audio-") }` */
    fun reset(predicate: (String) -> Boolean) {
        val keys = synchronized(states) { defaults.keys.filter(predicate) }
        if (keys.isNotEmpty()) reset(*keys.toTypedArray())
    }

    /** 全库恢复出厂：所有已登记属性回声明默认值，磁盘对应 key 全部移除 */
    fun resetAll() {
        val keys = synchronized(states) { defaults.keys.toList() }
        if (keys.isNotEmpty()) reset(*keys.toTypedArray())
    }

    /**
     * 类型安全重置（object 属性引用）：`reset(TimerState::switch)`。
     * 调用点由 persist-plugin 在编译期改写为顶层 [PersistedBackend.resetKey]（key 字面量内联，
     * R8 安全）；引用须内联写在调用处。未接插件时本方法抛错
     */
    fun reset(ref: KProperty0<*>): Unit = failRequirePlugin("reset(属性引用)")

    /**
     * 类型安全作用域重置：`resetScope(TimerState::class)`。
     * 调用点由 persist-plugin 改写为顶层 [PersistedBackend.resetScopeKey]；未接插件时抛错
     */
    fun resetScope(scope: KClass<*>): Unit = failRequirePlugin("resetScope(类引用)")

    private fun failRequirePlugin(api: String): Nothing = throw UnsupportedOperationException(
        "$api 需要编译器插件在调用点改写为存储 key，当前调用未被改写。" +
            "请检查 persist-plugin 的 -Xplugin 接线，或改用 reset(\"key\") / reset { } 字符串形式"
    )

    // ---- 内部机制：仅供同模块的后端函数（PersistedBackend）与委托使用，公开面只保留生命周期 API ----

    @Suppress("UNCHECKED_CAST")
    internal fun <T> stateFor(key: String, default: T): MutableState<T> {
        requireAttached()
        synchronized(states) {
            val existing = states[key]
            if (existing != null) {
                // getter 路径：default 是编译期内联的字面量；同 key 出现两个不同默认值即撞 key
                val registered = defaults[key]
                if (registered === unsetDefault) defaults[key] = default else checkDefault(key, default)
                return existing as MutableState<T>
            }
            return createState(key, default, comparable = true)
        }
    }

    internal fun <T> write(key: String, value: T) {
        requireSupportedType(value)
        // write 不参与默认值比对：seed 仅用于首次创建，避免把"新值"误当默认值引发误报
        val st = synchronized(states) {
            states[key] as? MutableState<T> ?: createState(key, value, comparable = false)
        }
        st.value = value
        launchDiskWrite { prefs ->
            when (value) {
                is Int -> prefs[intPreferencesKey(key)] = value
                is Long -> prefs[longPreferencesKey(key)] = value
                is Float -> prefs[floatPreferencesKey(key)] = value
                is Boolean -> prefs[booleanPreferencesKey(key)] = value
                is String -> prefs[stringPreferencesKey(key)] = value
            }
        }
    }

    // ---- 私有实现 ----

    @Suppress("UNCHECKED_CAST")
    private fun <T> createState(key: String, seed: T, comparable: Boolean): MutableState<T> {
        defaults.putIfAbsent(key, if (comparable) seed else unsetDefault)
        val initial = lastPrefs?.let { readFromPrefs(it, key, seed) } ?: seed
        val state = mutableStateOf(initial)
        states[key] = state as MutableState<Any?>
        return state
    }

    private fun checkDefault(key: String, default: Any?) {
        val registered = defaults[key]
        if (registered != null && registered != default) {
            throw PersistedKeyConflictException(key, registered, default)
        }
    }

    private fun launchDiskWrite(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        val s = store ?: failNotAttached()
        val sc = scope ?: failNotAttached()
        sc.launch {
            try {
                s.edit(block)
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                handleCorruption(t)
            }
        }
    }

    private fun requireAttached() {
        if (store == null) failNotAttached()
    }

    private fun failNotAttached(): Nothing = throw IllegalStateException(
        "PersistedStore 未初始化：请先在 Application.onCreate 调用 attach(dataStore, scope)，" +
            "再访问 @Persisted 属性"
    )

    private fun handleCorruption(t: Throwable) {
        isDegraded = true
        val handler = onCorruption
        if (handler == null) throw t
        handler(t)
    }

    private fun requireSupportedType(value: Any?) {
        require(
            value is Int || value is Long || value is Float || value is Boolean || value is String
        ) {
            "仅支持 Int/Long/Float/Boolean/String 的持久化，收到 ${value?.javaClass?.name ?: "null"}（key 未落盘）"
        }
    }

    private fun applyPrefs(prefs: Preferences) {
        synchronized(states) {
            for ((key, state) in states) {
                if (key in pendingRemovals) {
                    // 磁盘已确认 absent → 删除落地，解除跳过；仍存在则继续等待异步删除完成
                    if (prefs.asMap().none { it.key.name == key }) {
                        pendingRemovals.remove(key)
                    }
                    continue
                }
                val persisted = readFromPrefs(prefs, key, state.value) ?: continue
                state.value = persisted
            }
        }
    }

    private fun <T> readFromPrefs(prefs: Preferences, key: String, current: T): T? {
        @Suppress("UNCHECKED_CAST")
        return when (current) {
            is Int -> (prefs[intPreferencesKey(key)] ?: current) as T?
            is Long -> (prefs[longPreferencesKey(key)] ?: current) as T?
            is Float -> (prefs[floatPreferencesKey(key)] ?: current) as T?
            is Boolean -> (prefs[booleanPreferencesKey(key)] ?: current) as T?
            is String -> (prefs[stringPreferencesKey(key)] ?: current) as T?
            else -> null
        }
    }
}
