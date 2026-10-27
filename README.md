# DataStoreExtensions

[![](https://jitpack.io/v/LiJoeAllen/DataStoreExtensions.svg)](https://jitpack.io/#LiJoeAllen/DataStoreExtensions)

Android Compose 项目，用 **Jetpack DataStore（Preferences）** 实现 `@Persisted` 注解持久化属性：
声明一个属性即自动获得「读写持久化 + Compose 重组」能力。

```kotlin
@Persisted var xyz by persisted(1)   // 委托语法：默认值取 persisted 参数
@Persisted var abc = 100             // 纯注解语法：默认值取属性初值

@Persisted("demo")                   // 对象作用域：成员自动持久化
object DemoState {
    @Persisted("xyz1") var xyz by persisted(1)   // key = "demo-xyz1"
    var abc = 100                                // key = "demo-abc"

    object Audio {                               // 嵌套作用域，段逐层追加
        var volume = 50                          // key = "demo-Audio-volume"
    }
}
```

编译器插件在 IR 阶段把访问器统一改写为 `persistedGet*/persistedSet*` 后端调用，
共享同一套内存状态表 + 存储后端。支持 Int/Long/Float/Boolean/String 五种类型。
代码使用 `io.github.lijoeallen` 命名空间。

## 快速开始（三步）

### 1. 加依赖

**JitPack（匿名，无需任何凭据）**：

```kotlin
repositories { maven("https://jitpack.io") }
implementation("com.github.LiJoeAllen:DataStoreExtensions:0.2.0")
```

> GitHub Packages 的 Maven 读取强制鉴权（公开仓库也需 PAT），不适合作为消费渠道，
> 仅作发布备份（见「发布」）。编译器插件坐标见「发布」一节的多模块写法。

### 2. 接入编译器插件

`@Persisted` 的改写由编译器插件完成（纯注解/对象作用域没有插件**不会持久化**，会报编译错提示）。
插件的 Kotlin 版本需与你的 Kotlin Gradle 插件版本一致（当前基于 Kotlin 2.4）：

```kotlin
// build.gradle.kts
val persistPlugin = configurations.create("persistPlugin") { isTransitive = false }
dependencies {
    persistPlugin("io.github.lijoeallen:persist-plugin:0.2.0")
    // JitPack 渠道则为 "com.github.LiJoeAllen.DataStoreExtensions:persist-plugin:0.2.0"
}
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    compilerOptions.freeCompilerArgs.add(
        persistPlugin.incoming.files.elements.map { files ->
            "-Xplugin=" + files.single().asFile.absolutePath
        }
    )
}
```

### 3. 初始化（Application）

未调用 `attach()` 就访问属性会抛 `IllegalStateException`（快速失败，不静默丢数据）：

```kotlin
// 顶层声明 DataStore 单例
val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "your_prefs")

class App : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        PersistedStore.attach(appDataStore, appScope)
        // 等首批磁盘数据落表，之后属性读写是同步语义
        runBlocking { PersistedStore.awaitFirstLoad() }
    }
}
```

## 用法

**两种语法 × 两种作用域**：

| | 顶层属性 | `@Persisted object` 成员 |
|---|---|---|
| 委托 `by persisted(v)` | 默认值取 persisted 参数 | 成员自动纳入对象作用域 |
| 纯注解 `var x = v` | 默认值取属性初值 | 同左 |

**存储 key = 各层 key 段以 `-` 连接**（`key` 是统一参数，含义随层级自然组合）：

- 属性段 = 属性的 `key` 参数（缺省为属性名）
- 对象段 = 对象的 `key` 参数（缺省为对象简单名），多级嵌套时逐层追加
- `@Persisted object` 是持久化作用域：作用域内所有 var 与嵌套对象自动纳入；
  作用域外（顶层/普通类）仅带 @Persisted 的成员持久化，无作用域段

**类型与失败行为（全部 fail-fast，无静默丢弃）**：

- 仅支持 Int/Long/Float/Boolean/String；其他类型编译期报错（插件），
  纯委托运行时构造报错（`persisted(1.0)`）
- 存储文件名是存储契约，改名即丢数据

## 模块

| 模块 | 发布坐标 | 职责 |
|---|---|---|
| [datastore-extensions](./datastore-extensions) | `io.github.lijoeallen:datastore-extensions` | 可复用持久化框架：`@Persisted` 注解、`PersistedProperty` 委托、插件后端函数、`PersistedStore`（DataStore 后端） |
| [persist-plugin](./persist-plugin) | `io.github.lijoeallen:persist-plugin` | Kotlin 编译器插件：IR 阶段重写 `@Persisted` 属性的 get/set，计算对象前缀与自定义 key |
| [app](./app) | 不发布 | 演示：`App`（初始化）+ Compose UI |
| [build-logic](./build-logic) | 不发布 | 构建约定（convention plugin）：编译器插件接线、Maven 发布公共配置 |

## 核心设计

- 内存 `MutableState` 表保证同步读与 Compose 重组；DataStore 异步落盘；
  Flow 收集器把磁盘数据同步回内存表
- `attach()` + `awaitFirstLoad()` 等首批磁盘数据落表后，
  属性读写与 SharedPreferences 一样是同步语义
- key 规则见上文（`[前缀-]后缀`）

## 构建

AGP 9 / Gradle 9.8，构建用 JDK 17（daemon 工具链由 `gradle-daemon-jvm.properties` 钉定）：

```powershell
# 指向本机 JDK 17 安装目录（Android Studio 自带的 jbr 也可以），勿写死他人机器路径
$env:JAVA_HOME = "<本机 JDK 17 目录>"

# 日常开发 / AS 同步：默认消费仓库内模块
.\gradlew :app:assembleDebug
# 发布验证：消费 JitPack 正式产物（需对应版本已发布）
.\gradlew -PconsumeRemote :app:assembleDebug

adb install -r app\build\outputs\apk\debug\app-debug.apk
```

> app 平时以 `project(...)` 消费仓库内模块，日常构建不依赖远端；
> 发布前用 `-PconsumeRemote` 切到 JitPack 坐标做一次真实消费验证：能编译即证明发布产物可用。
> 依赖下载优先走阿里云国内镜像（`settings.gradle.kts`），官方源作兜底；
> Gradle 发行版走腾讯云镜像（`gradle-wrapper.properties`）；SDK 路径在 `local.properties`。

## 发布

[.github/workflows/publish.yml](.github/workflows/publish.yml) 一次发布两个库模块到 GitHub Packages：

- **在 GitHub 创建 Release**（tag 名即版本号，如 `0.2.0`，`v` 前缀自动去掉）即可触发；
  也可在 Actions 页面手动触发（workflow_dispatch）。
- 注意：GitHub Packages 不允许覆盖同名版本，重复发布需先删包或换版本号。

JitPack 在 tag 构建时同时发布两个模块（见 `jitpack.yml`，版本取 tag），消费坐标：

```kotlin
implementation("com.github.LiJoeAllen:DataStoreExtensions:0.2.0")                      // AAR
classpath / 配置依赖("com.github.LiJoeAllen.DataStoreExtensions:persist-plugin:0.2.0") // 插件（多模块坐标）
```

## 修改插件后

插件源码改动需重新出 jar（Gradle 的 `:persist-plugin:jar` 会自动被
app/datastore-extensions 的编译任务依赖，正常构建即可）：

```powershell
.\gradlew :persist-plugin:jar
```
