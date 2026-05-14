# Dynamo

> Java 动态线程池 - 运行时可调参数,零依赖。

[English](README.md)

[![License](https://img.shields.io/badge/license-Apache%202-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![JDK](https://img.shields.io/badge/JDK-8+-green.svg)](https://www.oracle.com/java/technologies/javase-downloads.html)
[![Maven Central](https://img.shields.io/badge/maven--central-1.0.0-orange.svg)](https://github.com/itlemon/dynamo)

## Dynamo 是什么?

Dynamo 是一个**轻量级、零依赖**的 Java 8+ 动态线程池库。它允许你在运行时动态调整 `corePoolSize`、`maximumPoolSize`、`keepAliveTime` 和 `queueCapacity`,无需重启应用。

与传统的 `ThreadPoolExecutor` 不同,Dynamo 使用 `Supplier<Integer>` 来解耦动态值与具体配置源(Nacos、Apollo、数据库、文件、甚至内存变量)。你只需提供"值从哪来",Dynamo 负责"何时应用"。

### 为什么选择 Dynamo?

- **零依赖**:只需要 JDK 8+,不引入任何第三方 jar
- **零配置**:自动探测 slf4j / log4j2 / JUL 日志框架
- **基于 Supplier**:适配任意配置中心(Nacos、Apollo、Consul、ZooKeeper 等)
- **支持 static final**:线程池可以声明为 `static final`
- **指标 & 事件**:内置指标暴露和参数变更监听器
- **生产级别**:无反射(队列扩容),无需 JVM 启动参数

## 快速开始

### Maven 依赖

```xml
<dependency>
    <groupId>cn.codingguide</groupId>
    <artifactId>dynamo</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 基础用法

```java
import cn.codingguide.dynamo.DynamicThreadPoolExecutor;

public class OrderService {

    // 线程池引用是 static final,但 core/max 大小是动态的!
    private static final DynamicThreadPoolExecutor POOL =
        DynamicThreadPoolExecutor.dynamic(
            () -> NacosConfig.getInt("order.pool.core", 4),
            () -> NacosConfig.getInt("order.pool.max", 32)
        );

    public void submitOrder(Order order) {
        POOL.execute(() -> processOrder(order));
    }
}
```

就这么简单!当 `NacosConfig` 的值变化时(通过 Nacos 配置中心),Dynamo 会在 5 秒内(默认刷新间隔)自动应用新值到线程池。

## 常用场景

### 1. 最简用法(自动命名)

```java
// 线程池名称自动从类名派生:"dynamic-OrderService-0/1/..."
private static final DynamicThreadPoolExecutor POOL =
    DynamicThreadPoolExecutor.dynamic(
        () -> config.getInt("core", 4),
        () -> config.getInt("max", 16)
    );
```

### 2. 指定名称

```java
private static final DynamicThreadPoolExecutor POOL =
    DynamicThreadPoolExecutor.dynamic(
        "order-pool",  // 线程名:"dynamic-order-pool-0/1/..."
        () -> config.getInt("core", 4),
        () -> config.getInt("max", 16)
    );
```

### 3. 全部参数动态化

```java
private static final DynamicThreadPoolExecutor POOL =
    DynamicThreadPoolExecutor.builder()
        .threadPoolName("order")
        .corePoolSize(() -> config.getInt("pool.core", 4))
        .maximumPoolSize(() -> config.getInt("pool.max", 32))
        .keepAliveSeconds(() -> config.getLong("pool.keepAlive", 60L))
        .queueCapacity(() -> config.getInt("pool.queue", 2048))
        .build();
```

### 4. 混合:部分静态、部分动态

```java
private static final DynamicThreadPoolExecutor POOL =
    DynamicThreadPoolExecutor.builder()
        .corePoolSize(() -> config.getInt("core", 4))
        .maximumPoolSize(() -> config.getInt("max", 32))
        .keepAliveSeconds(30L)           // 静态:永远 30 秒
        .queueCapacity(1024)             // 静态:永远 1024
        .build();
```

### 5. 自定义队列(如 SynchronousQueue)

```java
private static final DynamicThreadPoolExecutor POOL =
    DynamicThreadPoolExecutor.builder()
        .corePoolSize(() -> config.getInt("core", 8))
        .maximumPoolSize(() -> config.getInt("max", 64))
        .workQueue(new SynchronousQueue<Runnable>())  // 无缓冲队列
        .build();
```

### 6. 自定义拒绝策略

```java
private static final DynamicThreadPoolExecutor POOL =
    DynamicThreadPoolExecutor.builder()
        .corePoolSize(() -> config.getInt("core", 4))
        .maximumPoolSize(() -> config.getInt("max", 32))
        .rejectedHandler(new ThreadPoolExecutor.CallerRunsPolicy())  // 调用者运行
        .build();
```

### 7. 指标监控

```java
DynamicThreadPoolExecutor pool = DynamicThreadPoolExecutor.dynamic(...);

// 获取指标快照
ThreadPoolMetrics metrics = pool.getMetrics();
logger.info("线程池[{}] 活跃={}/{} 队列={}/{}({:.2f}%) 拒绝={}",
    metrics.getPoolName(),
    metrics.getActiveCount(),
    metrics.getMaximumPoolSize(),
    metrics.getQueueSize(),
    metrics.getQueueCapacity(),
    metrics.queueUtilization() * 100,
    metrics.getRejectedCount());

// 接入 Prometheus(示例)
Executors.newScheduledThreadPool(1).scheduleAtFixedRate(() -> {
    ThreadPoolMetrics m = pool.getMetrics();
    Metrics.gauge("dtp_active_threads", "pool", m.getPoolName()).set(m.getActiveCount());
    Metrics.gauge("dtp_queue_size", "pool", m.getPoolName()).set(m.getQueueSize());
    Metrics.gauge("dtp_queue_util", "pool", m.getPoolName()).set(m.queueUtilization());
}, 0, 10, TimeUnit.SECONDS);
```

### 8. 参数变更监听(审计 / 告警)

```java
private static final DynamicThreadPoolExecutor POOL =
    DynamicThreadPoolExecutor.builder()
        .corePoolSize(() -> config.getInt("core", 4))
        .maximumPoolSize(() -> config.getInt("max", 32))
        // 审计日志
        .addChangeListener(event -> {
            auditLogger.info("参数变更: {}", event);
        })
        // 剧烈变化告警
        .addChangeListener(event -> {
            if (event.getType() == ParameterType.CORE_POOL_SIZE) {
                int oldCore = (Integer) event.getOldValue();
                int newCore = (Integer) event.getNewValue();
                if (Math.abs(newCore - oldCore) > oldCore * 0.5) {
                    alertService.send("核心线程数剧烈变化: " + oldCore + " -> " + newCore);
                }
            }
        })
        .build();
```

## 配置中心集成

Dynamo 与**配置源无关**。只要你能把它包装成 `Supplier`,任何配置中心都可以接入。

### Nacos

```java
public class NacosConfig {
    private static final Map<String, String> CACHE = new ConcurrentHashMap<>();

    static {
        ConfigService cs = NacosFactory.createConfigService(props);
        String initial = cs.getConfig("pool.properties", "DEFAULT_GROUP", 3000);
        parseInto(initial, CACHE);
        cs.addListener("pool.properties", "DEFAULT_GROUP", new Listener() {
            @Override
            public void receiveConfigInfo(String content) {
                parseInto(content, CACHE);
            }
        });
    }

    public static int getInt(String key, int defaultValue) {
        String v = CACHE.get(key);
        try {
            return v == null ? defaultValue : Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}

// 使用
DynamicThreadPoolExecutor.dynamic(
    () -> NacosConfig.getInt("pool.core", 4),
    () -> NacosConfig.getInt("pool.max", 32)
);
```

### Apollo

```java
public class ApolloConfig {
    private static final Config config = ConfigService.getAppConfig();

    public static int getInt(String key, int defaultValue) {
        return config.getIntProperty(key, defaultValue).get();
    }
}

// 使用
DynamicThreadPoolExecutor.dynamic(
    () -> ApolloConfig.getInt("pool.core", 4),
    () -> ApolloConfig.getInt("pool.max", 32)
);
```

### 内存变量(测试 / 手动控制)

```java
AtomicInteger core = new AtomicInteger(4);
AtomicInteger max = new AtomicInteger(16);

DynamicThreadPoolExecutor pool = DynamicThreadPoolExecutor.dynamic(
    core::get,
    max::get
);

// 运行时调整
core.set(8);  // 5 秒内生效
max.set(32);
```

## 高级特性

### 指标采集

```java
ThreadPoolMetrics metrics = pool.getMetrics();

// 运行指标
metrics.getActiveCount();           // 活跃线程数
metrics.getQueueSize();             // 当前队列大小
metrics.getRejectedCount();         // 累计拒绝数

// 参数快照
metrics.getCorePoolSize();          // 当前核心线程数
metrics.getMaximumPoolSize();       // 当前最大线程数

// 衍生指标
metrics.queueUtilization();         // 队列使用率: 0.0 ~ 1.0
metrics.poolUtilization();          // 线程池使用率: 0.0 ~ 1.0
```

### 参数变更事件

```java
DynamicThreadPoolExecutor pool = DynamicThreadPoolExecutor.builder()
    .corePoolSize(() -> config.core())
    .maximumPoolSize(() -> config.max())
    .addChangeListener(event -> {
        System.out.printf("参数 %s 变更: %s -> %s,时间 %s%n",
            event.getType(),
            event.getOldValue(),
            event.getNewValue(),
            new Date(event.getTimestamp()));
    })
    .build();
```

### 自定义刷新间隔

```java
// 默认 5 秒;可调快或调慢
DynamicThreadPoolExecutor pool = DynamicThreadPoolExecutor.builder()
    .corePoolSize(() -> config.core())
    .maximumPoolSize(() -> config.max())
    .refreshInterval(Duration.ofSeconds(1))  // 每秒检查一次
    .build();
```

## 设计亮点

### 1. 基于 Supplier 的抽象

Dynamo 不与特定配置中心耦合,而是接受 `Supplier<Integer>`:

```java
// 之前:与 Nacos 紧耦合
pool.bindConfigSource(new NacosConfigSource(...));

// Dynamo:通过 Supplier 解耦
pool = DynamicThreadPoolExecutor.dynamic(
    () -> anyConfigSource.getInt("core"),  // 可以是 Nacos、Apollo、文件、数据库,任意来源
    () -> anyConfigSource.getInt("max")
);
```

### 2. 零依赖

Dynamo **零运行时依赖**:

- 不依赖 slf4j-api(通过反射探测)
- 不依赖 Nacos / Apollo 客户端
- 不依赖 Spring 框架
- 只需要 JDK 8+

### 3. 安全的参数调整

Dynamo 处理了调整 core/max 时的微妙顺序问题:

- **扩容**:先设置 `maximumPoolSize`,再设置 `corePoolSize`
- **缩容**:先设置 `corePoolSize`,再设置 `maximumPoolSize`
- 避免 `IllegalArgumentException: corePoolSize > maximumPoolSize`

### 4. 队列扩容无反射

与某些使用反射修改 JDK `final capacity` 字段的实现不同:

- Dynamo 自己实现了 `ResizableCapacityLinkedBlockingQueue`
- 不需要 `--add-opens java.base/java.util.concurrent=ALL-UNNAMED` 参数
- 在 JDK 8/11/17/21 上无需任何 JVM 参数即可运行

## 日志

Dynamo 自动探测 classpath 上的日志框架:

1. **slf4j**(如果存在 `org.slf4j.LoggerFactory`)→ 路由到你的 logback/log4j2
2. **log4j2**(如果存在 `org.apache.logging.log4j.LogManager`)→ 路由到 log4j2
3. **JUL**(java.util.logging,兜底)→ JDK 内置

**无需任何配置**。日志会自动出现在你现有的日志系统里。

## 最佳实践

### Supplier 实现

你的 `Supplier` 实现应该:

- **非阻塞**:不要在 `Supplier.get()` 里做网络调用或数据库查询
- **快速返回**:从内存缓存读取(如 `AtomicInteger`、`ConcurrentHashMap`)
- **允许异常**:Dynamo 会捕获异常并跳过本轮刷新

**推荐模式:**

```java
// 错误:在 Supplier 里做网络调用(阻塞刷新线程)
() -> nacosClient.getConfig("pool.core", "DEFAULT_GROUP")  // ❌ 错误

// 正确:从内存缓存读取
() -> NacosConfig.getInt("pool.core", 4)  // ✅ 正确

// NacosConfig 内部用 Nacos 监听器更新缓存:
class NacosConfig {
    static Map<String, String> CACHE = new ConcurrentHashMap<>();
    static {
        configService.addListener(..., content -> parseInto(content, CACHE));
    }
}
```

### 拒绝策略选择

| 业务场景 | 推荐策略 |
| --- | --- |
| 非关键任务(日志、指标、缓存刷新) | `LoggingDiscardPolicy`(默认) |
| 关键任务(订单、支付) | `CallerRunsPolicy` |
| 快速失败 + 外部重试 | `LoggingAbortPolicy` |
| 死信队列 | 自定义 `RejectedExecutionHandler` |

### 线程池大小设置

```java
// CPU 密集型任务
corePoolSize = CPU 核心数
maximumPoolSize = CPU 核心数 * 2

// IO 密集型任务
corePoolSize = CPU 核心数 * 2
maximumPoolSize = CPU 核心数 * 4
```

## 参与贡献

欢迎贡献代码!请随时提交 Pull Request。

## 开源协议

Apache License 2.0 - 详见 [LICENSE](LICENSE)。

## 作者

**itlemon** - [GitHub](https://github.com/itlemon)

## 相关链接

- GitHub: https://github.com/itlemon/dynamo
- 文档: https://github.com/itlemon/dynamo/wiki
- 问题反馈: https://github.com/itlemon/dynamo/issues
