package ru.tereegor.whitelist.bukkit.listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import ru.tereegor.whitelist.bukkit.WhitelistPlugin;
import ru.tereegor.whitelist.bukkit.manager.MessageManager;
import ru.tereegor.whitelist.common.model.RegistrationType;

public class PlayerJoinListener implements Listener {
    
    private final WhitelistPlugin plugin;
    
    public PlayerJoinListener(WhitelistPlugin plugin) {
        this.plugin = plugin;
    }
    
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerLogin(PlayerLoginEvent event) {
        Player player = event.getPlayer();
        
        if (!plugin.getPluginConfig().isWhitelistEnabled()) {
            return;
        }
        
        if (player.hasPermission("whitelist.bypass")) {
            return;
        }
        
        if (plugin.getPluginConfig().isAutoAdd()) {
            return;
        }
        
        try {
            boolean whitelisted = plugin.getWhitelistManager()
                    .isWhitelisted(player.getUniqueId(), player.getName())
                    .join();
            
            if (!whitelisted) {
                String kickMessage = plugin.getPluginConfig().getKickMessage();
                
                MessageManager msg = plugin.getMessageManager();
                Component message = msg.processAndDeserialize(kickMessage,
                        Placeholder.parsed("player", player.getName()),
                        Placeholder.parsed("server", plugin.getPluginConfig().getServerName()));
                
                event.disallow(PlayerLoginEvent.Result.KICK_WHITELIST, message);
            }
        } catch (Exception e) {
            plugin.getLogger().severe("Error checking whitelist for " + player.getName() + ": " + e.getMessage());
            e.printStackTrace();
            String kickMessage = plugin.getPluginConfig().getKickMessage();
            MessageManager msg = plugin.getMessageManager();
            Component message = msg.processAndDeserialize(kickMessage,
                    Placeholder.parsed("player", player.getName()),
                    Placeholder.parsed("server", plugin.getPluginConfig().getServerName()));
            event.disallow(PlayerLoginEvent.Result.KICK_WHITELIST, message);
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        
        if (!plugin.getPluginConfig().isWhitelistEnabled() || !plugin.getPluginConfig().isAutoAdd()) {
            return;
        }
        
        if (player.hasPermission("whitelist.bypass")) {
            return;
        }
        
        plugin.getWhitelistManager()
                .isWhitelisted(player.getUniqueId(), player.getName())
                .thenAccept(whitelisted -> {
                    if (!whitelisted) {
                        plugin.getWhitelistManager()
                                .addPlayer(
                                        player.getUniqueId(),
                                        player.getName(),
                                        RegistrationType.MANUAL,
                                        "Автоматически добавлен при входе",
                                        "Система"
                                )
                                .thenAccept(entry -> {
                                    MessageManager msg = plugin.getMessageManager();
                                    msg.send(player, "auto-added",
                                            MessageManager.placeholders(
                                                    "server", plugin.getPluginConfig().getServerName()
                                            ));
                                    
                                    plugin.getLogger().info("Player " + player.getName() + 
                                            " automatically added to whitelist");
                                })
                                .exceptionally(e -> {
                                    plugin.getLogger().warning("Failed to auto-add " + 
                                            player.getName() + " to whitelist: " + e.getMessage());
                                    return null;
                                });
                    }
                });
    }
}

