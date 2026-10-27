import com.vanniktech.maven.publish.MavenPublishBaseExtension
import org.gradle.api.publish.PublishingExtension

/**
 * Maven 发布约定：组名/版本（可被 -Pversion= 或 ORG_GRADLE_PROJECT_version= 覆盖，
 * CI 用 release tag、JitPack 用 VERSION 环境变量）、公共 POM 字段、GitHub Packages 发布仓库。
 * 各模块只声明自己的 coordinates 与 pom name/description。
 */
group = "io.github.lijoeallen"
version = providers.gradleProperty("version").getOrElse("0.2.0")

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

// GitHub Packages 发布仓库；凭证由 CI 的 GITHUB_ACTOR/GITHUB_TOKEN 环境变量提供
// https://docs.github.com/zh/actions/tutorials/publish-packages/publish-java-packages-with-gradle
extensions.configure<PublishingExtension> {
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
