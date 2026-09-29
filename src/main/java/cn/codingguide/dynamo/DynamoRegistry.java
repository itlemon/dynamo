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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * A lightweight, read-only registry of live {@link DynamicThreadPoolExecutor} instances in the
 * current JVM.
 * <p>
 * Every dynamic thread pool registers itself here on creation. The registry holds
 * <b>weak references</b> to the pools, so it never prevents an unused pool from being garbage
 * collected and never causes a memory leak. This makes it safe to leave enabled by default.
 * <p>
 * Typical use is a monitoring endpoint that iterates over all pools and collects their metrics:
 * <pre>{@code
 * for (DynamicThreadPoolExecutor pool : DynamoRegistry.pools()) {
 *     ThreadPoolMetrics m = pool.getMetrics();
 *     // export m to Prometheus / dashboard ...
 * }
 * }</pre>
 * The registry is intentionally minimal: it only lets you discover and observe pools. It does not
 * manage their lifecycle or configuration.
 *
 * @author itlemon
 * @since 1.0.0
 */
public final class DynamoRegistry {

    /**
     * Weak set of live pools. Keys are the pools; the value is an unused marker. Access is
     * guarded by {@code POOLS} itself since {@link WeakHashMap} is not thread-safe.
     */
    private static final Map<DynamicThreadPoolExecutor, Boolean> POOLS = new WeakHashMap<>();

    private DynamoRegistry() {
    }

    static void register(DynamicThreadPoolExecutor pool) {
        synchronized (POOLS) {
            POOLS.put(pool, Boolean.TRUE);
        }
    }

    static void unregister(DynamicThreadPoolExecutor pool) {
        synchronized (POOLS) {
            POOLS.remove(pool);
        }
    }

    /**
     * Return a snapshot list of all live dynamic thread pools.
     * <p>
     * The returned list is a copy; iterating it will not throw {@code ConcurrentModificationException}
     * even if pools are created or collected concurrently.
     *
     * @return an unmodifiable snapshot of live pools (may be empty, never {@code null})
     */
    public static List<DynamicThreadPoolExecutor> pools() {
        synchronized (POOLS) {
            List<DynamicThreadPoolExecutor> snapshot = new ArrayList<>(POOLS.size());
            for (Iterator<DynamicThreadPoolExecutor> it = POOLS.keySet().iterator(); it.hasNext(); ) {
                DynamicThreadPoolExecutor pool = it.next();
                if (pool != null) {
                    snapshot.add(pool);
                }
            }
            return Collections.unmodifiableList(snapshot);
        }
    }

    /**
     * Collect a metrics snapshot from every live pool.
     *
     * @return an unmodifiable list of metrics, one per live pool
     */
    public static List<ThreadPoolMetrics> collectMetrics() {
        List<DynamicThreadPoolExecutor> pools = pools();
        List<ThreadPoolMetrics> metrics = new ArrayList<>(pools.size());
        for (DynamicThreadPoolExecutor pool : pools) {
            metrics.add(pool.getMetrics());
        }
        return Collections.unmodifiableList(metrics);
    }

    /**
     * The number of live pools currently registered.
     *
     * @return live pool count
     */
    public static int size() {
        synchronized (POOLS) {
            return POOLS.size();
        }
    }
}
