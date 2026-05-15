/*
 * Copyright 2024 itlemon
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package cn.codingguide.dynamo;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 压力测试 - Dynamo vs JDK ThreadPoolExecutor
 */
public class StressTest {

    private static final int WARMUP_TASKS = 1000;
    private static final int TEST_TASKS = 10000;
    private static final int THREAD_COUNT = Runtime.getRuntime().availableProcessors();

    private static String repeat(String str, int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(str);
        }
        return sb.toString();
    }

    public static void main(String[] args) throws Exception {
        System.out.println(repeat("=", 80));
        System.out.println("Dynamo 动态线程池压力测试报告");
        System.out.println(repeat("=", 80));
        System.out.println("测试环境：");
        System.out.println("  JDK 版本: " + System.getProperty("java.version"));
        System.out.println("  CPU 核心数: " + THREAD_COUNT);
        System.out.println("  可用内存: " + (Runtime.getRuntime().maxMemory() / 1024 / 1024) + " MB");
        System.out.println("  操作系统: " + System.getProperty("os.name") + " " + System.getProperty("os.version"));
        System.out.println(repeat("=", 80));
        System.out.println();

        // 测试 1: 基准性能测试
        testBasicPerformance();

        // 测试 2: 高并发提交测试
        testHighConcurrency();

        // 测试 3: 动态参数调整测试
        testDynamicAdjustment();

        // 测试 4: 队列扩缩容测试
        testQueueResize();

        // 测试 5: 拒绝策略测试
        testRejectionPolicy();

        // 测试 6: 内存开销测试
        testMemoryOverhead();

        // 测试 7: 长时间运行稳定性测试（15秒版本）
        testLongRunning();

        System.out.println("\n" + repeat("=", 80));
        System.out.println("测试完成！");
        System.out.println(repeat("=", 80));
    }

    /**
     * 测试 1: 基准性能测试
     */
    private static void testBasicPerformance() throws Exception {
        System.out.println("【测试 1】基准性能测试 - 执行 " + TEST_TASKS + " 个简单任务");
        System.out.println(repeat("-", 80));

        // JDK 线程池（增大队列容量避免拒绝）
        ThreadPoolExecutor jdkPool = new ThreadPoolExecutor(
                THREAD_COUNT, THREAD_COUNT * 2, 60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(50000));

        long jdkTime = runBenchmark(jdkPool, TEST_TASKS, true);
        jdkPool.shutdown();
        jdkPool.awaitTermination(10, TimeUnit.SECONDS);

        // Dynamo 线程池（增大队列容量避免拒绝）
        AtomicInteger core = new AtomicInteger(THREAD_COUNT);
        AtomicInteger max = new AtomicInteger(THREAD_COUNT * 2);
        DynamicThreadPoolExecutor dynamoPool = DynamicThreadPoolExecutor.builder()
                .threadPoolName("benchmark")
                .corePoolSize(core::get)
                .maximumPoolSize(max::get)
                .queueCapacity(50000)
                .build();

        long dynamoTime = runBenchmark(dynamoPool, TEST_TASKS, false);
        dynamoPool.shutdown();
        dynamoPool.awaitTermination(10, TimeUnit.SECONDS);

        System.out.println("\n结果：");
        System.out.println("  JDK ThreadPoolExecutor:  " + jdkTime + " ms");
        System.out.println("  Dynamo 动态线程池:       " + dynamoTime + " ms");
        System.out.println("  性能差异: " + String.format("%.2f%%", (dynamoTime - jdkTime) * 100.0 / jdkTime));
        System.out.println();
    }

    /**
     * 测试 2: 高并发提交测试
     */
    private static void testHighConcurrency() throws Exception {
        System.out.println("【测试 2】高并发提交测试 - 多线程同时提交任务");
        System.out.println(repeat("-", 80));

        int concurrentThreads = 20;
        int tasksPerThread = 500;

        // JDK 线程池（增大队列容量避免拒绝）
        ThreadPoolExecutor jdkPool = new ThreadPoolExecutor(
                THREAD_COUNT, THREAD_COUNT * 2, 60, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(50000));

        long jdkTime = runConcurrentSubmit(jdkPool, concurrentThreads, tasksPerThread, true);
        jdkPool.shutdown();
        jdkPool.awaitTermination(10, TimeUnit.SECONDS);

        // Dynamo 线程池（增大队列容量避免拒绝）
        AtomicInteger core = new AtomicInteger(THREAD_COUNT);
        AtomicInteger max = new AtomicInteger(THREAD_COUNT * 2);
        DynamicThreadPoolExecutor dynamoPool = DynamicThreadPoolExecutor.builder()
                .threadPoolName("concurrent")
                .corePoolSize(core::get)
                .maximumPoolSize(max::get)
                .queueCapacity(50000)
                .build();

        long dynamoTime = runConcurrentSubmit(dynamoPool, concurrentThreads, tasksPerThread, false);
        dynamoPool.shutdown();
        dynamoPool.awaitTermination(10, TimeUnit.SECONDS);

        System.out.println("\n结果：");
        System.out.println("  JDK ThreadPoolExecutor:  " + jdkTime + " ms");
        System.out.println("  Dynamo 动态线程池:       " + dynamoTime + " ms");
        System.out.println("  性能差异: " + String.format("%.2f%%", (dynamoTime - jdkTime) * 100.0 / jdkTime));
        System.out.println();
    }

    /**
     * 测试 3: 动态参数调整测试
     */
    private static void testDynamicAdjustment() throws Exception {
        System.out.println("【测试 3】动态参数调整测试 - 运行时调整参数");
        System.out.println(repeat("-", 80));

        AtomicInteger core = new AtomicInteger(4);
        AtomicInteger max = new AtomicInteger(16);
        AtomicInteger queueCap = new AtomicInteger(1024);

        DynamicThreadPoolExecutor pool = DynamicThreadPoolExecutor.builder()
                .threadPoolName("dynamic")
                .corePoolSize(core::get)
                .maximumPoolSize(max::get)
                .queueCapacity(queueCap::get)
                .refreshInterval(Duration.ofMillis(100))
                .build();

        CountDownLatch latch = new CountDownLatch(10000);
        AtomicLong totalTime = new AtomicLong(0);

        // 提交任务
        long start = System.currentTimeMillis();
        for (int i = 0; i < 10000; i++) {
            final int taskId = i;
            pool.execute(() -> {
                long taskStart = System.nanoTime();
                try {
                    Thread.sleep(1);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                totalTime.addAndGet(System.nanoTime() - taskStart);
                latch.countDown();
            });

            // 动态调整参数
            if (i == 3000) {
                core.set(8);
                max.set(32);
                System.out.println("  [3000 tasks] 调整参数: core=8, max=32");
            }
            if (i == 7000) {
                core.set(16);
                max.set(64);
                queueCap.set(2048);
                System.out.println("  [7000 tasks] 调整参数: core=16, max=64, queue=2048");
            }
        }

        latch.await(60, TimeUnit.SECONDS);
        long elapsed = System.currentTimeMillis() - start;

        ThreadPoolMetrics metrics = pool.getMetrics();
        System.out.println("\n结果：");
        System.out.println("  总耗时: " + elapsed + " ms");
        System.out.println("  平均任务时间: " + String.format("%.2f", totalTime.get() / 10000.0 / 1_000_000) + " ms");
        System.out.println("  最终参数: core=" + metrics.getCorePoolSize() +
                ", max=" + metrics.getMaximumPoolSize() +
                ", queue=" + metrics.getQueueCapacity());
        System.out.println("  拒绝任务数: " + pool.getRejectedCount());

        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);
        System.out.println();
    }

    /**
     * 测试 4: 队列扩缩容测试
     */
    private static void testQueueResize() throws Exception {
        System.out.println("【测试 4】队列扩缩容测试");
        System.out.println(repeat("-", 80));

        AtomicInteger queueCap = new AtomicInteger(512);
        DynamicThreadPoolExecutor pool = DynamicThreadPoolExecutor.builder()
                .threadPoolName("queue-resize")
                .corePoolSize(() -> 2)
                .maximumPoolSize(() -> 4)
                .queueCapacity(queueCap::get)
                .refreshInterval(Duration.ofMillis(100))
                .build();

        // 快速提交大量任务，填满队列
        AtomicLong rejected = new AtomicLong(0);
        for (int round = 1; round <= 3; round++) {
            System.out.println("\n  第 " + round + " 轮测试:");

            for (int i = 0; i < 1000; i++) {
                try {
                    pool.execute(() -> {
                        try {
                            Thread.sleep(10);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    });
                } catch (RejectedExecutionException e) {
                    rejected.incrementAndGet();
                }
            }

            Thread.sleep(200);
            ThreadPoolMetrics m = pool.getMetrics();
            System.out.println("    队列容量: " + m.getQueueCapacity() +
                    ", 队列大小: " + m.getQueueSize() +
                    ", 拒绝数: " + pool.getRejectedCount());

            // 调整队列大小
            if (round == 1) {
                queueCap.set(2048);
                System.out.println("    -> 扩容队列到 2048");
            } else if (round == 2) {
                queueCap.set(256);
                System.out.println("    -> 缩容队列到 256");
            }

            Thread.sleep(200);
        }

        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.SECONDS);
        System.out.println();
    }

    /**
     * 测试 5: 拒绝策略测试
     */
    private static void testRejectionPolicy() throws Exception {
        System.out.println("【测试 5】拒绝策略测试");
        System.out.println(repeat("-", 80));

        DynamicThreadPoolExecutor pool = DynamicThreadPoolExecutor.builder()
                .threadPoolName("rejection")
                .corePoolSize(() -> 2)
                .maximumPoolSize(() -> 2)
                .queueCapacity(() -> 10)
                .rejectedHandler(new DynamicThreadPoolExecutor.LoggingDiscardPolicy())
                .build();

        int total = 1000;
        int submitted = 0;
        for (int i = 0; i < total; i++) {
            try {
                pool.execute(() -> {
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
                submitted++;
            } catch (RejectedExecutionException e) {
                // 忽略
            }
        }

        Thread.sleep(500);
        ThreadPoolMetrics m = pool.getMetrics();

        System.out.println("\n结果：");
        System.out.println("  尝试提交任务: " + total);
        System.out.println("  成功提交任务: " + submitted);
        System.out.println("  被拒绝任务: " + pool.getRejectedCount());
        System.out.println("  当前队列大小: " + m.getQueueSize());

        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.SECONDS);
        System.out.println();
    }

    /**
     * 测试 6: 内存开销测试
     */
    private static void testMemoryOverhead() throws Exception {
        System.out.println("【测试 6】内存开销测试");
        System.out.println(repeat("-", 80));

        Runtime runtime = Runtime.getRuntime();
        System.gc();
        Thread.sleep(1000);

        long before = runtime.totalMemory() - runtime.freeMemory();

        // 创建 100 个线程池
        List<DynamicThreadPoolExecutor> pools = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            pools.add(DynamicThreadPoolExecutor.builder()
                    .threadPoolName("pool-" + i)
                    .corePoolSize(() -> 4)
                    .maximumPoolSize(() -> 8)
                    .queueCapacity(() -> 1024)
                    .build());
        }

        Thread.sleep(1000);
        System.gc();
        Thread.sleep(1000);

        long after = runtime.totalMemory() - runtime.freeMemory();
        long overhead = (after - before) / 100;

        System.out.println("\n结果：");
        System.out.println("  创建线程池数: 100");
        System.out.println("  总内存增量: " + String.format("%.2f", (after - before) / 1024.0 / 1024.0) + " MB");
        System.out.println("  单个线程池开销: " + String.format("%.2f", overhead / 1024.0) + " KB");

        for (DynamicThreadPoolExecutor pool : pools) {
            pool.shutdown();
        }
        System.out.println();
    }

    /**
     * 测试 7: 长时间运行稳定性测试
     */
    private static void testLongRunning() throws Exception {
        System.out.println("【测试 7】长时间运行稳定性测试（15秒）");
        System.out.println(repeat("-", 80));

        AtomicInteger core = new AtomicInteger(THREAD_COUNT);
        AtomicInteger max = new AtomicInteger(THREAD_COUNT * 2);

        DynamicThreadPoolExecutor pool = DynamicThreadPoolExecutor.builder()
                .threadPoolName("long-running")
                .corePoolSize(core::get)
                .maximumPoolSize(max::get)
                .queueCapacity(() -> 2048)
                .refreshInterval(Duration.ofSeconds(1))
                .build();

        AtomicLong taskCount = new AtomicLong(0);
        AtomicLong errorCount = new AtomicLong(0);

        // 持续提交任务 15 秒
        long endTime = System.currentTimeMillis() + 15_000;
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

        // 定期调整参数
        scheduler.scheduleAtFixedRate(() -> {
            int newCore = 4 + (int) (Math.random() * 12);
            int newMax = newCore * 2;
            core.set(newCore);
            max.set(newMax);
            System.out.println("  [" + (System.currentTimeMillis() % 100000) + "] 调整参数: core=" + newCore + ", max=" + newMax);
        }, 5, 5, TimeUnit.SECONDS);

        // 提交任务
        while (System.currentTimeMillis() < endTime) {
            try {
                pool.execute(() -> {
                    try {
                        Thread.sleep((long) (Math.random() * 10));
                        taskCount.incrementAndGet();
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                    }
                });
            } catch (RejectedExecutionException e) {
                // 继续
            }
            if (taskCount.get() % 10000 == 0) {
                Thread.sleep(10);
            }
        }

        scheduler.shutdown();
        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);

        ThreadPoolMetrics m = pool.getMetrics();

        System.out.println("\n结果：");
        System.out.println("  执行任务总数: " + taskCount.get());
        System.out.println("  完成任务数: " + m.getCompletedTaskCount());
        System.out.println("  错误任务数: " + errorCount.get());
        System.out.println("  拒绝任务数: " + pool.getRejectedCount());
        System.out.println("  稳定性: " + (errorCount.get() == 0 ? "✓ 通过" : "✗ 失败"));
        System.out.println();
    }

    // ========== 辅助方法 ==========

    private static long runBenchmark(ThreadPoolExecutor pool, int tasks, boolean warmup) throws Exception {
        if (warmup) {
            // 预热
            CountDownLatch warmupLatch = new CountDownLatch(WARMUP_TASKS);
            for (int i = 0; i < WARMUP_TASKS; i++) {
                pool.execute(() -> {
                    doWork();
                    warmupLatch.countDown();
                });
            }
            warmupLatch.await();
        }

        // 正式测试
        CountDownLatch latch = new CountDownLatch(tasks);
        long start = System.currentTimeMillis();

        for (int i = 0; i < tasks; i++) {
            pool.execute(() -> {
                doWork();
                latch.countDown();
            });
        }

        latch.await();
        return System.currentTimeMillis() - start;
    }

    private static long runConcurrentSubmit(ThreadPoolExecutor pool, int threads, int tasksPerThread, boolean warmup) throws Exception {
        if (warmup) {
            CountDownLatch warmupLatch = new CountDownLatch(WARMUP_TASKS);
            for (int i = 0; i < WARMUP_TASKS; i++) {
                pool.execute(() -> {
                    doWork();
                    warmupLatch.countDown();
                });
            }
            warmupLatch.await();
        }

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch taskLatch = new CountDownLatch(threads * tasksPerThread);
        CountDownLatch threadLatch = new CountDownLatch(threads);

        // 创建提交线程
        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < tasksPerThread; j++) {
                        pool.execute(() -> {
                            doWork();
                            taskLatch.countDown();
                        });
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    threadLatch.countDown();
                }
            }).start();
        }

        long start = System.currentTimeMillis();
        startLatch.countDown();
        threadLatch.await();
        taskLatch.await();
        return System.currentTimeMillis() - start;
    }

    private static void doWork() {
        // 模拟工作负载
        double result = 0;
        for (int i = 0; i < 100; i++) {
            result += Math.sqrt(i);
        }
    }
}
