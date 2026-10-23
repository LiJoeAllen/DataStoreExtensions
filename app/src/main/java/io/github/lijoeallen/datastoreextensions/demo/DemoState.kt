package io.github.lijoeallen.datastoreextensions.demo

import io.github.lijoeallen.datastoreextensions.K
import io.github.lijoeallen.datastoreextensions.persisted

/**
 * 演示：对象作用域 + 自定义前缀/后缀。
 *
 * key = [前缀-]后缀；k 缺省/为空时回退到名字：
 * - 对象 `@K(k = "demo")` → 前缀 "demo"；`@K`（k 为空）→ 前缀 = 对象简单名
 * - 属性 `@K(k = "xyz1")` → 后缀 "xyz1"；k 为空/未标注 → 后缀 = 属性名
 */
@K(k = "demo")
object DemoState {

    @K(k = "xyz1")
    var xyz by persisted(1)   // key = "demo-xyz1"

    var abc = 100             // key = "demo-abc"（未标注，属性名作后缀）
}

/** k 为空的对象：前缀回退对象名 "Counter"，属性名作后缀 */
@K
object Counter {

    @K
    var count by persisted(0) // key = "Counter-count"
}
