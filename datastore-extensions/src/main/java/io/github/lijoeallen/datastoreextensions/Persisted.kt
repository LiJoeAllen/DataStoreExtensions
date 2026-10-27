package io.github.lijoeallen.datastoreextensions

import kotlin.reflect.KProperty

/**
 * 持久化属性标记：声明即获得「读写持久化 + Compose 重组」。
 *
 * `key` 是统一的 key 段，含义随声明层级自然组合，无同名异义：
 * - 属性：key 的最后一段，缺省为属性名
 * - 对象：作用域段，嵌套时逐层追加，缺省为对象简单名
 *
 * 两种声明语法：
 * 1. 委托：`@Persisted var xyz by persisted(1)`（默认值取 persisted 的参数）
 * 2. 纯注解：`@Persisted var abc = 100`（默认值取属性初值）
 *
 * 存储 key = 各层 key 段以 "-" 连接：
 * ```
 * @Persisted("app") object Settings {
 *     var volume = 50                        // app-volume
 *     object Audio {                         // 未标注，对象名作段
 *         var bitrate = 128                  // app-Audio-bitrate
 *     }
 * }
 * ```
 * 作用域内所有 var 与嵌套对象自动纳入；作用域外仅带 @Persisted 的成员持久化（无前缀）。
 *
 * get/set 由编译器插件在 IR 阶段重写为统一后端调用（见 persist-plugin 模块）；
 * 属性类型仅支持 Int/Long/Float/Boolean/String，其他类型编译期报错
 * （纯委托运行时写法则在构造时报错）。
 *
 * @param key 本层 key 段：属性为末段（缺省属性名），对象为作用域段（缺省对象简单名）
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class Persisted(val key: String = "")

/**
 * `persisted()` 委托的运行时实现。接入编译器插件后属性访问器会被整体改写，
 * 本类仅在「无插件」场景兜底：顶层委托、key = 属性名。
 */
class PersistedProperty<T>(private val default: T) {

    init {
        require(default is Int || default is Long || default is Float || default is Boolean || default is String) {
            "persisted() 仅支持 Int/Long/Float/Boolean/String，收到 ${default?.javaClass?.name ?: "null"}"
        }
    }

    operator fun getValue(thisRef: Any?, property: KProperty<*>): T =
        PersistedStore.stateFor(property.name, default).value

    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
        PersistedStore.write(property.name, value)
    }
}

fun <T> persisted(default: T): PersistedProperty<T> = PersistedProperty(default)
