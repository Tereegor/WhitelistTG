package ru.tereegor.whitelist.bukkit.listener;

import java.util.UUID;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import net.kyori.adventure.text.Component;
import ru.tereegor.whitelist.bukkit.WhitelistPlugin;
import ru.tereegor.whitelist.bukkit.config.PluginConfig;
import ru.tereegor.whitelist.bukkit.manager.MessageManager;

public class PlayerLoginListener implements Listener {

    private final WhitelistPlugin plugin;

    public PlayerLoginListener(WhitelistPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        PluginConfig config = plugin.getPluginConfig();

        if (!config.isWhitelistEnabled()) {
            return;
        }

        UUID playerUuid = event.getUniqueId();
        String playerName = event.getName();

        boolean isWhitelisted;
        try {
            isWhitelisted = plugin.getStorage()
                    .isWhitelisted(playerUuid, config.getServerName())
                    .join();
        } catch (Exception e) {
            plugin.getLogger().severe("Error checking whitelist for " + playerName + ": " + e.getMessage());
            e.printStackTrace();
            isWhitelisted = false;
        }

        if (!isWhitelisted) {
            Component kickMessage = plugin.getMessageManager().getComponentNoPrefix("kick.not-whitelisted",
                    MessageManager.placeholder("player", playerName),
                    MessageManager.placeholder("server", config.getServerDisplayName()));
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST, kickMessage);

            if (config.isDebug()) {
                plugin.getLogger().info("Denied " + playerName + " connection - not whitelisted");
            }
        }
    }
}
