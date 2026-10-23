# DataStoreExtensions

Android Compose 项目，用 **Jetpack DataStore（Preferences）** 实现 `@K` 注解持久化属性：
声明一个属性即自动获得「读写持久化 + Compose 重组」能力。**三种用法**：

```kotlin
@K var xyz by persisted(1)   // 写法一：属性委托（默认值取 persisted 参数）
@K var abc = 100             // 写法二：纯注解（默认值取属性初值）

@K(k = "demo")               // 写法三：对象作用域，自定义前缀
object DemoState {
    @K(k = "xyz1") var xyz by persisted(1)   // key = "demo-xyz1"（k 覆盖后缀）
    var abc = 100                            // key = "demo-abc"（未标注自动纳入）
}
```

编译器插件在 IR 阶段把访问器统一改写为 `persistedGet*/persistedSet*` 后端调用，
共享同一套内存状态表 + 存储后端。支持 Int/Long/Float/Boolean/String 五种类型。

**存储 key = `[前缀-]后缀`**：后缀 = 属性的 `k`（缺省为属性名）；
前缀 = 所在 `@K object` 的 `k`（缺省为对象简单名）；顶层属性无前缀（向后兼容）。
代码使用 `io.github.lijoeallen` 命名空间。

## 模块

| 模块 | 职责 |
|---|---|
| [datastore-extensions](./datastore-extensions) | 可复用持久化框架（发布为 `io.github.lijoeallen:datastore-extensions`）：`@K` 注解、`PersistedProperty` 委托、插件后端函数 `persistedGet*/persistedSet*`、`PersistedStore`（DataStore 后端） |
| [persist-plugin](./persist-plugin) | Kotlin 编译器插件：IR 阶段重写 `@K` 属性的 get/set，计算对象前缀与自定义 key |
| [app](./app) | 演示：`App`（Application 初始化 DataStore）+ Compose UI |

## 核心设计

- 内存 `MutableState` 表保证同步读与 Compose 重组；DataStore 异步落盘；
  Flow 收集器把磁盘数据同步回内存表
- `App.onCreate` 中 `attach()` + `awaitFirstLoad()`：等首批磁盘数据落表，
  之后属性读写与 SharedPreferences 一样是同步语义
- 支持 Int/Long/Float/Boolean/String，key 规则见上文（`[前缀-]后缀`）

## 构建

AGP 9 / Gradle 9.8，构建用 JDK 17（daemon 工具链由 `gradle-daemon-jvm.properties` 钉定）：

```powershell
# 指向本机 JDK 17 安装目录（Android Studio 自带的 jbr 也可以），勿写死他人机器路径
$env:JAVA_HOME = "<本机 JDK 17 目录>"
.\gradlew :datastore-extensions:publishToMavenLocal  # app 通过 Maven 坐标消费库，需先发布到本地
.\gradlew :app:assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

> app 模块以 Maven 坐标 `io.github.lijoeallen:datastore-extensions` 引用本仓库发布的库（优先 mavenLocal，
> 其次 GitHub Packages），而非 `project(...)`，本身就是一次发布验证：能编译即证明产物可用。
> 远程消费需设置环境变量 `GITHUB_ACTOR=LiJoeAllen` 与 `GITHUB_TOKEN=<PAT(read:packages)>`。

依赖下载优先走阿里云国内镜像（`settings.gradle.kts`），官方源作兜底；
Gradle 发行版走腾讯云镜像（`gradle-wrapper.properties`）。
Android SDK 路径在 `local.properties`。

## 发布 datastore-extensions

`datastore-extensions` 模块经 [vanniktech](https://github.com/vanniktech/gradle-maven-publish-plugin) 插件
发布到 GitHub Packages，工作流见 [.github/workflows/publish.yml](.github/workflows/publish.yml)：

- **在 GitHub 创建 Release**（tag 名即版本号，如 `0.1.0`，`v` 前缀自动去掉）即可触发；
  也可在 Actions 页面手动触发（workflow_dispatch）。
- 注意：GitHub Packages 不允许覆盖同名版本，重复发布需先删包或换版本号。

### 方式一：JitPack（匿名，无需任何凭据）

```kotlin
repositories { maven("https://jitpack.io") }
implementation("com.github.LiJoeAllen.DataStoreExtensions:datastore-extensions:0.1.1")
```

### 方式二：GitHub Packages（需 PAT(read:packages)）

```kotlin
repositories {
    maven("https://maven.pkg.github.com/LiJoeAllen/DataStoreExtensions") {
        credentials { /* GitHub 用户名 + PAT(read:packages) */ }
    }
}
implementation("io.github.lijoeallen:datastore-extensions:0.1.1")
```

纯注解写法（`@K var abc = 100`）需要编译器插件配合，源码在 `persist-plugin` 模块，
可作为本地模块接入（`-Xplugin=`），参见 `datastore-extensions/build.gradle.kts` 的接线方式。

## 修改插件后

插件源码改动需重新出 jar（Gradle 的 `:persist-plugin:jar` 会自动被
app/datastore-extensions 的编译任务依赖，正常构建即可）：

```powershell
.\gradlew :persist-plugin:jar
```
