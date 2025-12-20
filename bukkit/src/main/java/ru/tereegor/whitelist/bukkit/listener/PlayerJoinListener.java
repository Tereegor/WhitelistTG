package ru.tereegor.whitelist.bukkit.listener;

import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import ru.tereegor.whitelist.bukkit.WhitelistPlugin;
import ru.tereegor.whitelist.bukkit.config.PluginConfig;
import ru.tereegor.whitelist.bukkit.manager.MessageManager;
import ru.tereegor.whitelist.common.model.RegistrationType;

@RequiredArgsConstructor
public class PlayerJoinListener implements Listener {
    
    private final WhitelistPlugin plugin;
    
    private PluginConfig config() {
        return plugin.getPluginConfig();
    }
    
    private MessageManager msg() {
        return plugin.getMessageManager();
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerLogin(PlayerLoginEvent event) {
        Player player = event.getPlayer();
        
        if (!config().isWhitelistEnabled() || player.hasPermission("whitelist.bypass") || config().isAutoAdd()) {
            return;
        }
        
        try {
            boolean whitelisted = plugin.getWhitelistManager()
                    .isWhitelisted(player.getUniqueId(), player.getName())
                    .join();
            
            if (!whitelisted) {
                event.disallow(PlayerLoginEvent.Result.KICK_WHITELIST, createKickMessage(player));
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Error checking whitelist for %s: %s"
                    .formatted(player.getName(), e.getMessage()));
            e.printStackTrace();
            event.disallow(PlayerLoginEvent.Result.KICK_WHITELIST, createKickMessage(player));
        }
    }
    
    private Component createKickMessage(Player player) {
        return msg().processAndDeserialize(config().getKickMessage(),
                Placeholder.parsed("player", player.getName()),
                Placeholder.parsed("server", config().getServerName()));
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        if (!config().isWhitelistEnabled() || !config().isAutoAdd() || player.hasPermission("whitelist.bypass")) {
            return;
        }
        
        plugin.getWhitelistManager()
                .isWhitelisted(player.getUniqueId(), player.getName())
                .thenAccept(whitelisted -> {
                    if (!whitelisted) {
                        autoAddPlayer(player);
                    }
                });
    }
    
    private void autoAddPlayer(Player player) {
        plugin.getWhitelistManager()
                .addPlayer(player.getUniqueId(), player.getName(),
                        RegistrationType.MANUAL, "Автоматически добавлен при входе", "Система")
                .thenAccept(entry -> {
                    msg().send(player, "auto-added",
                            MessageManager.placeholders("server", config().getServerName()));
                    plugin.getLogger().info("Player %s automatically added to whitelist"
                            .formatted(player.getName()));
                })
                .exceptionally(e -> {
                    plugin.getLogger().warning("Failed to auto-add %s to whitelist: %s"
                            .formatted(player.getName(), e.getMessage()));
                    return null;
                });
    }
}
