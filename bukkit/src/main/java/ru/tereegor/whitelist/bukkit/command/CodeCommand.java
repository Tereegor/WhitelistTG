package ru.tereegor.whitelist.bukkit.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import ru.tereegor.whitelist.bukkit.WhitelistPlugin;
import ru.tereegor.whitelist.bukkit.manager.MessageManager;

import static ru.tereegor.whitelist.bukkit.manager.MessageManager.placeholder;

public class CodeCommand implements CommandExecutor {
    
    private final WhitelistPlugin plugin;
    private final MessageManager msg;
    
    public CodeCommand(WhitelistPlugin plugin) {
        this.plugin = plugin;
        this.msg = plugin.getMessageManager();
    }
    
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
            @NotNull String label, @NotNull String[] args) {
        
        if (!(sender instanceof Player player)) {
            msg.send(sender, "general.player-only");
            return true;
        }
        
        if (args.length < 1) {
            msg.send(sender, "code.usage");
            return true;
        }
        
        String code = args[0].toUpperCase();
        
        String normalizedCode = code.contains("-") ? code : 
                (code.length() >= 6 ? code.substring(0, 3) + "-" + code.substring(3) : code);
        
        plugin.getWhitelistManager().activateCode(normalizedCode, player.getUniqueId(), player.getName())
                .thenAccept(result -> {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (result.success()) {
                            msg.send(player, "code.success");
                            msg.send(player, "code.success-details", 
                                    placeholder("server", plugin.getPluginConfig().getServerName()));
                            msg.send(player, "code.linked");
                        } else {
                            msg.send(player, result.messageKey());
                        }
                    });
                });
        
        return true;
    }
}

