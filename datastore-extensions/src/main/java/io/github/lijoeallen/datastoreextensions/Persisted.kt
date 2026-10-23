package io.github.lijoeallen.datastoreextensions

import kotlin.reflect.KProperty

/**
 * 持久化属性标记，用法：
 * 1. 委托：`@K var xyz by persisted(1)`（默认值取 persisted 的参数）
 * 2. 纯注解：`@K var abc = 100`（默认值取属性初值）
 * 3. 对象作用域：`@K object State { var x = 1 }`，其内所有 var 自动持久化
 *
 * 存储 key = [前缀-]后缀：
 * - 后缀 = 属性的 `k`（缺省为属性名）：`@K(k = "x1") var xyz ...` → "...-x1"
 * - 前缀 = 所在 `@K object` 的 `k`（缺省为对象简单名）：`@K(k = "demo") object State` → "demo-..."
 * - 顶层属性无前缀，key 即后缀（向后兼容）
 * 两种写法的 get/set 均由编译器插件在 IR 阶段重写为统一后端调用（见 persist-plugin 模块）。
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.LOCAL_VARIABLE, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class K(val k: String = "")

class PersistedProperty<T>(private val default: T) {

    operator fun getValue(thisRef: Any?, property: KProperty<*>): T =
        PersistedStore.stateFor(property.name, default).value

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        PersistedStore.write(property.name, value)
        PersistedStore.stateFor(property.name, default).value = value
    }
}

fun <T> persisted(default: T): PersistedProperty<T> = PersistedProperty(default)

// ---- 编译器插件生成的 getter/setter 调用的后端函数（勿改名，插件按名引用） ----
fun persistedGetInt(key: String, default: Int): Int = PersistedStore.stateFor(key, default).value
fun persistedSetInt(key: String, value: Int) {
    PersistedStore.write(key, value)
    PersistedStore.stateFor(key, value).value = value
}

fun persistedGetLong(key: String, default: Long): Long = PersistedStore.stateFor(key, default).value
fun persistedSetLong(key: String, value: Long) {
    PersistedStore.write(key, value)
    PersistedStore.stateFor(key, value).value = value
}

fun persistedGetFloat(key: String, default: Float): Float = PersistedStore.stateFor(key, default).value
fun persistedSetFloat(key: String, value: Float) {
    PersistedStore.write(key, value)
    PersistedStore.stateFor(key, value).value = value
}

fun persistedGetBoolean(key: String, default: Boolean): Boolean = PersistedStore.stateFor(key, default).value
fun persistedSetBoolean(key: String, value: Boolean) {
    PersistedStore.write(key, value)
    PersistedStore.stateFor(key, value).value = value
}

fun persistedGetString(key: String, default: String): String = PersistedStore.stateFor(key, default).value
fun persistedSetString(key: String, value: String) {
    PersistedStore.write(key, value)
    PersistedStore.stateFor(key, value).value = value
}
