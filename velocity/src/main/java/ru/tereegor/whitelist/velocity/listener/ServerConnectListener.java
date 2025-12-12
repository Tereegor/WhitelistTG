package ru.tereegor.whitelist.velocity.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import ru.tereegor.whitelist.velocity.WhitelistVelocityPlugin;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class ServerConnectListener {
    
    private final WhitelistVelocityPlugin plugin;
    private final LegacyComponentSerializer serializer = LegacyComponentSerializer.legacyAmpersand();
    private static final long WHITELIST_CHECK_TIMEOUT_SECONDS = 5;
    
    public ServerConnectListener(WhitelistVelocityPlugin plugin) {
        this.plugin = plugin;
    }
    
    @Subscribe
    public void onServerPreConnect(ServerPreConnectEvent event) {
        Player player = event.getPlayer();
        
        var targetServer = event.getOriginalServer();
        if (targetServer == null) {
            return;
        }
        
        String serverName = targetServer.getServerInfo().getName();
        
        if (plugin.getConfig().isBypassServer(serverName)) {
            return;
        }
        
        if (player.hasPermission("whitelist.bypass")) {
            return;
        }
        
        boolean isWhitelisted;
        try {
            CompletableFuture<Boolean> future = plugin.getCache()
                    .isWhitelisted(player.getUniqueId(), serverName);
            
            isWhitelisted = future.get(WHITELIST_CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            plugin.getLogger().error("Whitelist check timeout for " + player.getUsername() + " after " + 
                    WHITELIST_CHECK_TIMEOUT_SECONDS + " seconds");
            isWhitelisted = false;
        } catch (Exception e) {
            plugin.getLogger().error("Error checking whitelist for " + player.getUsername(), e);
            isWhitelisted = false;
        }
        
        if (!isWhitelisted) {
            String kickMessage = plugin.getConfig().getKickMessage()
                    .replace("%player%", player.getUsername())
                    .replace("%server%", serverName);

            player.disconnect(serializer.deserialize(kickMessage));

            if (plugin.getConfig().isDebug()) {
                plugin.getLogger().info("Denied {} connection to {} - not whitelisted",
                        player.getUsername(), serverName);
            }
        } else {
            if (plugin.getConfig().isDebug()) {
                plugin.getLogger().info("Allowed {} connection to {} - whitelisted", 
                        player.getUsername(), serverName);
            }
        }
    }
}

