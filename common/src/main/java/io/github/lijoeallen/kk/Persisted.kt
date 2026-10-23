package io.github.lijoeallen.kk

import kotlin.reflect.KProperty

/**
 * 持久化属性标记，两种用法：
 * 1. 委托：`@K var xyz by persisted(1)`
 * 2. 纯注解：`@K var abc = 100`（get/set 由编译器插件重写，见 kk-persist-plugin 模块）
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.LOCAL_VARIABLE)
@Retention(AnnotationRetention.BINARY)
annotation class K

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
