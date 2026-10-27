rootProject.name = "build-logic"

include(":convention")

// convention 复用根项目的版本目录，KGP / vanniktech 版本与主构建保持同一单一来源
dependencyResolutionManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/public")
        gradlePluginPortal()
        mavenCentral()
    }
    versionCatalogs {
        create("libs") { from(files("../gradle/libs.versions.toml")) }
    }
}
