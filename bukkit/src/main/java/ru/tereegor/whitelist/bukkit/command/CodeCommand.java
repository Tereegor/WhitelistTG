package ru.tereegor.whitelist.bukkit.command;

import lombok.RequiredArgsConstructor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import ru.tereegor.whitelist.bukkit.WhitelistPlugin;
import ru.tereegor.whitelist.bukkit.manager.MessageManager;

import static ru.tereegor.whitelist.bukkit.manager.MessageManager.placeholder;

@RequiredArgsConstructor
public class CodeCommand implements CommandExecutor {
    
    private final WhitelistPlugin plugin;
    
    private MessageManager msg() {
        return plugin.getMessageManager();
    }
    
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
            @NotNull String label, @NotNull String[] args) {
        
        if (!(sender instanceof Player player)) {
            msg().send(sender, "general.player-only");
            return true;
        }
        
        if (args.length < 1) {
            msg().send(sender, "code.usage");
            return true;
        }
        
        String code = normalizeCode(args[0].toUpperCase());
        
        debug("Player %s trying to activate code: %s".formatted(player.getName(), code));
        
        plugin.getWhitelistManager().activateCode(code, player.getUniqueId(), player.getName())
                .thenAccept(result -> runSync(() -> handleActivationResult(player, result)))
                .exceptionally(e -> {
                    plugin.getLogger().warning("Error activating code for %s: %s"
                            .formatted(player.getName(), e.getMessage()));
                    debugPrint(e);
                    runSync(() -> msg().send(player, "code.invalid"));
                    return null;
                });
        
        return true;
    }
    
    private String normalizeCode(String code) {
        if (!code.contains("-") && code.length() >= 6) {
            return code.substring(0, 3) + "-" + code.substring(3);
        }
        return code;
    }
    
    private void handleActivationResult(Player player, 
            ru.tereegor.whitelist.bukkit.manager.WhitelistManager.ActivationResult result) {
        if (result.isSuccess()) {
            msg().send(player, "code.success");
            msg().send(player, "code.success-details", 
                    placeholder("server", plugin.getPluginConfig().getServerName()));
            msg().send(player, "code.linked");
        } else {
            debug("Code activation failed for %s: %s".formatted(player.getName(), result.getMessageKey()));
            msg().send(player, result.getMessageKey());
        }
    }
    
    private void runSync(Runnable task) {
        plugin.getServer().getScheduler().runTask(plugin, task);
    }
    
    private void debug(String message) {
        if (plugin.getPluginConfig().isDebug()) {
            plugin.getLogger().info(message);
        }
    }
    
    private void debugPrint(Throwable e) {
        if (plugin.getPluginConfig().isDebug()) {
            e.printStackTrace();
        }
    }
}
