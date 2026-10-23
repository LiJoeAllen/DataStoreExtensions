import org.gradle.api.publish.PublishingExtension

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.vanniktech.maven.publish)
}

group = "io.github.lijoeallen"
// 可被 -Pversion= 或 ORG_GRADLE_PROJECT_version= 覆盖（CI 用 release tag 作为版本号）
version = providers.gradleProperty("version").getOrElse("0.1.0")

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
        description = "@K annotated persisted properties with Jetpack DataStore (Preferences) backend: declare a property, get persistence + Compose recomposition for free."
        inceptionYear = "2026"
        url = "https://github.com/LiJoeAllen/DataStoreExtensions"
        licenses {
            license {
                name = "Apache-2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0.txt"
                distribution = "repo"
            }
        }
        developers {
            developer {
                id = "LiJoeallen"
                name = "LiJoeallen"
                url = "https://github.com/LiJoeAllen"
            }
        }
        scm {
            url = "https://github.com/LiJoeAllen/DataStoreExtensions"
            connection = "scm:git:https://github.com/LiJoeAllen/DataStoreExtensions.git"
            developerConnection = "scm:git:git@github.com:LiJoeAllen/DataStoreExtensions.git"
        }
    }
}

// GitHub Packages 发布仓库；凭证由 CI 的 GITHUB_ACTOR/GITHUB_TOKEN 环境变量提供
// https://docs.github.com/zh/actions/tutorials/publish-packages/publish-java-packages-with-gradle
configure<PublishingExtension> {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/LiJoeAllen/DataStoreExtensions")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}

// ---- @K compiler plugin wiring (local module) ----
val pluginProject = project(":persist-plugin")
val pluginJarPath = pluginProject.layout.buildDirectory.file("libs/${pluginProject.name}.jar")
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    dependsOn(pluginProject.tasks.named("jar"))
    compilerOptions.freeCompilerArgs.add("-Xplugin=${pluginJarPath.get().asFile.absolutePath}")
}
