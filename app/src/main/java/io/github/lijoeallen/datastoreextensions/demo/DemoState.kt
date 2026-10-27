package io.github.lijoeallen.datastoreextensions.demo

import io.github.lijoeallen.datastoreextensions.Persisted
import io.github.lijoeallen.datastoreextensions.persisted

/**
 * 演示：对象作用域 + 多层嵌套。key = 各层 key 段以 "-" 连接，段缺省回退到名字：
 * - 对象 `@Persisted("demo")` → 段 "demo"；未标注 → 段 = 对象简单名
 * - 属性 `@Persisted("xyz1")` → 段 "xyz1"；未标注 → 段 = 属性名
 */
@Persisted("demo")
object DemoState {

    @Persisted("xyz1")
    var xyz by persisted(1)   // key = "demo-xyz1"

    var abc = 100             // key = "demo-abc"（未标注，属性名作末段）

    /** 嵌套作用域：段逐层追加，成员自动纳入 */
    object Audio {
        var volume = 50       // key = "demo-Audio-volume"（对象名作段）

        @Persisted("rate")
        var rate = 128        // key = "demo-Audio-rate"（属性段自定义）
    }
}

/** 顶层无作用域：key 即属性段（对象名作段） */
@Persisted
object Counter {

    var count by persisted(0) // key = "Counter-count"
}
