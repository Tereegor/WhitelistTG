package ru.tereegor.whitelist.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import lombok.Getter;
import org.slf4j.Logger;
import ru.tereegor.whitelist.common.config.DatabaseConfig;
import ru.tereegor.whitelist.common.storage.SqlStorage;
import ru.tereegor.whitelist.common.storage.StorageType;
import ru.tereegor.whitelist.velocity.cache.WhitelistCache;
import ru.tereegor.whitelist.velocity.command.WhitelistVelocityCommand;
import ru.tereegor.whitelist.velocity.config.VelocityConfig;
import ru.tereegor.whitelist.velocity.listener.ServerConnectListener;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Plugin(
        id = "whitelisttg",
        name = "WhitelistTG",
        version = "1.0.0",
        description = "Multi-server whitelist with Telegram integration",
        authors = {"Tereegor"}
)
@Getter
public class WhitelistVelocityPlugin {
    
    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;
    
    private VelocityConfig config;
    private SqlStorage storage;
    private WhitelistCache cache;
    
    @Inject
    public WhitelistVelocityPlugin(ProxyServer proxy, Logger logger, 
            @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }
    
    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        if (!Files.exists(dataDirectory)) {
            try {
                Files.createDirectories(dataDirectory);
            } catch (IOException e) {
                logger.error("Failed to create data directory", e);
                return;
            }
        }
        
        saveDefaultConfig();
        
        this.config = new VelocityConfig(dataDirectory);
        
        initDatabase();
        
        this.cache = new WhitelistCache(
                storage, 
                config.getCacheTtl(), 
                config.getCacheMaxSize()
        );
        
        proxy.getEventManager().register(this, new ServerConnectListener(this));
        
        proxy.getCommandManager().register(
                proxy.getCommandManager().metaBuilder("wlv")
                        .aliases("whitelistvelocity")
                        .plugin(this)
                        .build(),
                new WhitelistVelocityCommand(this)
        );
        
        logger.info("WhitelistTG Velocity plugin enabled!");
        
        storage.getAllServers().thenAccept(servers -> {
            if (servers.isEmpty()) {
                logger.info("No servers registered yet. Waiting for Bukkit plugins to register...");
            } else {
                logger.info("Found {} registered servers:", servers.size());
                for (var server : servers) {
                    logger.info("  - {} (whitelist: {}, online: {})", 
                            server.getName(), 
                            server.isWhitelistEnabled() ? "enabled" : "disabled",
                            server.isOnline() ? "yes" : "no");
                }
            }
        });
    }
    
    @Subscribe
    public void onProxyShutdown(ProxyShutdownEvent event) {
        if (cache != null) {
            cache.shutdown();
        }
        if (storage != null) {
            storage.close().join();
        }
        logger.info("WhitelistTG Velocity plugin disabled!");
    }
    
    private void saveDefaultConfig() {
        Path configPath = dataDirectory.resolve("config.yml");
        if (!Files.exists(configPath)) {
            try (InputStream in = getClass().getResourceAsStream("/config.yml")) {
                if (in != null) {
                    Files.copy(in, configPath);
                }
            } catch (IOException e) {
                logger.error("Failed to save default config", e);
            }
        }
    }
    
    private void initDatabase() {
        DatabaseConfig dbConfig = DatabaseConfig.builder()
                .storageType(StorageType.fromString(config.getStorageType()))
                .host(config.getDatabaseHost())
                .port(config.getDatabasePort())
                .database(config.getDatabaseName())
                .username(config.getDatabaseUsername())
                .password(config.getDatabasePassword())
                .maximumPoolSize(config.getPoolMaxSize())
                .minimumIdle(config.getPoolMinIdle())
                .connectionTimeout(config.getConnectionTimeout())
                .idleTimeout(config.getIdleTimeout())
                .maxLifetime(config.getMaxLifetime())
                .dataFolderPath(dataDirectory.toString())
                .build();
        
        this.storage = new SqlStorage(dbConfig, msg -> logger.info(msg));
        storage.initialize().join();
    }
    
    public void reload() {
        this.config = new VelocityConfig(dataDirectory);
        if (cache != null) {
            cache.invalidateAll();
        }
        logger.info("Configuration reloaded!");
    }
}

