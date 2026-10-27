import com.vanniktech.maven.publish.MavenPublishBaseExtension

/**
 * Maven 发布约定：组名/版本（可被 -Pversion= 或 ORG_GRADLE_PROJECT_version= 覆盖，
 * JitPack 用 VERSION 环境变量）、公共 POM 字段。
 * 发布渠道为 JitPack（构建 tag 时自动 publishToMavenLocal），各模块只声明
 * 自己的 coordinates 与 pom name/description。
 */
group = "io.github.lijoeallen"
version = providers.gradleProperty("version").getOrElse("0.2.1")

extensions.configure<MavenPublishBaseExtension> {
    pom {
        url.set("https://github.com/LiJoeAllen/DataStoreExtensions")
        inceptionYear.set("2026")
        licenses {
            license {
                name.set("Apache-2.0")
                url.set("https://www.apache.org/licenses/LICENSE-2.0.txt")
                distribution.set("repo")
            }
        }
        developers {
            developer {
                id.set("LiJoeAllen")
                name.set("LiJoeAllen")
                url.set("https://github.com/LiJoeAllen")
            }
        }
        scm {
            url.set("https://github.com/LiJoeAllen/DataStoreExtensions")
            connection.set("scm:git:https://github.com/LiJoeAllen/DataStoreExtensions.git")
            developerConnection.set("scm:git:git@github.com:LiJoeAllen/DataStoreExtensions.git")
        }
    }
}

