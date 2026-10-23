plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
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

dependencies {
    // 以 Maven 坐标消费本仓库发布的库（发布验证）：
    // 先 ./gradlew :datastore-extensions:publishToMavenLocal，再构建 app
    implementation(libs.datastore.extensions)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.datastore.preferences)
}

// ---- @K compiler plugin wiring (local module) ----
// 路径从 jar 任务的实际输出生成（版本号会影响 jar 文件名，勿硬编码 libs/<name>.jar）；
// 在 configureEach 内懒解析，此时 persist-plugin 的任务已注册
val pluginProject = project(":persist-plugin")
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    val jarTask = pluginProject.tasks.named<Jar>("jar")
    dependsOn(jarTask)
    compilerOptions.freeCompilerArgs.add(
        jarTask.map { "-Xplugin=${it.archiveFile.get().asFile.absolutePath}" }
    )
}
