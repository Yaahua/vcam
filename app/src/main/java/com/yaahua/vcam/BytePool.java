package com.yaahua.vcam;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

public class BytePool {
    private static final Map<Integer, Deque<byte[]>> pools = new HashMap<>();
    private static final int MAX_POOL_SIZE = 5;

    public static synchronized byte[] acquire(int size) {
        Deque<byte[]> pool = pools.get(size);
        if (pool != null && !pool.isEmpty()) {
            return pool.poll();
        }
        return new byte[size];
    }

    public static synchronized void release(byte[] buffer) {
        if (buffer == null) return;
        int size = buffer.length;
        Deque<byte[]> pool = pools.get(size);
        if (pool == null) {
            pool = new ArrayDeque<>();
            pools.put(size, pool);
        }
        if (pool.size() < MAX_POOL_SIZE) {
            pool.offer(buffer);
        }
    }

    public static synchronized void clear() {
        pools.clear();
    }
}