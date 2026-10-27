/**
 * 编译器插件 ABI 契约面（persist-plugin 按名引用，勿改名/勿挪包）。
 *
 * 这些函数是插件在 IR 阶段生成的 getter/setter 调用目标：
 *   persistedGet<Type>(key, default) / persistedSet<Type>(key, value)
 * 与用户 API（Persisted.kt）变更原因不同：只随插件 ABI 演进，
 * 改动任何一个名字都必须与 persist-plugin 的 PersistPlugin.kt 同步。
 */
package io.github.lijoeallen.datastoreextensions

// ---- 编译器插件生成的 getter/setter 调用的后端函数 ----
fun persistedGetInt(key: String, default: Int): Int = PersistedStore.stateFor(key, default).value
fun persistedSetInt(key: String, value: Int) {
    PersistedStore.write(key, value)
}

fun persistedGetLong(key: String, default: Long): Long = PersistedStore.stateFor(key, default).value
fun persistedSetLong(key: String, value: Long) {
    PersistedStore.write(key, value)
}

fun persistedGetFloat(key: String, default: Float): Float = PersistedStore.stateFor(key, default).value
fun persistedSetFloat(key: String, value: Float) {
    PersistedStore.write(key, value)
}

fun persistedGetBoolean(key: String, default: Boolean): Boolean = PersistedStore.stateFor(key, default).value
fun persistedSetBoolean(key: String, value: Boolean) {
    PersistedStore.write(key, value)
}

fun persistedGetString(key: String, default: String): String = PersistedStore.stateFor(key, default).value
fun persistedSetString(key: String, value: String) {
    PersistedStore.write(key, value)
}
