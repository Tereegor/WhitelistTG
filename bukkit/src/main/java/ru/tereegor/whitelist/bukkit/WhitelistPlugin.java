package ru.tereegor.whitelist.bukkit;

import lombok.Getter;
import org.bukkit.plugin.java.JavaPlugin;
import ru.tereegor.whitelist.bukkit.command.CodeCommand;
import ru.tereegor.whitelist.bukkit.command.InviteCommand;
import ru.tereegor.whitelist.bukkit.command.WhitelistCommand;
import ru.tereegor.whitelist.bukkit.config.PluginConfig;
import ru.tereegor.whitelist.bukkit.listener.PlayerJoinListener;
import ru.tereegor.whitelist.bukkit.manager.MessageManager;
import ru.tereegor.whitelist.bukkit.manager.WhitelistManager;
import ru.tereegor.whitelist.bukkit.telegram.TelegramBot;
import ru.tereegor.whitelist.common.config.DatabaseConfig;
import ru.tereegor.whitelist.common.model.ServerInfo;
import ru.tereegor.whitelist.common.storage.SqlStorage;
import ru.tereegor.whitelist.common.storage.StorageType;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Getter
public class WhitelistPlugin extends JavaPlugin {
    
    @Getter
    private static WhitelistPlugin instance;
    
    private PluginConfig pluginConfig;
    private MessageManager messageManager;
    private SqlStorage storage;
    private WhitelistManager whitelistManager;
    private TelegramBot telegramBot;
    private ScheduledExecutorService scheduler;
    
    @Override
    public void onEnable() {
        try {
            instance = this;
            
            getLogger().info("Enabling WhitelistTG...");
            
            saveDefaultConfig();
            this.pluginConfig = new PluginConfig(this);
            getLogger().info("Configuration loaded. Server: " + pluginConfig.getServerName());
            
            this.messageManager = new MessageManager(this, pluginConfig.getLanguage());
            getLogger().info("Messages loaded. Language: " + pluginConfig.getLanguage());
            
            initDatabase();
            getLogger().info("Database initialized");
            
            this.whitelistManager = new WhitelistManager(this, storage);
            
            registerServer();
            getLogger().info("Server registered: " + pluginConfig.getServerName());
            
            registerCommands();
            getLogger().info("Commands registered");
            
            getServer().getPluginManager().registerEvents(new PlayerJoinListener(this), this);
            getLogger().info("Listeners registered");
            
            if (pluginConfig.isTelegramEnabled()) {
                startTelegramBot();
            }
            
            startHeartbeat();
            getLogger().info("Heartbeat scheduler started");
            
            getLogger().info("WhitelistTG enabled successfully! Server: " + pluginConfig.getServerName());
        } catch (Exception e) {
            getLogger().severe("Failed to enable WhitelistTG!");
            getLogger().severe("Error: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
        }
    }
    
    @Override
    public void onDisable() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdown();
        }
        
        if (telegramBot != null) {
            telegramBot.stop();
        }
        
        if (storage != null) {
            storage.close().join();
        }
        
        getLogger().info("WhitelistTG disabled!");
    }
    
    private void initDatabase() {
        try {
            DatabaseConfig dbConfig = DatabaseConfig.builder()
                    .storageType(StorageType.fromString(getConfig().getString("storage", "H2")))
                    .host(getConfig().getString("database.host", "localhost"))
                    .port(getConfig().getInt("database.port", 3306))
                    .database(getConfig().getString("database.database", "whitelist"))
                    .username(pluginConfig.getDbUsername())
                    .password(pluginConfig.getDbPassword())
                    .maximumPoolSize(getConfig().getInt("database.pool.maximum-pool-size", 10))
                    .minimumIdle(getConfig().getInt("database.pool.minimum-idle", 2))
                    .connectionTimeout(getConfig().getLong("database.pool.connection-timeout", 30000))
                    .idleTimeout(getConfig().getLong("database.pool.idle-timeout", 600000))
                    .maxLifetime(getConfig().getLong("database.pool.max-lifetime", 1800000))
                    .dataFolderPath(getDataFolder().getAbsolutePath())
                    .build();
            
            getLogger().info("Initializing database: " + dbConfig.getStorageType());
            this.storage = new SqlStorage(dbConfig, msg -> getLogger().info("[DB] " + msg));
            storage.initialize().join();
            getLogger().info("Database initialized successfully");
        } catch (Exception e) {
            getLogger().severe("Failed to initialize database: " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Database initialization failed", e);
        }
    }
    
    private void registerServer() {
        ServerInfo serverInfo = ServerInfo.builder()
                .name(pluginConfig.getServerName())
                .displayName(pluginConfig.getServerDisplayName())
                .whitelistEnabled(pluginConfig.isWhitelistEnabled())
                .build();
        
        storage.registerServer(serverInfo).join();
    }
    
    private void registerCommands() {
        var wlCommand = getCommand("wlt");
        if (wlCommand != null) {
            WhitelistCommand cmd = new WhitelistCommand(this);
            wlCommand.setExecutor(cmd);
            wlCommand.setTabCompleter(cmd);
        }
        
        var codeCommand = getCommand("code");
        if (codeCommand != null) {
            codeCommand.setExecutor(new CodeCommand(this));
        }
        
        var inviteCommand = getCommand("invite");
        if (inviteCommand != null) {
            InviteCommand cmd = new InviteCommand(this);
            inviteCommand.setExecutor(cmd);
            inviteCommand.setTabCompleter(cmd);
        }
    }
    
    private void startTelegramBot() {
        try {
            this.telegramBot = new TelegramBot(this);
            telegramBot.start();
            getLogger().info("Telegram bot started successfully!");
        } catch (Exception e) {
            getLogger().severe("Failed to start Telegram bot: " + e.getMessage());
            if (pluginConfig.isDebug()) {
                e.printStackTrace();
            }
        }
    }
    
    private void startHeartbeat() {
        scheduler = Executors.newSingleThreadScheduledExecutor();
        int interval = getConfig().getInt("heartbeat-interval", 30);
        
        scheduler.scheduleAtFixedRate(() -> {
            try {
                storage.updateServerHeartbeat(pluginConfig.getServerName());
                storage.updateServerWhitelistStatus(
                        pluginConfig.getServerName(), 
                        pluginConfig.isWhitelistEnabled()
                );
            } catch (Exception e) {
                if (pluginConfig.isDebug()) {
                    getLogger().warning("Heartbeat failed: " + e.getMessage());
                }
            }
        }, interval, interval, TimeUnit.SECONDS);
    }
    
    public void reload() {
        reloadConfig();
        this.pluginConfig = new PluginConfig(this);
        this.messageManager = new MessageManager(this, pluginConfig.getLanguage());
        
        registerServer();
        
        if (telegramBot != null) {
            telegramBot.stop();
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (pluginConfig.isTelegramEnabled()) {
            startTelegramBot();
        }
    }
}

