package ru.tereegor.whitelist.bukkit.listener;

import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import ru.tereegor.whitelist.bukkit.WhitelistPlugin;
import ru.tereegor.whitelist.bukkit.config.PluginConfig;

import java.util.UUID;

import static ru.tereegor.whitelist.bukkit.manager.MessageManager.placeholder;

@RequiredArgsConstructor
public class PlayerLoginListener implements Listener {

    private final WhitelistPlugin plugin;

    private PluginConfig config() {
        return plugin.getPluginConfig();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerPreLogin(AsyncPlayerPreLoginEvent event) {
        if (!config().isWhitelistEnabled()) {
            return;
        }

        UUID playerUuid = event.getUniqueId();
        String playerName = event.getName();

        boolean isWhitelisted = checkWhitelist(playerUuid, playerName);

        if (!isWhitelisted) {
            Component kickMessage = plugin.getMessageManager().getComponentNoPrefix("kick.not-whitelisted",
                    placeholder("player", playerName),
                    placeholder("server", config().getServerDisplayName()));
            
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST, kickMessage);
            debug("Denied %s connection - not whitelisted".formatted(playerName));
        }
    }
    
    private boolean checkWhitelist(UUID playerUuid, String playerName) {
        try {
            return plugin.getStorage()
                    .isWhitelisted(playerUuid, config().getServerName())
                    .join();
        } catch (Exception e) {
            plugin.getLogger().severe("Error checking whitelist for %s: %s"
                    .formatted(playerName, e.getMessage()));
            e.printStackTrace();
            return false;
        }
    }
    
    private void debug(String message) {
        if (config().isDebug()) {
            plugin.getLogger().info(message);
        }
    }
}
