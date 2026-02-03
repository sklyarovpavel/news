package ru.fedresurs.crawler.util;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class InMemoryCache<K, V> {
    private static class Entry<V> {
        final V value;
        final long expireAt;
        Entry(V value, long expireAt) {
            this.value = value;
            this.expireAt = expireAt;
        }
    }

    private final long ttlMs;
    private final Map<K, Entry<V>> store = new ConcurrentHashMap<>();

    public InMemoryCache(long ttlMs) {
        this.ttlMs = ttlMs;
    }

    public void put(K key, V value) {
        long expireAt = Instant.now().toEpochMilli() + ttlMs;
        store.put(key, new Entry<>(value, expireAt));
    }

    public Optional<V> get(K key) {
        Entry<V> entry = store.get(key);
        if (entry == null) return Optional.empty();
        if (entry.expireAt < Instant.now().toEpochMilli()) {
            store.remove(key);
            return Optional.empty();
        }
        return Optional.of(entry.value);
    }
}

