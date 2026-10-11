# Spring 注解速查（边问边记）

> 用法：你每问我一个看不懂的注解 / API / 写法，我就往这份文件里加一条。
> 每一条都按同一个模板写：**它是什么 → 它怎么生效 → 在我们 demo 的哪一行 → 去掉会怎样 → 面试怎么答**。
> 版本：v1 · 2026-10-10

---

## 一、通用方法：拿到一个不认识的注解，3 分钟自己看懂

别急着搜中文博客（版本经常过期）。按这五步走，任何注解都能拆开：

### 第 1 步：看它从哪个包来（最快的一步）

`import org.springframework.boot.context.properties.ConfigurationProperties;`

包名就是身份证明：`org.springframework.boot` 说明是 Spring Boot 自己的（不是 Spring Framework 也不是第三方），`context.properties` 说明它属于"配置属性"这块功能。**看包名能立刻排除掉 90% 的错误猜测。**

### 第 2 步：看它的元注解（决定它能贴在哪、什么时候还在）

注解头上的注解就是它的能力边界：

| 元注解 | 告诉你什么 |
|---|---|
| `@Target({TYPE, METHOD})` | 只能贴在**类**或**方法**上，贴到字段上直接编译不过 |
| `@Retention(RUNTIME)` | 运行时还保留着 → 说明框架能在运行时读到它（这是"框架能生效"的前提） |
| `@Documented` | 会出现在 javadoc 里 |
| `@Indexed` | Spring 的类扫描优化标记，编译期生成索引文件，加快启动扫描 |
| `@AliasFor` | 两个元素互为别名（下面 @ConfigurationProperties 就用了） |

**关键认知：注解本身不干活，它只是"贴在代码上的标签"，必须有别的代码去读它。** 所以第 3 步最重要。

### 第 3 步：看它声明了哪些元素（能配什么、默认值是什么）

注解里的方法（带默认值）就是它的参数，比如 `prefix()`、`ignoreUnknownFields()`。
**默认值决定了"你不写它会怎样"**，这是排查问题的关键。

### 第 4 步：找"谁在处理它"

这是理解机制的核心一步。方法有两条：
- IDEA 里 `Alt+F7`（Find Usages）搜注解，或者 `Shift+Cmd+F` 全局搜类名；
- 更省事的办法：**看同一个包里还有哪些类**。Spring 的命名很直白，`ConfigurationProperties` 旁边就躺着 `ConfigurationPropertiesBinder`、`ConfigurationPropertiesBeanRegistrar` —— 一眼就知道是它们俩在干活（一个负责注册 Bean，一个负责把值绑到字段上）。

### 第 5 步：做一次破坏性实验 + 查官方文档

- **改坏它**：把前缀改错、把属性名改成不存在的、把注解删掉，看报什么错。报错信息往往直接告诉你机制（这一招最快，30 秒出答案）。
- **查文档**：机制的权威解释在 Spring Boot 官方 reference 的 *Externalized Configuration* / *Type-safe Configuration Properties* 一节；查 API 签名用 javadoc；查"以前是什么行为、哪个版本改的"用 release notes。

### 顺手记几个 IDEA 快捷键

| 快捷键 | 作用 |
|---|---|
| `Cmd+B` / `Cmd+Click` | 跳到定义（看注解源码、看方法实现） |
| `Alt+F7` | Find Usages，找谁在用 / 谁在处理 |
| `Shift+Cmd+F` | 全局搜字符串（找类名、找配置项） |
| `Shift+Shift` | 搜类名 / 文件 |
| `Ctrl+H` | 看类型继承树（看接口有几个实现） |
| `Cmd+F12` | 看当前类的方法列表 |

### 附：这台机器没有 javap，怎么直接"拆"一个 class 文件

IntelliJ 自带的 JBR 里没有 `javap`，但可以用 `strings` 从 class 文件的常量池里读出它声明了哪些东西。本文档第二节的那份"剖面图"就是这么来的：

```bash
JAR=~/.m2/repository/org/springframework/boot/spring-boot/3.5.0/spring-boot-3.5.0.jar
unzip -p "$JAR" org/springframework/boot/context/properties/ConfigurationProperties.class | strings
```

---

## 二、`@ConfigurationProperties`

### 它是什么（一句话）

**把配置文件里同一个前缀的一整组属性，一次性绑定到一个 Java 对象上。**

```java
@ConfigurationProperties(prefix = "kdx")   // ← 绑 application.yml 里 kdx.* 那一组
public class KdxProperties {
    private String baseUrl;                // ← 绑 kdx.base-url
    private int connectTimeoutMillis;      // ← 绑 kdx.connect-timeout-millis
}
```

### 它的真实声明（我从 class 文件里读出来的）

```
@Target(ElementType...)        ← 只能贴类 / 方法
@Retention(RetentionPolicy...) ← 运行时保留，所以框架能读到
@Documented
@Indexed                       ← Spring 的扫描优化
@AliasFor                       ← 元素之间有别名关系
元素：prefix、ignoreInvalidFields、ignoreUnknownFields
```

两个要点：

1. **`value()` 和 `prefix()` 互为别名**（靠 `@AliasFor` 实现），所以 `@ConfigurationProperties("kdx")` 和 `@ConfigurationProperties(prefix = "kdx")` 完全等价。这是你读别人代码时容易困惑的地方。
2. **`ignoreUnknownFields` 默认 true**：yml 里多写了不存在的属性，不会报错，而是被静默忽略 —— 所以"配置写了但没生效"往往不是绑定失败，而是名字写错了。

### 它怎么生效（注解不干活，这条链路才是重点）

```
① 得先被注册成 Bean（否则它不是 Bean，注入直接报错）
       方式 A：注解上再加 @Component
       方式 B：某个 @Configuration 上写 @EnableConfigurationProperties(KdxProperties.class)   ← 我们 demo 用的之一
       方式 C：启动类上写 @ConfigurationPropertiesScan，自动扫所有 @ConfigurationProperties  ← 我们 demo 用的主方式
② ConfigurationPropertiesBeanRegistrar / Binder 出场
       启动时读 Environment（yml、环境变量、命令行参数），按前缀把值绑到字段上，并做类型转换
③ 你注入这个 Bean 直接用
```

### 和 `@Value` 的取舍（面试常问"你为什么用这个不用 @Value"）

| 维度 | `@Value("${kdx.base-url}")` | `@ConfigurationProperties` |
|---|---|---|
| 组织形式 | 一个属性一个注解，散落各处 | 一组属性一个类，集中 |
| 类型安全 | 字符串拼写错要到运行时才炸 | 类型不匹配启动就报错 |
| 批量绑定 | 每个字段写一遍 | 自动按名字和类型匹配 |
| 复杂结构 | 嵌套对象、List、Map 很别扭 | 原生支持 |
| 校验 | 不支持 | 加 `@Validated` + `@NotBlank` 等即可 |
| IDE 提示 | 有 | 需要额外的 `spring-boot-configuration-processor`（可选依赖，只生成元数据，不影响运行） |
| 松散绑定 | 不支持 | 支持：`redis-key-prefix` ↔ `redisKeyPrefix` |

**一句话结论**：一两个零散配置用 `@Value` 无所谓；成组配置、还要校验，就用 `@ConfigurationProperties`。

### 五个真实的坑

1. **忘了注册成 Bean**：只写 `@ConfigurationProperties` 不写 `@Component` / `@EnableConfigurationProperties` / 不加 `@ConfigurationPropertiesScan` → 注入时报"找不到 Bean"。这是最常见的坑。
2. **名字写错静默为 null**：`lab.dedup-ttl` 写成了 `lab.dedup-tt`，字段保持默认值，不报错。排查手段：启动日志里打一行、或者写个 `/api/infra` 这样的自检接口把值打出来（我们 demo 就有）。
3. **没有 setter 绑不上**（除非用构造器绑定）。Boot 3 里 record + 构造器绑定是推荐写法：`@ConfigurationProperties(prefix = "x") public record XConfig(String a, String b) {}`。
4. **校验不是自动的**：要在类上加 `@Validated`，字段上加 `@NotBlank` / `@Min` 之类，否则写了也不生效。
5. **环境变量优先级高于 yml**：`MYSQL_PASSWORD=xxx` 会覆盖 yml 里的 `password`。排查"我明明改了 yml 怎么没用"时，先想这条。

### 在我们 demo 里的位置

| 文件 | 作用 |
|---|---|
| `config/KdxProperties.java` | 绑 `kdx.*`：金蝶地址、appKey、超时时间 |
| `config/LabProperties.java` | 绑 `lab.*`：用内存还是 Redis、key 前缀、TTL、对账阈值 |
| `LabApplication.java` | `@ConfigurationPropertiesScan`，自动扫描注册 |
| `config/InfraConfig.java` | `@EnableConfigurationProperties(LabProperties.class)`，显式注册的写法 |
| `resources/application.yml`、`application-infra.yml` | 两份配置，靠 profile 切换 |

### 三分钟自己验证（改坏它）

1. 把 `application.yml` 里的 `lab.dedup-ttl-seconds: 3600` 改成 `lab.dedup-ttl-seconds-typo: 3600`，重启后调 `GET /api/infra` → TTL 会保持默认值 3600，**不报错**（对应坑 2）。
2. 把 `LabProperties` 上的 `@ConfigurationProperties(prefix = "lab")` 删掉，重启 → 启动直接失败，报找不到 `LabProperties` 这个 Bean（对应坑 1）。
3. 把 `application.yml` 的 `redis-key-prefix` 改成 `redisKeyPrefix`，重启去 `/api/infra` 看前缀 → **照样生效**，这就是松散绑定。

### 面试怎么答

> `@ConfigurationProperties` 是把一组同前缀的配置绑到一个 Java 对象上，替代散落各处的 `@Value`。
> 好处是成组、类型安全、能加 `@Validated` 做校验、支持松散绑定和嵌套结构。
> 用法上有两种注册方式：给配置类加 `@Component`，或者在启动类上加 `@ConfigurationPropertiesScan`。
> 有个坑要注意：属性名写错时默认是静默忽略（`ignoreUnknownFields` 默认 true），字段会保持默认值不报错，
> 所以我们一般会在启动时打一行关键配置，或者做个自检接口把实际生效的值打出来。

---

## 变更记录

| 版本 | 日期 | 内容 |
|---|---|---|
| v1 | 2026-10-10 | 建立通用查法五步 + 第一条注解 `@ConfigurationProperties`。 |
