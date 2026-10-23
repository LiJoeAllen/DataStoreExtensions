plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    // matches the Kotlin compiler shipped with the Kotlin Gradle plugin
    compileOnly(libs.kotlin.compiler.embeddable)
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
