plugins {
    `kotlin-dsl`
}

dependencies {
    // convention plugin 引用的类型：KotlinCompile（接线约定）、MavenPublishBaseExtension（发布约定）
    implementation(libs.kotlin.gradle.plugin)
    implementation(libs.vanniktech.gradle.plugin)
}
