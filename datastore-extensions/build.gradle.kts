plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.vanniktech.maven.publish)
    id("persist.publishing-conventions")
    id("persist.compiler-plugin-wiring")
}

android {
    namespace = "io.github.lijoeallen.datastoreextensions"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    // MutableState/mutableStateOf for Compose recomposition
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.runtime)
    // DataStore backend
    implementation(libs.datastore.preferences)
}

mavenPublishing {
    coordinates(group.toString(), "datastore-extensions", version.toString())

    pom {
        name = "DataStoreExtensions"
        description = "@Persisted annotated persisted properties with Jetpack DataStore (Preferences) backend: declare a property, get persistence + Compose recomposition for free."
    }
}
