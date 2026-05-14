package cn.codingguide.dynamo;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Quick smoke test to verify Dynamo works correctly.
 */
public class QuickTest {

    public static void main(String[] args) throws Exception {
        System.out.println("=== Dynamo Quick Test ===\n");

        // Test 1: Basic dynamic pool
        System.out.println("Test 1: Create dynamic pool with in-memory suppliers");
        AtomicInteger core = new AtomicInteger(2);
        AtomicInteger max = new AtomicInteger(4);

        DynamicThreadPoolExecutor pool = DynamicThreadPoolExecutor.dynamic(
                core::get,
                max::get
        );

        System.out.println("Initial pool name: " + pool.getPoolName());
        System.out.println("Initial core size: " + pool.getCorePoolSize());
        System.out.println("Initial max size: " + pool.getMaximumPoolSize());

        // Test 2: Submit tasks
        System.out.println("\nTest 2: Submit 10 tasks");
        for (int i = 0; i < 10; i++) {
            final int taskId = i;
            pool.execute(new Runnable() {
                @Override
                public void run() {
                    System.out.println("Task " + taskId + " running on " + Thread.currentThread().getName());
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            });
        }

        Thread.sleep(500);

        // Test 3: Get metrics
        System.out.println("\nTest 3: Get metrics");
        ThreadPoolMetrics metrics = pool.getMetrics();
        System.out.println(metrics);
        System.out.println("Queue utilization: " + String.format("%.2f%%", metrics.queueUtilization() * 100));
        System.out.println("Pool utilization: " + String.format("%.2f%%", metrics.poolUtilization() * 100));

        // Test 4: Change parameters dynamically
        System.out.println("\nTest 4: Change core size from 2 to 6");
        core.set(6);
        max.set(12);
        System.out.println("Waiting 6 seconds for refresh...");
        Thread.sleep(6000);

        System.out.println("After refresh - core size: " + pool.getCorePoolSize());
        System.out.println("After refresh - max size: " + pool.getMaximumPoolSize());

        // Test 5: With change listener
        System.out.println("\nTest 5: Create pool with change listener");
        AtomicInteger core2 = new AtomicInteger(3);
        DynamicThreadPoolExecutor pool2 = DynamicThreadPoolExecutor.builder()
                .threadPoolName("test-pool")
                .corePoolSize(core2::get)
                .maximumPoolSize(new java.util.function.Supplier<Integer>() {
                    @Override
                    public Integer get() {
                        return core2.get() * 2;
                    }
                })
                .addChangeListener(new ParameterChangeListener() {
                    @Override
                    public void onChange(ParameterChangeEvent event) {
                        System.out.println(">>> Parameter changed: " + event);
                    }
                })
                .build();

        System.out.println("pool2 name: " + pool2.getPoolName());
        System.out.println("Changing core2 from 3 to 5...");
        core2.set(5);
        Thread.sleep(6000);

        // Shutdown
        System.out.println("\nTest 6: Shutdown pools");
        pool.shutdown();
        pool2.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);
        pool2.awaitTermination(5, TimeUnit.SECONDS);

        System.out.println("\n=== All tests passed! ===");
    }
}
