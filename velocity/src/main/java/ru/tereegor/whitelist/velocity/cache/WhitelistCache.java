package ru.tereegor.whitelist.velocity.cache;

import ru.tereegor.whitelist.common.storage.SqlStorage;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;

public class WhitelistCache {
    
    private final SqlStorage storage;
    private final long ttlMillis;
    private final int maxSize;
    private final Map<String, CacheEntry<Boolean>> whitelistCache;
    private final ScheduledExecutorService cleaner;
    
    public WhitelistCache(SqlStorage storage, int ttlSeconds, int maxSize) {
        this.storage = storage;
        this.ttlMillis = ttlSeconds * 1000L;
        this.maxSize = maxSize;
        this.whitelistCache = new ConcurrentHashMap<>();
        
        this.cleaner = Executors.newSingleThreadScheduledExecutor();
        cleaner.scheduleAtFixedRate(this::cleanup, ttlSeconds, ttlSeconds, TimeUnit.SECONDS);
    }
    
    public CompletableFuture<Boolean> isWhitelisted(UUID playerUuid, String serverName) {
        String key = createKey(playerUuid, serverName);
        
        CacheEntry<Boolean> cached = whitelistCache.get(key);
        if (cached != null && !cached.isExpired()) {
            return CompletableFuture.completedFuture(cached.value());
        }
        
        return storage.isWhitelisted(playerUuid, serverName)
                .thenApply(result -> {
                    cacheResult(key, result);
                    return result;
                });
    }
    
    private void cacheResult(String key, Boolean result) {
        ensureCapacity();
        whitelistCache.put(key, new CacheEntry<>(result, System.currentTimeMillis() + ttlMillis));
    }
    
    private void ensureCapacity() {
        if (whitelistCache.size() < maxSize) {
            return;
        }
        
        whitelistCache.entrySet().removeIf(e -> e.getValue().isExpired());
        
        if (whitelistCache.size() >= maxSize) {
            whitelistCache.keySet().stream().findFirst().ifPresent(whitelistCache::remove);
        }
    }
    
    public void invalidate(UUID playerUuid, String serverName) {
        whitelistCache.remove(createKey(playerUuid, serverName));
    }
    
    public void invalidatePlayer(UUID playerUuid) {
        String prefix = playerUuid.toString() + ":";
        whitelistCache.keySet().removeIf(key -> key.startsWith(prefix));
    }
    
    public void invalidateServer(String serverName) {
        String suffix = ":" + serverName;
        whitelistCache.keySet().removeIf(key -> key.endsWith(suffix));
    }
    
    public void invalidateAll() {
        whitelistCache.clear();
    }
    
    private void cleanup() {
        whitelistCache.entrySet().removeIf(e -> e.getValue().isExpired());
    }
    
    public void shutdown() {
        cleaner.shutdown();
    }
    
    private String createKey(UUID playerUuid, String serverName) {
        return playerUuid.toString() + ":" + serverName;
    }
    
    private record CacheEntry<T>(T value, long expiresAt) {
        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }
}
