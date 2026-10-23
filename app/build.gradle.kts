plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "io.github.lijoeallen.datastoreextensions.demo"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.lijoeallen.datastoreextensions.demo"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(project(":datastore-extensions"))

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.datastore.preferences)
}

// ---- @K compiler plugin wiring (local module) ----
val pluginProject = project(":persist-plugin")
val pluginJarPath = pluginProject.layout.buildDirectory.file("libs/${pluginProject.name}.jar")
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    dependsOn(pluginProject.tasks.named("jar"))
    compilerOptions.freeCompilerArgs.add("-Xplugin=${pluginJarPath.get().asFile.absolutePath}")
}
