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
implementation("com.github.LiJoeAllen:DataStoreExtensions:0.3.0")
```

### 2. 接入编译器插件

`@Persisted` 的改写由编译器插件完成（纯注解/对象作用域没有插件**不会持久化**，会报编译错提示）。
编译器插件与编译器 ABI 强绑定，**需选与你的 Kotlin 版本匹配的插件 tag**（兼容表见「发布」一节；
本项目默认构建基于 Kotlin 2.4）：

```kotlin
// build.gradle.kts
val persistPlugin = configurations.create("persistPlugin") { isTransitive = false }
dependencies {
    persistPlugin("com.github.LiJoeAllen.DataStoreExtensions:persist-plugin:0.3.0")
    // Kotlin 2.3.x 项目请用变体 tag：
    // persistPlugin("com.github.LiJoeAllen.DataStoreExtensions:persist-plugin:0.3.0-k2.3.21")
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

未调用 `attach()` 就访问属性会抛 `IllegalStateException`（快速失败，不静默丢数据）。
嵌入式/慢速存储场景建议传入 `onCorruption` 回调启用**降级模式**：磁盘损坏时不再抛出，
库以默认值继续服务读（`isDegraded = true`），由业务方决定重建文件：

```kotlin
// 顶层声明 DataStore 单例
val Context.appDataStore: DataStore<Preferences> by preferencesDataStore(name = "your_prefs")

class App : Application() {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        PersistedStore.attach(appDataStore, appScope)          // 默认 fail-fast
        // PersistedStore.attach(appDataStore, appScope) { t ->   // 降级模式（可选）
        //     Log.e("App", "存储损坏，已降级为默认值", t)
        // }
        // 等首批磁盘数据落表，之后属性读写是同步语义。
        // 主线程 runBlocking 仅适合小文件+快存储；嵌入式冷启动建议放后台协程，
        // 配合 PersistedStore.firstLoadDone 做启动门闩：
        appScope.launch { PersistedStore.awaitFirstLoad() }
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
- **绝对 key**：属性 key 以 `/` 开头时忽略全部作用域段（如 `@Persisted("/show_images")`），
  用于存量 flat key 收拢进 object 时数据零迁移

**重置（恢复出厂）**：

```kotlin
PersistedStore.reset(TimerState::switch)          // 类型安全：属性引用（推荐）
PersistedStore.resetScope(TimerState::class)      // 类型安全：整个作用域
PersistedStore.reset("demo-xyz1")                 // 字符串 key（动态场景）
PersistedStore.reset { it.startsWith("demo-") }   // 按谓词
PersistedStore.resetAll()                         // 全库恢复出厂
```

引用形式的调用点由编译器插件改写为 key 字面量（R8 安全、零反射、typo 直接编译错）；
限制：引用须内联写在调用处（先赋给变量再传不会被改写），属性引用覆盖 object 成员。
未接插件时引用形式抛 `UnsupportedOperationException`（fail-fast）。

重置是同步语义：内存立即回声明默认值，磁盘异步删除，Flow 回灌会跳过待删 key
直到磁盘确认——不存在「磁盘已删、内存仍旧值」的窗口。

**key 唯一性保障（双层）**：编译期插件对同模块内撞 key 的两个声明直接报编译错；
跨模块/增量编译场景由运行期兜底——同 key 被不同默认值的声明访问时抛
`PersistedKeyConflictException`。

**类型与失败行为（全部 fail-fast，无静默丢弃）**：

- 仅支持 Int/Long/Float/Boolean/String；其他类型编译期报错（插件），
  纯委托运行时构造报错（`persisted(1.0)`）
- 存储文件名是存储契约，改名即丢数据

## 从手写 DataStore / SharedPreferences 迁移

- **同名文件复用**：`preferencesDataStore(name = ...)` 同名委托是进程内单例，迁移期新旧入口可共存读同一文件
- **存量 flat key 收拢进 object**：属性用绝对 key（`/` 开头）忽略作用域段，key 与存量完全一致，零迁移：

  ```kotlin
  @Persisted object Settings {
      @Persisted("/show_images") var showImages = true    // key = "show_images"
      @Persisted("/timer_switch") var timerSwitch = true  // key = "timer_switch"
  }
  ```

- 新增字段不必带 `/`，走正常作用域段即可，存量数据原地不动
- 注意存储 key 的可用字符受 DataStore 约束（字母/数字/`_`/`-`），段内避免其他符号

## 模块

| 模块 | JitPack 坐标 | 职责 |
|---|---|---|
| [datastore-extensions](./datastore-extensions) | `com.github.LiJoeAllen:DataStoreExtensions` | 可复用持久化框架：`@Persisted` 注解、`PersistedProperty` 委托、插件后端函数、`PersistedStore`（DataStore 后端） |
| [persist-plugin](./persist-plugin) | `com.github.LiJoeAllen.DataStoreExtensions:persist-plugin` | Kotlin 编译器插件：IR 阶段重写 `@Persisted` 属性的 get/set，计算作用域段与自定义 key |
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

发布渠道只有 **JitPack**：**在 GitHub 创建 Release**（tag 名即版本号，如 `0.3.0`）即可，
无需任何 CI 配置——JitPack 在首次请求该版本时自动构建（见 `jitpack.yml`：
双模块 publishToMavenLocal，版本取 tag）。版本不可变，重新发布需换版本号。

消费坐标（匿名）：

```kotlin
implementation("com.github.LiJoeAllen:DataStoreExtensions:0.3.0")                      // AAR
classpath / 配置依赖("com.github.LiJoeAllen.DataStoreExtensions:persist-plugin:0.3.0") // 插件（多模块坐标）
```

### 插件与 Kotlin 版本兼容表

编译器插件与编译器 ABI 强绑定，请按项目 Kotlin 版本选 tag（运行时 AAR 无此约束）：

| tag | 构建用 Kotlin | 适用项目 Kotlin |
|---|---|---|
| `0.3.0` | 2.4.21 | 2.4.x |
| `0.3.0-k2.3.21` | 2.3.21 | 2.3.x |

## 修改插件后

插件源码改动需重新出 jar（Gradle 的 `:persist-plugin:jar` 会自动被
app/datastore-extensions 的编译任务依赖，正常构建即可）：

```powershell
.\gradlew :persist-plugin:jar
```
