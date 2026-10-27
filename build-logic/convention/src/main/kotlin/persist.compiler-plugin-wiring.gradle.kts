import org.gradle.api.tasks.bundling.Jar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/**
 * -Xplugin 接线约定：把 :persist-plugin 的 jar 传给本项目所有 Kotlin 编译任务。
 *
 * 路径从 jar 任务的实际输出生成（版本号会影响 jar 文件名，勿硬编码 libs/<name>.jar）；
 * 在 configureEach 内懒解析，此时 persist-plugin 的任务已注册。
 */
val persistPluginProject = project(":persist-plugin")

tasks.withType<KotlinCompile>().configureEach {
    val jarTask = persistPluginProject.tasks.named<Jar>("jar")
    dependsOn(jarTask)
    compilerOptions.freeCompilerArgs.add(
        jarTask.map { "-Xplugin=${it.archiveFile.get().asFile.absolutePath}" }
    )
}
