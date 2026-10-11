# JDK 8 → 17 → 21 对照实验

给"只用过 JDK 8"的人看的东西。不背特性列表，而是把同一件事在三个版本里各写一遍，跑起来对着看。

## 怎么跑

```bash
cd "/Users/wufeng/Documents/ChatGPT/面试/coding-lab/jdk8-vs-21"
./run.sh
```

需要 JDK 21+。你装了 21 就用 21；没装也可以在终端临时借用 IDEA 自带的：

```bash
JAVA_HOME="/Applications/IntelliJ IDEA.app/Contents/jbr/Contents/Home" ./run.sh
```

用 JDK 8 跑会直接报错并提示你换 JDK —— 那正是这个实验要说明的第一件事。

## 文件

| 文件 | 内容 |
|---|---|
| `src/Jdk8Style.java` | 五件事的 Java 8 写法：手写 DTO、`\n` 拼报文、`switch`+`break`、`instanceof`+强转、固定线程池 |
| `src/Jdk17Style.java` | 同样五件事的 17 写法：`record`、文本块、`switch` 表达式、模式匹配、`toList()` |
| `src/Jdk21Style.java` | 只有 21 才有的：switch 模式匹配 + 记录解构、顺序集合、虚拟线程 |
| `src/JdkLab.java` | 主程序，逐项打印两种写法的结果对照 |

## 三个版本到底差在哪

### 一、写代码时天天用得上的语法

| 能力 | Java 8 | Java 17 / 21 |
|---|---|---|
| 不可变数据类 | 字段+构造+getter+equals+hashCode+toString 全手写（30 行） | `record Contract(String bizId, double amount) {}`（1 行） |
| 多行字符串 | `"{\n" + "  \"a\": 1\n" + "}"` | 文本块 `"""` |
| 条件分支 | `switch` + `break`，忘了 break 就穿透 | `switch` 表达式 `case 1 -> "草稿";`，漏分支编译不过 |
| 类型判断 | `instanceof` + 显式强转 | `instanceof String s`，判断赋值合一 |
| 局部变量类型 | 必须写全类型 | `var list = new ArrayList<String>();` |
| 空值处理 | `Optional.isPresent()` 里套 if | `isEmpty()` / `or()` / `ifPresentOrElse()` |

### 二、标准库

- `List.of` / `Map.of`（9）一行建不可变集合，`Stream.toList()`（16）不用再 `collect(Collectors.toList())`。
- `String` 加了 `isBlank` / `strip` / `repeat` / `lines` / `formatted`；`strip` 认识 Unicode 空白，`trim` 只认识 ASCII。
- `Files.readString` / `writeString`（11），读文件不用再写循环。
- `HttpClient`（11）取代 `HttpURLConnection`，支持 HTTP/2 和异步。
- 顺序集合（21）：`getFirst()` / `getLast()` / `reversed()` 变成接口标准方法。
- 还有个细节常被忽略：**Java 9 起 `String` 内部从 `char[]` 变成 `byte[]` + 编码标记（紧凑字符串）**，纯 ASCII 场景内存少一半。这也是面试里"String 有哪些变化"的答案之一。

### 三、运行时与 GC

- 默认 GC：8 是 Parallel，**9 起改成 G1**，停顿更可控；14 移除 CMS；ZGC / Shenandoah 提供低停顿，21 有分代 ZGC。
- 启动更快：类数据共享（CDS/AppCDS）默认开启。
- 虚拟线程（21 正式）：一任务一线程。实验里 2000 个各等 100ms 的任务，固定线程池 200 个线程要 1048ms，虚拟线程 133ms。
- **虚拟线程的边界**（说清这个才是加分项）：它只解决"线程在等 IO 时被白白占住"，不解决 CPU 密集；数据库连接池只有 20 个连接时，照样排队。

### 四、从 8 升上来会踩的坑（面试爱问）

| 变化 | 影响 |
|---|---|
| Java EE 模块在 11 彻底移除 | `javax.xml.bind`（JAXB）、JAX-WS、Activation 要单独引依赖 |
| 包名 `javax.*` → `jakarta.*` | Spring Boot 3 跟着换了，老代码直接编译不过 |
| Nashorn JS 引擎移除（15） | 有脚本引擎依赖的要换方案 |
| 强封装内部 API（16+） | 反射类库（老版本 Hibernate/Jackson/CGLIB）要加 `--add-opens` |
| 默认字符编码改 UTF-8（18） | 以前靠平台默认编码读文件的项目可能出现乱码 |
| `SecurityManager` 弃用（17/21） | 用它做沙箱的方案要重新设计 |
| 模块化 JPMS（9） | 不用它也能跑，但 `module-info.java` 是面试常见追问 |

## 面试话术（直接背）

> "我原来在蓝凌那批项目上用 JDK 8，后来自己用 Spring Boot 3 做实验和新项目时用 17/21。
> 升级的好处有三块：语法上 record、文本块、switch 表达式、instanceof 模式匹配把样板代码砍掉一大半；运行时上默认 GC 从 Parallel 变成 G1，还有 ZGC 这种低停顿收集器；生态上 Spring Boot 3 直接要求 17 起步，不升就没法用新框架。
> 成本也很实在：Java EE 模块被移出 JDK，JAXB 这类要单独引依赖；`javax` 变 `jakarta`；一些反射类库因为内部 API 强封装要加 `--add-opens`。
> 21 我还试过虚拟线程：2000 个各等 100 毫秒的任务，固定线程池要 1 秒，虚拟线程 130 多毫秒。但它只解决阻塞等待的问题，不解决 CPU 密集，也绕不开数据库连接池的排队。"

最后一句提醒：**21 里的结构化并发、字符串模板这类还是预览特性，别说成生产在用**。面试官追一句"你在哪个项目用的"，答不上来就减分。
