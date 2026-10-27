plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.vanniktech.maven.publish)
    id("persist.publishing-conventions")
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

mavenPublishing {
    coordinates(group.toString(), "persist-plugin", version.toString())

    pom {
        name = "PersistPlugin"
        description = "Kotlin compiler plugin for DataStoreExtensions: rewrites @Persisted property accessors to persistedGet*/persistedSet* backend calls at IR stage."
    }
}
