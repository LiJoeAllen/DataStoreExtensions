package io.github.lijoeallen.datastoreextensions.demo

import io.github.lijoeallen.datastoreextensions.K
import io.github.lijoeallen.datastoreextensions.persisted

/**
 * 演示：对象作用域 + 自定义前缀/后缀。
 *
 * key = [前缀-]后缀：
 * - 对象 `@K(k = "demo")` → 前缀 "demo"（缺省则用对象简单名 "DemoState"）
 * - 属性 `@K(k = "xyz1")` → 后缀 "xyz1"（缺省则用属性名）
 */
@K(k = "demo")
object DemoState {

    @K(k = "xyz1")
    var xyz by persisted(1)   // key = "demo-xyz1"

    var abc = 100             // key = "demo-abc"（未标注，自动纳入）
}
