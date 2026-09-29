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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Smoke test for near-real-time refresh ({@code refreshNow()}) and the slow-refresh warning (L1).
 */
public class RefreshNowTest {

    public static void main(String[] args) throws Exception {
        System.out.println("=== Dynamo refreshNow / slow-refresh Test ===\n");

        AtomicInteger core = new AtomicInteger(2);
        AtomicInteger max = new AtomicInteger(4);

        // Long poll interval so we can prove refreshNow() does NOT wait for the poll.
        DynamicThreadPoolExecutor pool = DynamicThreadPoolExecutor.builder()
                .threadPoolName("refresh-now-pool")
                .corePoolSize(core::get)
                .maximumPoolSize(max::get)
                .refreshInterval(Duration.ofMinutes(10))
                .build();

        System.out.println("Test 1: refreshNow() applies changes immediately");
        System.out.println("before: core=" + pool.getCorePoolSize() + " max=" + pool.getMaximumPoolSize());
        core.set(6);
        max.set(12);
        long t0 = System.currentTimeMillis();
        pool.refreshNow();
        long elapsed = System.currentTimeMillis() - t0;
        System.out.println("after : core=" + pool.getCorePoolSize() + " max=" + pool.getMaximumPoolSize()
                + " (applied in " + elapsed + "ms)");
        expect(pool.getCorePoolSize() == 6, "core should be applied immediately");
        expect(pool.getMaximumPoolSize() == 12, "max should be applied immediately");
        expect(elapsed < 1000, "refreshNow should not wait for the poll interval");

        System.out.println("\nTest 2: refreshNow() is idempotent when nothing changed");
        pool.refreshNow();
        expect(pool.getCorePoolSize() == 6, "core stays the same");

        pool.shutdown();
        pool.awaitTermination(5, TimeUnit.SECONDS);

        System.out.println("\nTest 3: slow Supplier triggers [DTP-SLOW-REFRESH] warning");
        DynamicThreadPoolExecutor slowPool = DynamicThreadPoolExecutor.builder()
                .threadPoolName("slow-pool")
                .corePoolSize(() -> {
                    sleep(200);   // Simulate a badly written, blocking Supplier
                    return 2;
                })
                .maximumPoolSize(() -> 4)
                .refreshInterval(Duration.ofMinutes(10))
                .slowRefreshThreshold(100)   // Warn if a refresh takes > 100ms
                .build();

        System.out.println("Triggering a slow refresh (watch for [DTP-SLOW-REFRESH] above/below)...");
        slowPool.refreshNow();
        slowPool.shutdown();
        slowPool.awaitTermination(5, TimeUnit.SECONDS);

        System.out.println("\n=== All refreshNow tests passed! ===");
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void expect(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
