package io.github.lijoeallen.kk

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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * @K 持久化属性的 DataStore 后端。
 *
 * 设计：内存 MutableState 表（保证 Compose 同步读与重组）+ DataStore 异步落盘 +
 * Flow 收集器把磁盘数据同步回内存表。首次加载由 App 启动时 awaitFirstLoad() 完成，
 * 之后属性读写与 SharedPreferences 版一样是同步语义。
 */
object PersistedStore {

    // key = 属性名。保证委托/插件生成的访问器在每次重组后仍持有同一 MutableState 实例。
    private val states = mutableMapOf<String, MutableState<Any?>>()

    @Volatile
    private var store: DataStore<Preferences>? = null

    @Volatile
    private var scope: CoroutineScope? = null

    @Volatile
    private var lastPrefs: Preferences? = null

    fun attach(dataStore: DataStore<Preferences>, coroutineScope: CoroutineScope) {
        store = dataStore
        scope = coroutineScope
        coroutineScope.launch {
            dataStore.data.collect { prefs ->
                lastPrefs = prefs
                applyPrefs(prefs)
            }
        }
    }

    /** 等待首批磁盘数据加载完成（App.onCreate 调用一次） */
    suspend fun awaitFirstLoad() {
        val s = store ?: return
        val prefs = s.data.first()
        lastPrefs = prefs
        applyPrefs(prefs)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> stateFor(key: String, default: T): MutableState<T> {
        synchronized(states) {
            val existing = states[key]
            if (existing != null) return existing as MutableState<T>
            val initial = lastPrefs?.let { readFromPrefs(it, key, default) } ?: default
            val state = mutableStateOf(initial)
            states[key] = state as MutableState<Any?>
            return state
        }
    }

    fun <T> write(key: String, value: T) {
        // 先更新内存（即时重组），再异步落盘
        stateFor(key, value).value = value
        val s = store ?: return
        val sc = scope ?: return
        sc.launch {
            s.edit { prefs ->
                when (value) {
                    is Int -> prefs[intPreferencesKey(key)] = value
                    is Long -> prefs[longPreferencesKey(key)] = value
                    is Float -> prefs[floatPreferencesKey(key)] = value
                    is Boolean -> prefs[booleanPreferencesKey(key)] = value
                    is String -> prefs[stringPreferencesKey(key)] = value
                    else -> Unit
                }
            }
        }
    }

    private fun applyPrefs(prefs: Preferences) {
        synchronized(states) {
            for ((key, state) in states) {
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
