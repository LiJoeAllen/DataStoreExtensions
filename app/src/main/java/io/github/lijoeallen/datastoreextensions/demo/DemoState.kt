package io.github.lijoeallen.datastoreextensions.demo

import io.github.lijoeallen.datastoreextensions.K
import io.github.lijoeallen.datastoreextensions.persisted

/**
 * 演示：同一文件内两种 @K 写法并存。
 * - xyz：委托写法（by persisted）
 * - abc：纯注解写法（get/set 由编译器插件重写为 persistedGetXxx/persistedSetXxx 后端调用）
 */
@K
var xyz by persisted(1)

@K
var abc = 100
