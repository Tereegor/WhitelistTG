package ru.tereegor.whitelist.velocity.listener;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.Player;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import ru.tereegor.whitelist.velocity.WhitelistVelocityPlugin;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@RequiredArgsConstructor
public class ServerConnectListener {
    
    private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.legacyAmpersand();
    private static final long WHITELIST_CHECK_TIMEOUT_SECONDS = 5;
    
    private final WhitelistVelocityPlugin plugin;
    
    @Subscribe
    public void onServerPreConnect(ServerPreConnectEvent event) {
        try {
            processConnection(event);
        } catch (Exception e) {
            plugin.getLogger().error("Unexpected error in ServerPreConnectEvent", e);
            denyWithError(event);
        }
    }
    
    private void processConnection(ServerPreConnectEvent event) {
        Player player = event.getPlayer();
        var targetServer = event.getOriginalServer();
        
        if (targetServer == null) {
            return;
        }
        
        String serverName = targetServer.getServerInfo().getName();
        
        if (shouldBypass(player, serverName)) {
            return;
        }
        
        boolean isWhitelisted = checkWhitelist(player, serverName);
        
        if (!isWhitelisted) {
            denyAccess(event, player, serverName);
        } else {
            debug("Allowed %s connection to %s - whitelisted".formatted(player.getUsername(), serverName));
        }
    }
    
    private boolean shouldBypass(Player player, String serverName) {
        if (!plugin.getConfig().requiresWhitelist(serverName)) {
            debug("Bypassing whitelist check for %s on server %s".formatted(player.getUsername(), serverName));
            return true;
        }
        
        if (player.hasPermission("whitelist.bypass")) {
            debug("Bypassing whitelist check for %s - has permission".formatted(player.getUsername()));
            return true;
        }
        
        return false;
    }
    
    private boolean checkWhitelist(Player player, String serverName) {
        try {
            return plugin.getCache()
                    .isWhitelisted(player.getUniqueId(), serverName)
                    .get(WHITELIST_CHECK_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            plugin.getLogger().error("Whitelist check timeout for {} after {} seconds",
                    player.getUsername(), WHITELIST_CHECK_TIMEOUT_SECONDS);
            return false;
        } catch (Exception e) {
            plugin.getLogger().error("Error checking whitelist for {} on server {}",
                    player.getUsername(), serverName, e);
            return false;
        }
    }
    
    private void denyAccess(ServerPreConnectEvent event, Player player, String serverName) {
        String kickMessage = plugin.getConfig().getKickMessage()
                .replace("%player%", player.getUsername())
                .replace("%server%", serverName);

        event.setResult(ServerPreConnectEvent.ServerResult.denied());
        player.sendMessage(SERIALIZER.deserialize(kickMessage));
        
        debug("Denied %s connection to %s - not whitelisted".formatted(player.getUsername(), serverName));
    }
    
    private void denyWithError(ServerPreConnectEvent event) {
        event.setResult(ServerPreConnectEvent.ServerResult.denied());
        event.getPlayer().sendMessage(SERIALIZER.deserialize(
                "&cПроизошла ошибка при проверке доступа. Попробуйте позже."));
    }
    
    private void debug(String message) {
        if (plugin.getConfig().isDebug()) {
            plugin.getLogger().info(message);
        }
    }
}
