package cn.codingguide.dynamo;

import java.util.AbstractQueue;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A variant of {@link java.util.concurrent.LinkedBlockingQueue} with runtime-adjustable capacity.
 * <p>
 * Unlike JDK's {@code LinkedBlockingQueue} where capacity is {@code final}, this implementation
 * allows changing the capacity at runtime via {@link #setCapacity(int)}. This is crucial for
 * dynamic thread pool's queue size adjustment without reflection or JVM flags.
 * <p>
 * <b>Key design:</b>
 * <ul>
 *   <li>{@code capacity} is {@code volatile} instead of {@code final}</li>
 *   <li>{@code setCapacity} holds {@code putLock} and signals waiting producers on expansion</li>
 *   <li>Shrinking capacity does NOT discard existing elements, only affects future {@code put} operations</li>
 *   <li>Dual-lock design (putLock / takeLock) for high concurrency, same as JDK LinkedBlockingQueue</li>
 * </ul>
 * <p>
 * <b>Thread safety:</b> This class is fully thread-safe for concurrent put/take/resize operations.
 * <p>
 * <b>No reflection:</b> This implementation does NOT require {@code --add-opens} JVM flags
 * (unlike approaches that use reflection to modify JDK's final capacity field).
 *
 * @param <E> the type of elements held in this queue
 * @author itlemon
 * @since 1.0.0
 */
public class ResizableCapacityLinkedBlockingQueue<E> extends AbstractQueue<E>
        implements BlockingQueue<E> {

    /**
     * Linked list node.
     */
    private static class Node<E> {
        E item;
        Node<E> next;

        Node(E x) {
            this.item = x;
        }
    }

    /**
     * Current queue capacity (volatile for runtime adjustability).
     */
    private volatile int capacity;

    /**
     * Current number of elements.
     */
    private final AtomicInteger count = new AtomicInteger();

    /**
     * Head of linked list (dummy node, head.next is the first real element).
     */
    private transient Node<E> head;

    /**
     * Tail of linked list.
     */
    private transient Node<E> last;

    /**
     * Lock for put operations.
     */
    private final ReentrantLock putLock = new ReentrantLock();

    /**
     * Wait queue for waiting puts.
     */
    private final Condition notFull = putLock.newCondition();

    /**
     * Lock for take operations.
     */
    private final ReentrantLock takeLock = new ReentrantLock();

    /**
     * Wait queue for waiting takes.
     */
    private final Condition notEmpty = takeLock.newCondition();

    /**
     * Creates a queue with the given initial capacity.
     *
     * @param capacity the initial capacity
     * @throws IllegalArgumentException if capacity is not positive
     */
    public ResizableCapacityLinkedBlockingQueue(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException();
        }
        this.capacity = capacity;
        this.last = head = new Node<>(null);
    }

    /**
     * Adjust the queue capacity at runtime.
     * <p>
     * <b>Expansion:</b> Wakes up all waiting producers (via {@code notFull.signalAll()})
     * so they can retry based on the new capacity.
     * <p>
     * <b>Shrinking:</b> Does NOT discard existing elements in the queue.
     * Only affects future {@code put} operations. If current size &gt; new capacity,
     * no new elements can be added until size drops below new capacity.
     *
     * @param newCapacity the new capacity
     * @throws IllegalArgumentException if new capacity is not positive
     */
    public void setCapacity(int newCapacity) {
        if (newCapacity <= 0) {
            throw new IllegalArgumentException();
        }
        putLock.lock();
        try {
            this.capacity = newCapacity;
            // Wake up all waiting producers on expansion
            if (count.get() < newCapacity) {
                notFull.signalAll();
            }
        } finally {
            putLock.unlock();
        }
    }

    /**
     * Get current queue capacity.
     *
     * @return current capacity
     */
    public int getCapacity() {
        return capacity;
    }

    private void signalNotEmpty() {
        takeLock.lock();
        try {
            notEmpty.signal();
        } finally {
            takeLock.unlock();
        }
    }

    private void signalNotFull() {
        putLock.lock();
        try {
            notFull.signal();
        } finally {
            putLock.unlock();
        }
    }

    private void enqueue(Node<E> node) {
        last = last.next = node;
    }

    private E dequeue() {
        Node<E> h = head;
        Node<E> first = h.next;
        h.next = h; // help GC
        head = first;
        E x = first.item;
        first.item = null;
        return x;
    }

    private void fullyLock() {
        putLock.lock();
        takeLock.lock();
    }

    private void fullyUnlock() {
        takeLock.unlock();
        putLock.unlock();
    }

    @Override
    public void put(E e) throws InterruptedException {
        if (e == null) {
            throw new NullPointerException();
        }
        int c;
        putLock.lockInterruptibly();
        try {
            // Key: re-read volatile capacity on each loop iteration
            while (count.get() >= capacity) {
                notFull.await();
            }
            enqueue(new Node<E>(e));
            c = count.getAndIncrement();
            if (c + 1 < capacity) {
                notFull.signal();
            }
        } finally {
            putLock.unlock();
        }
        if (c == 0) {
            signalNotEmpty();
        }
    }

    @Override
    public boolean offer(E e) {
        if (e == null) {
            throw new NullPointerException();
        }
        if (count.get() >= capacity) {
            return false;
        }
        int c = -1;
        putLock.lock();
        try {
            if (count.get() < capacity) {
                enqueue(new Node<E>(e));
                c = count.getAndIncrement();
                if (c + 1 < capacity) {
                    notFull.signal();
                }
            }
        } finally {
            putLock.unlock();
        }
        if (c == 0) {
            signalNotEmpty();
        }
        return c >= 0;
    }

    @Override
    public boolean offer(E e, long timeout, TimeUnit unit) throws InterruptedException {
        if (e == null) {
            throw new NullPointerException();
        }
        long nanos = unit.toNanos(timeout);
        int c;
        putLock.lockInterruptibly();
        try {
            while (count.get() >= capacity) {
                if (nanos <= 0L) {
                    return false;
                }
                nanos = notFull.awaitNanos(nanos);
            }
            enqueue(new Node<E>(e));
            c = count.getAndIncrement();
            if (c + 1 < capacity) {
                notFull.signal();
            }
        } finally {
            putLock.unlock();
        }
        if (c == 0) {
            signalNotEmpty();
        }
        return true;
    }

    @Override
    public E take() throws InterruptedException {
        E x;
        int c;
        takeLock.lockInterruptibly();
        try {
            while (count.get() == 0) {
                notEmpty.await();
            }
            x = dequeue();
            c = count.getAndDecrement();
            if (c > 1) {
                notEmpty.signal();
            }
        } finally {
            takeLock.unlock();
        }
        if (c == capacity) {
            signalNotFull();
        }
        return x;
    }

    @Override
    public E poll() {
        if (count.get() == 0) {
            return null;
        }
        E x = null;
        int c = -1;
        takeLock.lock();
        try {
            if (count.get() > 0) {
                x = dequeue();
                c = count.getAndDecrement();
                if (c > 1) {
                    notEmpty.signal();
                }
            }
        } finally {
            takeLock.unlock();
        }
        if (c == capacity) {
            signalNotFull();
        }
        return x;
    }

    @Override
    public E poll(long timeout, TimeUnit unit) throws InterruptedException {
        E x;
        int c;
        long nanos = unit.toNanos(timeout);
        takeLock.lockInterruptibly();
        try {
            while (count.get() == 0) {
                if (nanos <= 0L) {
                    return null;
                }
                nanos = notEmpty.awaitNanos(nanos);
            }
            x = dequeue();
            c = count.getAndDecrement();
            if (c > 1) {
                notEmpty.signal();
            }
        } finally {
            takeLock.unlock();
        }
        if (c == capacity) {
            signalNotFull();
        }
        return x;
    }

    @Override
    public E peek() {
        if (count.get() == 0) {
            return null;
        }
        takeLock.lock();
        try {
            Node<E> first = head.next;
            return first == null ? null : first.item;
        } finally {
            takeLock.unlock();
        }
    }

    @Override
    public int size() {
        return count.get();
    }

    @Override
    public int remainingCapacity() {
        return Math.max(0, capacity - count.get());
    }

    @Override
    public int drainTo(Collection<? super E> c) {
        return drainTo(c, Integer.MAX_VALUE);
    }

    @Override
    public int drainTo(Collection<? super E> c, int maxElements) {
        if (c == null) {
            throw new NullPointerException();
        }
        if (c == this) {
            throw new IllegalArgumentException();
        }
        if (maxElements <= 0) {
            return 0;
        }
        boolean signalNotFull = false;
        takeLock.lock();
        try {
            int n = Math.min(maxElements, count.get());
            Node<E> h = head;
            int i = 0;
            try {
                while (i < n) {
                    Node<E> p = h.next;
                    c.add(p.item);
                    p.item = null;
                    h.next = h; // help GC
                    h = p;
                    ++i;
                }
                return n;
            } finally {
                if (i > 0) {
                    head = h;
                    signalNotFull = (count.getAndAdd(-i) == capacity);
                }
            }
        } finally {
            takeLock.unlock();
            if (signalNotFull) {
                signalNotFull();
            }
        }
    }

    @Override
    public Iterator<E> iterator() {
        return new Itr();
    }

    /**
     * Weakly-consistent iterator.
     */
    private class Itr implements Iterator<E> {
        private Node<E> current;
        private Node<E> lastRet;
        private E currentElement;

        Itr() {
            fullyLock();
            try {
                current = head.next;
                if (current != null) {
                    currentElement = current.item;
                }
            } finally {
                fullyUnlock();
            }
        }

        @Override
        public boolean hasNext() {
            return current != null;
        }

        @Override
        public E next() {
            fullyLock();
            try {
                if (current == null) {
                    throw new NoSuchElementException();
                }
                E x = currentElement;
                lastRet = current;
                Node<E> p = current.next;
                // Skip nodes that have been consumed (item == null)
                while (p != null && p.item == null) {
                    p = p.next;
                }
                current = p;
                currentElement = (p == null) ? null : p.item;
                return x;
            } finally {
                fullyUnlock();
            }
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
}
