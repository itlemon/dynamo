package cn.codingguide.dynamo;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

import cn.codingguide.dynamo.internal.logger.Logger;
import cn.codingguide.dynamo.internal.logger.Loggers;

/**
 * A dynamic {@link ThreadPoolExecutor} with runtime-adjustable core and maximum pool sizes.
 * <p>
 * Unlike standard {@code ThreadPoolExecutor}, this implementation accepts {@link Supplier}
 * instances for {@code corePoolSize} and {@code maximumPoolSize}. The executor periodically
 * polls these suppliers and applies any changes automatically.
 * <p>
 * <b>Key features:</b>
 * <ul>
 *   <li><b>Zero dependencies:</b> Only requires JDK 8+, no third-party libraries</li>
 *   <li><b>Zero configuration:</b> Logging auto-detects slf4j / log4j2 / JUL on classpath</li>
 *   <li><b>Supplier-based:</b> Decouple dynamic values from config sources (Nacos, Apollo, etc.)</li>
 *   <li><b>Metrics exposure:</b> Get runtime metrics via {@link #getMetrics()}</li>
 *   <li><b>Change events:</b> Listen to parameter changes via {@link ParameterChangeListener}</li>
 *   <li><b>Thread-safe:</b> All parameter adjustments are done safely without task loss</li>
 * </ul>
 * <p>
 * <b>Usage example:</b>
 * <pre>{@code
 * // Static final declaration is supported
 * private static final DynamicThreadPoolExecutor POOL =
 *     DynamicThreadPoolExecutor.dynamic(
 *         () -> NacosConfig.getInt("order.core", 4),
 *         () -> NacosConfig.getInt("order.max", 32)
 *     );
 *
 * public void submit(Runnable task) {
 *     POOL.execute(task);
 * }
 * }</pre>
 *
 * @author itlemon
 * @since 1.0.0
 */
public class DynamicThreadPoolExecutor extends ThreadPoolExecutor {

    /**
     * Shared daemon thread for refreshing all dynamic thread pools.
     */
    private static final ScheduledExecutorService REFRESHER =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "dtp-refresher");
                t.setDaemon(true);
                return t;
            });

    private final String poolName;
    private final Supplier<Integer> coreSupplier;
    private final Supplier<Integer> maxSupplier;
    private final Supplier<Long> keepAliveSecSupplier;
    private final Supplier<Integer> queueCapacitySupplier;
    private final ResizableCapacityLinkedBlockingQueue<Runnable> resizableQueue;
    private final ScheduledFuture<?> refreshFuture;
    private final LongAdder rejectedCount = new LongAdder();
    private final Logger log;
    private final List<ParameterChangeListener> changeListeners;

    private volatile int lastCore;
    private volatile int lastMax;
    private volatile int lastQueueCapacity;
    private volatile long lastKeepAliveSec;

    /**
     * Create a dynamic thread pool with only core and max size suppliers.
     * Thread pool name is auto-derived from caller class name.
     *
     * @param coreSupplier supplier for core pool size (must return positive integer)
     * @param maxSupplier  supplier for maximum pool size (must be &gt;= core size)
     * @return a new dynamic thread pool executor
     */
    public static DynamicThreadPoolExecutor dynamic(
            Supplier<Integer> coreSupplier,
            Supplier<Integer> maxSupplier) {
        return builder()
                .corePoolSize(coreSupplier)
                .maximumPoolSize(maxSupplier)
                .build();
    }

    /**
     * Create a dynamic thread pool with specified name.
     *
     * @param name         thread pool name (prefix "dynamic-" is auto-added if missing)
     * @param coreSupplier supplier for core pool size
     * @param maxSupplier  supplier for maximum pool size
     * @return a new dynamic thread pool executor
     */
    public static DynamicThreadPoolExecutor dynamic(
            String name,
            Supplier<Integer> coreSupplier,
            Supplier<Integer> maxSupplier) {
        return builder()
                .threadPoolName(name)
                .corePoolSize(coreSupplier)
                .maximumPoolSize(maxSupplier)
                .build();
    }

    /**
     * Create a builder for complex configuration scenarios.
     *
     * @return a new builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link DynamicThreadPoolExecutor}.
     */
    public static class Builder {
        private String name;
        private Supplier<Integer> coreSupplier;
        private Supplier<Integer> maxSupplier;
        private Supplier<Long> keepAliveSecSupplier;
        private Supplier<Integer> queueCapacitySupplier;
        private BlockingQueue<Runnable> customWorkQueue;
        private RejectedExecutionHandler rejectedHandler;
        private Duration refreshInterval = Duration.ofSeconds(5);
        private ThreadFactory threadFactory;
        private final List<ParameterChangeListener> changeListeners = new ArrayList<>();

        /**
         * Set the thread pool name.
         *
         * @param n thread pool name (prefix "dynamic-" is auto-added if missing)
         * @return this builder
         */
        public Builder threadPoolName(String n) {
            this.name = n;
            return this;
        }

        /**
         * Set the core pool size supplier (required).
         *
         * @param s supplier that returns core pool size
         * @return this builder
         */
        public Builder corePoolSize(Supplier<Integer> s) {
            this.coreSupplier = s;
            return this;
        }

        /**
         * Set the maximum pool size supplier (required).
         *
         * @param s supplier that returns maximum pool size
         * @return this builder
         */
        public Builder maximumPoolSize(Supplier<Integer> s) {
            this.maxSupplier = s;
            return this;
        }

        /**
         * Set keep alive time in seconds (static value).
         *
         * @param v keep alive seconds
         * @return this builder
         */
        public Builder keepAliveSeconds(final long v) {
            this.keepAliveSecSupplier = () -> v;
            return this;
        }

        /**
         * Set keep alive time supplier (dynamic value).
         *
         * @param s supplier that returns keep alive seconds
         * @return this builder
         */
        public Builder keepAliveSeconds(Supplier<Long> s) {
            this.keepAliveSecSupplier = s;
            return this;
        }

        /**
         * Set queue capacity (static value).
         *
         * @param v queue capacity
         * @return this builder
         */
        public Builder queueCapacity(final int v) {
            this.queueCapacitySupplier = () -> v;
            return this;
        }

        /**
         * Set queue capacity supplier (dynamic value).
         *
         * @param s supplier that returns queue capacity
         * @return this builder
         */
        public Builder queueCapacity(Supplier<Integer> s) {
            this.queueCapacitySupplier = s;
            return this;
        }

        /**
         * Set custom work queue (mutually exclusive with queueCapacity).
         * Note: Custom queues do NOT support dynamic capacity adjustment.
         *
         * @param q the custom blocking queue
         * @return this builder
         */
        public Builder workQueue(BlockingQueue<Runnable> q) {
            this.customWorkQueue = q;
            return this;
        }

        /**
         * Set custom rejected execution handler.
         *
         * @param h the rejected execution handler
         * @return this builder
         */
        public Builder rejectedHandler(RejectedExecutionHandler h) {
            this.rejectedHandler = h;
            return this;
        }

        /**
         * Set refresh interval for polling suppliers.
         *
         * @param d refresh interval duration
         * @return this builder
         */
        public Builder refreshInterval(Duration d) {
            this.refreshInterval = d;
            return this;
        }

        /**
         * Set custom thread factory.
         *
         * @param tf the thread factory
         * @return this builder
         */
        public Builder threadFactory(ThreadFactory tf) {
            this.threadFactory = tf;
            return this;
        }

        /**
         * Add a parameter change listener.
         * Multiple listeners can be registered and will be called in registration order.
         *
         * @param listener the listener
         * @return this builder
         */
        public Builder addChangeListener(ParameterChangeListener listener) {
            this.changeListeners.add(listener);
            return this;
        }

        /**
         * Build a {@link DynamicThreadPoolExecutor} with configured parameters.
         *
         * @return a new dynamic thread pool executor instance
         * @throws NullPointerException     if coreSupplier or maxSupplier is null
         * @throws IllegalStateException    if both workQueue and queueCapacity are set
         * @throws IllegalArgumentException if refreshInterval is not positive
         */
        public DynamicThreadPoolExecutor build() {
            if (coreSupplier == null) {
                throw new NullPointerException("corePoolSize supplier is required");
            }
            if (maxSupplier == null) {
                throw new NullPointerException("maximumPoolSize supplier is required");
            }
            if (customWorkQueue != null && queueCapacitySupplier != null) {
                throw new IllegalStateException("workQueue and queueCapacity are mutually exclusive");
            }
            if (refreshInterval == null || refreshInterval.toMillis() <= 0) {
                throw new IllegalArgumentException("refreshInterval must be > 0");
            }
            if (keepAliveSecSupplier == null) {
                keepAliveSecSupplier = () -> 60L;
            }
            if (customWorkQueue == null && queueCapacitySupplier == null) {
                queueCapacitySupplier = () -> 1024;
            }

            String prefix = normalizePrefix(name);
            Logger logger = Loggers.get("dtp." + prefix);
            if (rejectedHandler == null) {
                rejectedHandler = new LoggingDiscardPolicy(logger);
            }
            if (threadFactory == null) {
                threadFactory = defaultThreadFactory(prefix);
            }

            List<ParameterChangeListener> listeners = new CopyOnWriteArrayList<>(
                    changeListeners);

            return new DynamicThreadPoolExecutor(this, prefix, logger, listeners);
        }
    }

    private DynamicThreadPoolExecutor(Builder b, String prefix, Logger logger,
                                      List<ParameterChangeListener> listeners) {
        super(
                b.coreSupplier.get(),
                Math.max(b.coreSupplier.get(), b.maxSupplier.get()),
                b.keepAliveSecSupplier.get(),
                TimeUnit.SECONDS,
                chooseQueue(b),
                b.threadFactory,
                new AbortPolicy()
        );

        this.poolName = prefix;
        this.coreSupplier = b.coreSupplier;
        this.maxSupplier = b.maxSupplier;
        this.keepAliveSecSupplier = b.keepAliveSecSupplier;
        this.queueCapacitySupplier = b.queueCapacitySupplier;
        this.log = logger;
        this.changeListeners = listeners;

        BlockingQueue<Runnable> q = getQueue();
        if (q instanceof ResizableCapacityLinkedBlockingQueue) {
            this.resizableQueue = (ResizableCapacityLinkedBlockingQueue<Runnable>) q;
        } else {
            this.resizableQueue = null;
        }

        setRejectedExecutionHandler(wrap(b.rejectedHandler));

        this.lastCore = getCorePoolSize();
        this.lastMax = getMaximumPoolSize();
        this.lastKeepAliveSec = b.keepAliveSecSupplier.get();
        this.lastQueueCapacity = (resizableQueue != null) ? b.queueCapacitySupplier.get() : -1;

        long ms = b.refreshInterval.toMillis();
        this.refreshFuture = REFRESHER.scheduleWithFixedDelay(
                this::refresh,
                ms, ms, TimeUnit.MILLISECONDS);
    }

    private static BlockingQueue<Runnable> chooseQueue(Builder b) {
        if (b.customWorkQueue != null) {
            return b.customWorkQueue;
        }
        return new ResizableCapacityLinkedBlockingQueue<>(b.queueCapacitySupplier.get());
    }

    private void refresh() {
        try {
            Integer newCoreObj = coreSupplier.get();
            Integer newMaxObj = maxSupplier.get();
            if (newCoreObj == null || newMaxObj == null) {
                return;
            }
            int newCore = newCoreObj;
            int newMax = newMaxObj;
            if (newCore <= 0 || newMax < newCore) {
                return;
            }

            if (newCore != lastCore || newMax != lastMax) {
                int oldCore = lastCore;
                int oldMax = lastMax;

                if (newMax >= lastMax) {
                    if (newMax != lastMax) {
                        setMaximumPoolSize(newMax);
                    }
                    if (newCore != lastCore) {
                        setCorePoolSize(newCore);
                    }
                } else {
                    if (newCore != lastCore) {
                        setCorePoolSize(newCore);
                    }
                    if (newMax != lastMax) {
                        setMaximumPoolSize(newMax);
                    }
                }

                if (newCore != oldCore) {
                    fireChangeEvent(ParameterType.CORE_POOL_SIZE, oldCore, newCore);
                }
                if (newMax != oldMax) {
                    fireChangeEvent(ParameterType.MAXIMUM_POOL_SIZE, oldMax, newMax);
                }

                lastCore = newCore;
                lastMax = newMax;
            }

            Long newKeepObj = keepAliveSecSupplier.get();
            if (newKeepObj != null) {
                long newKeep = newKeepObj;
                if (newKeep >= 0 && newKeep != lastKeepAliveSec) {
                    long oldKeep = lastKeepAliveSec;
                    setKeepAliveTime(newKeep, TimeUnit.SECONDS);
                    fireChangeEvent(ParameterType.KEEP_ALIVE_SECONDS, oldKeep, newKeep);
                    lastKeepAliveSec = newKeep;
                }
            }

            if (resizableQueue != null) {
                Integer newQueueObj = queueCapacitySupplier.get();
                if (newQueueObj != null) {
                    int newQueue = newQueueObj;
                    if (newQueue > 0 && newQueue != lastQueueCapacity) {
                        int oldQueue = lastQueueCapacity;
                        resizableQueue.setCapacity(newQueue);
                        fireChangeEvent(ParameterType.QUEUE_CAPACITY, oldQueue, newQueue);
                        lastQueueCapacity = newQueue;
                    }
                }
            }
        } catch (Throwable t) {
            log.warn("refresh " + poolName + " failed", t);
        }
    }

    private void fireChangeEvent(ParameterType type, Object oldValue, Object newValue) {
        if (changeListeners.isEmpty()) {
            return;
        }
        ParameterChangeEvent event = new ParameterChangeEvent(
                poolName, type, oldValue, newValue, System.currentTimeMillis());
        for (ParameterChangeListener listener : changeListeners) {
            try {
                listener.onChange(event);
            } catch (Throwable t) {
                log.warn("ParameterChangeListener failed for " + type, t);
            }
        }
    }

    private RejectedExecutionHandler wrap(final RejectedExecutionHandler delegate) {
        return (r, e) -> {
            rejectedCount.increment();
            delegate.rejectedExecution(r, e);
        };
    }

    /**
     * Get the thread pool name.
     *
     * @return pool name
     */
    public String getPoolName() {
        return poolName;
    }

    /**
     * Get total number of rejected tasks since pool creation.
     *
     * @return rejected task count
     */
    public long getRejectedCount() {
        return rejectedCount.sum();
    }

    /**
     * Get a snapshot of current thread pool metrics.
     * <p>
     * This method is lightweight and can be called frequently (e.g., every 10 seconds)
     * for monitoring purposes.
     * </p>
     *
     * @return current metrics snapshot
     */
    public ThreadPoolMetrics getMetrics() {
        BlockingQueue<Runnable> queue = getQueue();
        int queueCap = (resizableQueue != null) ? resizableQueue.getCapacity() : -1;

        return new ThreadPoolMetrics(
                poolName,
                System.currentTimeMillis(),
                getActiveCount(),
                getTaskCount(),
                getCompletedTaskCount(),
                queue.size(),
                queue.remainingCapacity(),
                rejectedCount.sum(),
                getCorePoolSize(),
                getMaximumPoolSize(),
                getKeepAliveTime(TimeUnit.SECONDS),
                queueCap
        );
    }

    @Override
    public void shutdown() {
        if (refreshFuture != null) {
            refreshFuture.cancel(false);
        }
        super.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        if (refreshFuture != null) {
            refreshFuture.cancel(true);
        }
        return super.shutdownNow();
    }

    // ==================== Thread Naming ====================

    private static String resolveCallerClassName() {
        StackTraceElement[] stack = new Throwable().getStackTrace();
        String self = DynamicThreadPoolExecutor.class.getName();
        String bldr = self + "$Builder";
        for (StackTraceElement ste : stack) {
            String cn = ste.getClassName();
            if (cn.equals(self) || cn.equals(bldr)) {
                continue;
            }
            int dot = cn.lastIndexOf('.');
            String simple = dot < 0 ? cn : cn.substring(dot + 1);
            int dollar = simple.lastIndexOf('$');
            if (dollar >= 0) {
                simple = simple.substring(0, dollar);
            }
            return simple;
        }
        return "UnknownPool";
    }

    private static String normalizePrefix(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return "dynamic-" + resolveCallerClassName();
        }
        return raw.startsWith("dynamic-") ? raw : "dynamic-" + raw;
    }

    private static ThreadFactory defaultThreadFactory(final String prefix) {
        final AtomicInteger seq = new AtomicInteger();
        return r -> new Thread(r, prefix + "-" + seq.getAndIncrement());
    }

    // ==================== Default Rejection Policies ====================

    /**
     * Default rejection policy that logs a warning and discards the task (does NOT throw exception).
     * <p>
     * This is the safest default for most applications - rejected tasks are silently discarded
     * with a warning log, without disrupting the caller thread.
     * </p>
     */
    public static class LoggingDiscardPolicy implements RejectedExecutionHandler {
        private final Logger log;

        public LoggingDiscardPolicy() {
            this(Loggers.get("dtp"));
        }

        public LoggingDiscardPolicy(Logger log) {
            this.log = log;
        }

        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
            String name = "unknown";
            if (e instanceof DynamicThreadPoolExecutor) {
                name = ((DynamicThreadPoolExecutor) e).getPoolName();
            }
            log.warn("Task discarded by DTP. pool=" + name
                    + " core=" + e.getCorePoolSize()
                    + " max=" + e.getMaximumPoolSize()
                    + " poolSize=" + e.getPoolSize()
                    + " active=" + e.getActiveCount()
                    + " queueSize=" + e.getQueue().size()
                    + " completed=" + e.getCompletedTaskCount());
        }
    }

    /**
     * Rejection policy that logs a warning and throws {@link RejectedExecutionException}.
     * <p>
     * Use this for critical tasks where rejection should fail-fast.
     * </p>
     */
    public static class LoggingAbortPolicy implements RejectedExecutionHandler {
        private final Logger log;

        public LoggingAbortPolicy() {
            this(Loggers.get("dtp"));
        }

        public LoggingAbortPolicy(Logger log) {
            this.log = log;
        }

        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
            String name = "unknown";
            if (e instanceof DynamicThreadPoolExecutor) {
                name = ((DynamicThreadPoolExecutor) e).getPoolName();
            }
            log.warn("Task rejected by DTP (abort). pool=" + name
                    + " active=" + e.getActiveCount()
                    + " queueSize=" + e.getQueue().size());
            throw new RejectedExecutionException("Task " + r + " rejected from " + name);
        }
    }
}
