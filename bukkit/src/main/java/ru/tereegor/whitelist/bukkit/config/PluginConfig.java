package ru.tereegor.whitelist.bukkit.config;

import lombok.Getter;
import org.bukkit.configuration.file.FileConfiguration;
import ru.tereegor.whitelist.bukkit.WhitelistPlugin;

@Getter
public class PluginConfig {
    
    private final WhitelistPlugin plugin;
    
    private final String serverName;
    private final String serverDisplayName;
    private final String language;
    
    private boolean whitelistEnabled;
    private boolean autoAdd;
    private final String kickMessage;
    private final int codeExpirationMinutes;
    
    private final boolean telegramEnabled;
    private final String telegramToken;
    private final String telegramUsername;
    private final String rules;
    
    private final String dbUsername;
    private final String dbPassword;
    
    private final boolean debug;
    
    public PluginConfig(WhitelistPlugin plugin) {
        this.plugin = plugin;
        FileConfiguration config = plugin.getConfig();
        
        this.serverName = config.getString("server-name", "MyServer");
        this.serverDisplayName = config.getString("server-display-name", "&6" + serverName);
        this.language = config.getString("language", "ru");
        
        this.whitelistEnabled = config.getBoolean("whitelist.enabled", true);
        this.autoAdd = config.getBoolean("whitelist.auto-add", false);
        this.kickMessage = config.getString("whitelist.kick-message", 
                "&cВы не в вайтлисте сервера %server%");
        this.codeExpirationMinutes = config.getInt("whitelist.code-expiration-minutes", 30);
        
        this.telegramEnabled = config.getBoolean("telegram.enabled", false);
        this.telegramToken = config.getString("telegram.token", "");
        this.telegramUsername = config.getString("telegram.username", "");
        this.rules = config.getString("telegram.rules", "Правила сервера...");
        
        this.dbUsername = config.getString("database.username", "root");
        this.dbPassword = config.getString("database.password", "");
        
        this.debug = config.getBoolean("debug", false);
        
        if (telegramEnabled && (telegramToken.isEmpty() || telegramToken.contains("1234567890"))) {
            plugin.getLogger().warning("Telegram enabled but token not configured in config.yml!");
            plugin.getLogger().warning("Please set telegram.token and telegram.username in config.yml");
        }
    }
    
    public void setWhitelistEnabled(boolean enabled) {
        this.whitelistEnabled = enabled;
        plugin.getConfig().set("whitelist.enabled", enabled);
        plugin.saveConfig();
    }
    
    public void setAutoAdd(boolean enabled) {
        this.autoAdd = enabled;
        plugin.getConfig().set("whitelist.auto-add", enabled);
        plugin.saveConfig();
    }
}
