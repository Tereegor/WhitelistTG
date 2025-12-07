package ru.tereegor.whitelist.velocity.config;

import lombok.Getter;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@Getter
public class VelocityConfig {
    
    private final String language;
    private final String kickMessage;
    private final String whitelistDisabledMessage;
    private final List<String> bypassServers;
    
    private final String storageType;
    private final String databaseHost;
    private final int databasePort;
    private final String databaseName;
    private final String databaseUsername;
    private final String databasePassword;
    
    private final int poolMaxSize;
    private final int poolMinIdle;
    private final long connectionTimeout;
    private final long idleTimeout;
    private final long maxLifetime;
    
    private final int cacheTtl;
    private final int cacheMaxSize;
    
    private final boolean debug;
    
    public VelocityConfig(Path dataDirectory) {
        Path configPath = dataDirectory.resolve("config.yml");
        Map<String, Object> config;
        
        try (InputStream in = Files.newInputStream(configPath)) {
            Yaml yaml = new Yaml();
            Object loaded = yaml.load(in);
            if (loaded instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> typedConfig = (Map<String, Object>) loaded;
                config = typedConfig;
            } else {
                config = Map.of();
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load config.yml", e);
        }
        
        this.language = getString(config, "language", "ru");
        
        Map<String, Object> messages = getMap(config, "messages");
        this.kickMessage = getString(messages, "kick-message", 
                "&cУ вас нет доступа к серверу &6%server%");
        this.whitelistDisabledMessage = getString(messages, "whitelist-disabled",
                "&aВайтлист отключен");
        
        this.bypassServers = getList(config, "bypass-servers", List.of("lobby", "hub"));
        
        this.storageType = getString(config, "storage", "H2");
        
        Map<String, Object> database = getMap(config, "database");
        this.databaseHost = getString(database, "host", "localhost");
        this.databasePort = getInt(database, "port", 3306);
        this.databaseName = getString(database, "database", "whitelist");
        this.databaseUsername = getString(database, "username", "root");
        this.databasePassword = getString(database, "password", "");
        
        Map<String, Object> pool = getMap(database, "pool");
        this.poolMaxSize = getInt(pool, "maximum-pool-size", 10);
        this.poolMinIdle = getInt(pool, "minimum-idle", 2);
        this.connectionTimeout = getLong(pool, "connection-timeout", 30000);
        this.idleTimeout = getLong(pool, "idle-timeout", 600000);
        this.maxLifetime = getLong(pool, "max-lifetime", 1800000);
        
        Map<String, Object> cache = getMap(config, "cache");
        this.cacheTtl = getInt(cache, "ttl", 60);
        this.cacheMaxSize = getInt(cache, "max-size", 1000);
        
        this.debug = getBoolean(config, "debug", false);
    }
    
    public boolean isBypassServer(String serverName) {
        return bypassServers.stream()
                .anyMatch(s -> s.equalsIgnoreCase(serverName));
    }
    
    @SuppressWarnings("unchecked")
    private Map<String, Object> getMap(Map<String, Object> config, String key) {
        Object value = config.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return Map.of();
    }
    
    private String getString(Map<String, Object> config, String key, String defaultValue) {
        Object value = config.get(key);
        return value != null ? value.toString() : defaultValue;
    }
    
    private int getInt(Map<String, Object> config, String key, int defaultValue) {
        Object value = config.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }
    
    private long getLong(Map<String, Object> config, String key, long defaultValue) {
        Object value = config.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return defaultValue;
    }
    
    private boolean getBoolean(Map<String, Object> config, String key, boolean defaultValue) {
        Object value = config.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return defaultValue;
    }
    
    @SuppressWarnings("unchecked")
    private List<String> getList(Map<String, Object> config, String key, List<String> defaultValue) {
        Object value = config.get(key);
        if (value instanceof List) {
            return (List<String>) value;
        }
        return defaultValue;
    }
}

