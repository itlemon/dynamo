# Dynamo

> The Dynamic Thread Pool for Java - Runtime adjustable thread pools with zero dependencies.

[中文文档](README_CN.md)

[![License](https://img.shields.io/badge/license-Apache%202-blue.svg)](https://www.apache.org/licenses/LICENSE-2.0)
[![JDK](https://img.shields.io/badge/JDK-8+-green.svg)](https://www.oracle.com/java/technologies/javase-downloads.html)
[![Maven Central](https://img.shields.io/badge/maven--central-1.0.0-orange.svg)](https://github.com/itlemon/dynamo)

## What is Dynamo?

Dynamo is a **lightweight, zero-dependency** dynamic thread pool library for Java 8+. It allows you to adjust `corePoolSize`, `maximumPoolSize`, `keepAliveTime`, and `queueCapacity` at runtime without restarting your application.

Unlike traditional `ThreadPoolExecutor`, Dynamo uses `Supplier<Integer>` to decouple dynamic values from specific config sources (Nacos, Apollo, database, file, or even in-memory variables). You provide the "how to get the value", Dynamo handles the "when to apply it".

### Why Dynamo?

- **Zero dependencies**: Only JDK 8+ required, no third-party jars
- **Zero configuration**: Auto-detects slf4j / log4j2 / JUL logging
- **Supplier-based**: Works with any config source (Nacos, Apollo, Consul, ZooKeeper, etc.)
- **Static final friendly**: Thread pool can be declared as `static final`
- **Metrics & events**: Built-in metrics exposure and parameter change listeners
- **Production-ready**: No reflection (on queue resize), no JVM flags required

## Quick Start

### Maven Dependency

```xml
<dependency>
    <groupId>cn.codingguide</groupId>
    <artifactId>dynamo</artifactId>
    <version>1.0.0</version>
</dependency>
```

### Basic Usage

```java
import cn.codingguide.dynamo.DynamicThreadPoolExecutor;

public class OrderService {

    // Thread pool reference is static final, but core/max sizes are dynamic!
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

That's it! When `NacosConfig` values change (via Nacos config center), Dynamo automatically applies the new values to the thread pool within 5 seconds (default refresh interval).

## Common Usage Scenarios

### 1. Minimal Setup (Auto-Named)

```java
// Thread pool name auto-derived from class name: "dynamic-OrderService-0/1/..."
private static final DynamicThreadPoolExecutor POOL =
    DynamicThreadPoolExecutor.dynamic(
        () -> config.getInt("core", 4),
        () -> config.getInt("max", 16)
    );
```

### 2. With Custom Name

```java
private static final DynamicThreadPoolExecutor POOL =
    DynamicThreadPoolExecutor.dynamic(
        "order-pool",  // Thread names: "dynamic-order-pool-0/1/..."
        () -> config.getInt("core", 4),
        () -> config.getInt("max", 16)
    );
```

### 3. All Parameters Dynamic

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

### 4. Mixed: Some Static, Some Dynamic

```java
private static final DynamicThreadPoolExecutor POOL =
    DynamicThreadPoolExecutor.builder()
        .corePoolSize(() -> config.getInt("core", 4))
        .maximumPoolSize(() -> config.getInt("max", 32))
        .keepAliveSeconds(30L)           // Static: always 30 seconds
        .queueCapacity(1024)             // Static: always 1024
        .build();
```

### 5. Custom Queue (e.g., SynchronousQueue)

```java
private static final DynamicThreadPoolExecutor POOL =
    DynamicThreadPoolExecutor.builder()
        .corePoolSize(() -> config.getInt("core", 8))
        .maximumPoolSize(() -> config.getInt("max", 64))
        .workQueue(new SynchronousQueue<Runnable>())  // No queue buffering
        .build();
```

### 6. Custom Rejection Policy

```java
private static final DynamicThreadPoolExecutor POOL =
    DynamicThreadPoolExecutor.builder()
        .corePoolSize(() -> config.getInt("core", 4))
        .maximumPoolSize(() -> config.getInt("max", 32))
        .rejectedHandler(new ThreadPoolExecutor.CallerRunsPolicy())  // Caller runs
        .build();
```

### 7. Metrics & Monitoring

```java
DynamicThreadPoolExecutor pool = DynamicThreadPoolExecutor.dynamic(...);

// Get metrics snapshot
ThreadPoolMetrics metrics = pool.getMetrics();
logger.info("Pool[{}] active={}/{} queue={}/{} util={:.2f}% rejected={}",
    metrics.getPoolName(),
    metrics.getActiveCount(),
    metrics.getMaximumPoolSize(),
    metrics.getQueueSize(),
    metrics.getQueueCapacity(),
    metrics.queueUtilization() * 100,
    metrics.getRejectedCount());

// Integrate with Prometheus (example)
Executors.newScheduledThreadPool(1).scheduleAtFixedRate(() -> {
    ThreadPoolMetrics m = pool.getMetrics();
    Metrics.gauge("dtp_active_threads", "pool", m.getPoolName()).set(m.getActiveCount());
    Metrics.gauge("dtp_queue_size", "pool", m.getPoolName()).set(m.getQueueSize());
    Metrics.gauge("dtp_queue_util", "pool", m.getPoolName()).set(m.queueUtilization());
}, 0, 10, TimeUnit.SECONDS);
```

### 8. Parameter Change Listeners (Audit / Alerting)

```java
private static final DynamicThreadPoolExecutor POOL =
    DynamicThreadPoolExecutor.builder()
        .corePoolSize(() -> config.getInt("core", 4))
        .maximumPoolSize(() -> config.getInt("max", 32))
        // Audit log
        .addChangeListener(event -> {
            auditLogger.info("Parameter changed: {}", event);
        })
        // Alert on drastic change
        .addChangeListener(event -> {
            if (event.getType() == ParameterType.CORE_POOL_SIZE) {
                int oldCore = (Integer) event.getOldValue();
                int newCore = (Integer) event.getNewValue();
                if (Math.abs(newCore - oldCore) > oldCore * 0.5) {
                    alertService.send("Core size changed drastically: " + oldCore + " -> " + newCore);
                }
            }
        })
        .build();
```

## Integration with Config Centers

Dynamo is **config-source agnostic**. It works with any config center as long as you can wrap it in a `Supplier`.

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

// Usage
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

// Usage
DynamicThreadPoolExecutor.dynamic(
    () -> ApolloConfig.getInt("pool.core", 4),
    () -> ApolloConfig.getInt("pool.max", 32)
);
```

### In-Memory (for Testing / Manual Control)

```java
AtomicInteger core = new AtomicInteger(4);
AtomicInteger max = new AtomicInteger(16);

DynamicThreadPoolExecutor pool = DynamicThreadPoolExecutor.dynamic(
    core::get,
    max::get
);

// Adjust at runtime
core.set(8);  // Applied within 5 seconds
max.set(32);
```

## Advanced Features

### Metrics

```java
ThreadPoolMetrics metrics = pool.getMetrics();

// Runtime metrics
metrics.getActiveCount();           // Active threads
metrics.getQueueSize();             // Current queue size
metrics.getRejectedCount();         // Total rejected tasks

// Parameter snapshot
metrics.getCorePoolSize();          // Current core size
metrics.getMaximumPoolSize();       // Current max size

// Derived metrics
metrics.queueUtilization();         // Queue usage: 0.0 ~ 1.0
metrics.poolUtilization();          // Pool usage: 0.0 ~ 1.0
```

### Parameter Change Events

```java
DynamicThreadPoolExecutor pool = DynamicThreadPoolExecutor.builder()
    .corePoolSize(() -> config.core())
    .maximumPoolSize(() -> config.max())
    .addChangeListener(event -> {
        System.out.printf("Parameter %s changed: %s -> %s at %s%n",
            event.getType(),
            event.getOldValue(),
            event.getNewValue(),
            new Date(event.getTimestamp()));
    })
    .build();
```

### Custom Refresh Interval

```java
// Default is 5 seconds; adjust for faster or slower polling
DynamicThreadPoolExecutor pool = DynamicThreadPoolExecutor.builder()
    .corePoolSize(() -> config.core())
    .maximumPoolSize(() -> config.max())
    .refreshInterval(Duration.ofSeconds(1))  // Check every 1 second
    .build();
```

## Design Highlights

### 1. Supplier-Based Abstraction

Instead of coupling with specific config centers, Dynamo accepts `Supplier<Integer>`:

```java
// Before: Tightly coupled with Nacos
pool.bindConfigSource(new NacosConfigSource(...));

// Dynamo: Decouple via Supplier
pool = DynamicThreadPoolExecutor.dynamic(
    () -> anyConfigSource.getInt("core"),  // Could be Nacos, Apollo, file, DB, anything
    () -> anyConfigSource.getInt("max")
);
```

### 2. Zero Dependencies

Dynamo has **ZERO runtime dependencies**:

- No slf4j-api (uses reflection to detect)
- No Nacos / Apollo client
- No Spring framework
- Just JDK 8+

### 3. Safe Parameter Adjustment

Dynamo handles the tricky order when adjusting core/max sizes:

- **Expansion**: Set `maximumPoolSize` first, then `corePoolSize`
- **Shrinkage**: Set `corePoolSize` first, then `maximumPoolSize`
- This avoids `IllegalArgumentException: corePoolSize > maximumPoolSize`

### 4. No Reflection on Queue Resize

Unlike some implementations that use reflection to modify JDK's `final capacity` field:

- Dynamo implements its own `ResizableCapacityLinkedBlockingQueue`
- No `--add-opens java.base/java.util.concurrent=ALL-UNNAMED` required
- Works on JDK 8/11/17/21 without JVM flags

## Logging

Dynamo auto-detects logging backend on classpath:

1. **slf4j** (if `org.slf4j.LoggerFactory` exists) → routes to your logback/log4j2
2. **log4j2** (if `org.apache.logging.log4j.LogManager` exists) → routes to log4j2
3. **JUL** (java.util.logging, fallback) → JDK built-in

**No configuration needed.** Logs automatically appear in your existing logging system.

## Best Practices

### Supplier Implementation

Your `Supplier` implementations should be:

- **Non-blocking**: Don't make network calls or DB queries inside `Supplier.get()`
- **Fast**: Read from in-memory cache (e.g., `AtomicInteger`, `ConcurrentHashMap`)
- **Exception-tolerant**: Dynamo catches exceptions and skips the refresh cycle

**Recommended pattern:**

```java
// Bad: Network call in Supplier (blocks refresh thread)
() -> nacosClient.getConfig("pool.core", "DEFAULT_GROUP")  // ❌ WRONG

// Good: Read from memory cache
() -> NacosConfig.getInt("pool.core", 4)  // ✅ CORRECT

// Where NacosConfig internally uses Nacos listener to update cache:
class NacosConfig {
    static Map<String, String> CACHE = new ConcurrentHashMap<>();
    static {
        configService.addListener(..., content -> parseInto(content, CACHE));
    }
}
```

### Choosing Rejection Policy

| Scenario | Recommended Policy |
| --- | --- |
| Non-critical tasks (logging, metrics, cache refresh) | `LoggingDiscardPolicy` (default) |
| Critical tasks (orders, payments) | `CallerRunsPolicy` |
| Fail-fast + external retry | `LoggingAbortPolicy` |
| Dead letter queue | Custom `RejectedExecutionHandler` |

### Thread Pool Sizing

```java
// CPU-bound tasks
corePoolSize = CPU cores
maximumPoolSize = CPU cores * 2

// IO-bound tasks
corePoolSize = CPU cores * 2
maximumPoolSize = CPU cores * 4
```

## Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

## License

Apache License 2.0 - see [LICENSE](LICENSE) for details.

## Author

**itlemon** - [GitHub](https://github.com/itlemon)

## Links

- GitHub: https://github.com/itlemon/dynamo
- Documentation: https://github.com/itlemon/dynamo/wiki
- Issues: https://github.com/itlemon/dynamo/issues
