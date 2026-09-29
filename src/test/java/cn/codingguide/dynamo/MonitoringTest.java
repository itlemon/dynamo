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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Smoke test for the task-level monitoring, historical peaks, load level, built-in change logging,
 * and read-only registry features.
 */
public class MonitoringTest {

    public static void main(String[] args) throws Exception {
        System.out.println("=== Dynamo Monitoring Test ===\n");

        AtomicInteger runTimeouts = new AtomicInteger();
        AtomicInteger queueTimeouts = new AtomicInteger();

        // A small pool with tight timeouts so both kinds fire.
        DynamicThreadPoolExecutor pool = DynamicThreadPoolExecutor.builder()
                .threadPoolName("monitor-pool")
                .corePoolSize(() -> 1)
                .maximumPoolSize(() -> 1)
                .queueCapacity(100)
                .runTimeout(200)
                .queueTimeout(150)
                .logChanges()
                .addTaskTimeoutListener(event -> {
                    if (event.getType() == TaskTimeoutType.RUN_TIMEOUT) {
                        runTimeouts.incrementAndGet();
                    } else {
                        queueTimeouts.incrementAndGet();
                    }
                    System.out.println(">>> timeout: " + event);
                })
                .build();

        System.out.println("Test 1: registry sees the pool");
        expect(DynamoRegistry.pools().contains(pool), "registry should contain pool");
        System.out.println("registry size = " + DynamoRegistry.size());

        System.out.println("\nTest 2: submit slow tasks to trigger run + queue timeouts");
        CountDownLatch latch = new CountDownLatch(4);
        for (int i = 0; i < 4; i++) {
            final int id = i;
            pool.execute(() -> {
                try {
                    // Each task runs 300ms (> runTimeout 200ms); with 1 thread, later tasks
                    // wait in the queue > queueTimeout 150ms.
                    Thread.sleep(300);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await(10, TimeUnit.SECONDS);
        Thread.sleep(200);

        System.out.println("run timeouts fired    = " + runTimeouts.get());
        System.out.println("queue timeouts fired  = " + queueTimeouts.get());
        expect(runTimeouts.get() >= 1, "expected at least one run timeout");
        expect(queueTimeouts.get() >= 1, "expected at least one queue timeout");

        System.out.println("\nTest 3: historical peaks + load level");
        ThreadPoolMetrics m = pool.getMetrics();
        System.out.println(m);
        System.out.println("largestPoolSize  = " + m.getLargestPoolSize());
        System.out.println("largestQueueSize = " + m.getLargestQueueSize());
        System.out.println("maxTaskTimeMillis= " + m.getMaxTaskTimeMillis());
        System.out.println("loadLevel        = " + m.loadLevel());
        expect(m.getLargestQueueSize() >= 1, "expected a recorded queue peak");
        expect(m.getMaxTaskTimeMillis() >= 200, "expected a recorded task time peak");

        System.out.println("\nTest 4: registry metrics collection");
        System.out.println("collected " + DynamoRegistry.collectMetrics().size() + " metric snapshot(s)");

        System.out.println("\nTest 5: shutdown unregisters from registry");
        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);
        expect(!DynamoRegistry.pools().contains(pool), "registry should not contain a shut-down pool");

        System.out.println("\n=== All monitoring tests passed! ===");
    }

    private static void expect(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
