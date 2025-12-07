package ru.tereegor.whitelist.velocity.cache;

import ru.tereegor.whitelist.common.model.ServerInfo;
import ru.tereegor.whitelist.common.storage.SqlStorage;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;

public class WhitelistCache {
    
    private final SqlStorage storage;
    private final long ttlMillis;
    private final int maxSize;
    
    private final Map<String, CacheEntry<Boolean>> whitelistCache;
    
    private final Map<String, CacheEntry<ServerInfo>> serverCache;
    
    private final ScheduledExecutorService cleaner;
    
    public WhitelistCache(SqlStorage storage, int ttlSeconds, int maxSize) {
        this.storage = storage;
        this.ttlMillis = ttlSeconds * 1000L;
        this.maxSize = maxSize;
        this.whitelistCache = new ConcurrentHashMap<>();
        this.serverCache = new ConcurrentHashMap<>();
        
        this.cleaner = Executors.newSingleThreadScheduledExecutor();
        cleaner.scheduleAtFixedRate(this::cleanup, ttlSeconds, ttlSeconds, TimeUnit.SECONDS);
    }
    
    public CompletableFuture<Boolean> isWhitelisted(UUID playerUuid, String serverName) {
        String key = playerUuid.toString() + ":" + serverName;
        
        CacheEntry<Boolean> cached = whitelistCache.get(key);
        if (cached != null && !cached.isExpired()) {
            return CompletableFuture.completedFuture(cached.value());
        }
        
        return storage.isWhitelisted(playerUuid, serverName).thenApply(result -> {
            if (whitelistCache.size() < maxSize) {
                whitelistCache.put(key, new CacheEntry<>(result, System.currentTimeMillis() + ttlMillis));
            }
            return result;
        });
    }
    
    public CompletableFuture<Optional<ServerInfo>> getServer(String serverName) {
        CacheEntry<ServerInfo> cached = serverCache.get(serverName);
        if (cached != null && !cached.isExpired()) {
            return CompletableFuture.completedFuture(Optional.of(cached.value()));
        }
        
        return storage.getServer(serverName).thenApply(result -> {
            result.ifPresent(server -> {
                if (serverCache.size() < maxSize) {
                    serverCache.put(serverName, new CacheEntry<>(server, System.currentTimeMillis() + ttlMillis));
                }
            });
            return result;
        });
    }
    
    public void invalidate(UUID playerUuid, String serverName) {
        whitelistCache.remove(playerUuid.toString() + ":" + serverName);
    }
    
    public void invalidatePlayer(UUID playerUuid) {
        String prefix = playerUuid.toString() + ":";
        whitelistCache.keySet().removeIf(key -> key.startsWith(prefix));
    }
    
    public void invalidateServer(String serverName) {
        serverCache.remove(serverName);
        String suffix = ":" + serverName;
        whitelistCache.keySet().removeIf(key -> key.endsWith(suffix));
    }
    
    public void invalidateAll() {
        whitelistCache.clear();
        serverCache.clear();
    }
    
    private void cleanup() {
        whitelistCache.entrySet().removeIf(e -> e.getValue().isExpired());
        serverCache.entrySet().removeIf(e -> e.getValue().isExpired());
    }
    
    public void shutdown() {
        cleaner.shutdown();
    }
    
    private record CacheEntry<T>(T value, long expiresAt) {
        boolean isExpired() {
            return System.currentTimeMillis() > expiresAt;
        }
    }
}

