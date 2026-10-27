plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("persist.compiler-plugin-wiring")
}

android {
    namespace = "io.github.lijoeallen.datastoreextensions.demo"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "io.github.lijoeallen.datastoreextensions.demo"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// 默认消费仓库内模块（日常开发 / AS 同步开箱即用）；
// -PconsumeRemote 时消费 JitPack 发布产物，作为发布前的真实消费验证（须版本已发布）
val consumeRemote = providers.gradleProperty("consumeRemote").isPresent

dependencies {
    if (consumeRemote) {
        implementation(libs.datastore.extensions)
    } else {
        implementation(project(":datastore-extensions"))
    }

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.datastore.preferences)
}
