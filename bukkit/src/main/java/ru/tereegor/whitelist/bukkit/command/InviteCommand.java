package ru.tereegor.whitelist.bukkit.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.tereegor.whitelist.bukkit.WhitelistPlugin;
import ru.tereegor.whitelist.bukkit.manager.MessageManager;
import ru.tereegor.whitelist.common.model.PlayerLink;

import java.util.Arrays;
import java.util.List;

import static ru.tereegor.whitelist.bukkit.manager.MessageManager.placeholders;

public class InviteCommand implements CommandExecutor, TabCompleter {
    
    private final WhitelistPlugin plugin;
    private final MessageManager msg;
    
    public InviteCommand(WhitelistPlugin plugin) {
        this.plugin = plugin;
        this.msg = plugin.getMessageManager();
    }
    
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
            @NotNull String label, @NotNull String[] args) {
        
        if (!sender.hasPermission("whitelist.invite")) {
            msg.send(sender, "general.no-permission");
            return true;
        }
        
        if (args.length < 1) {
            msg.send(sender, "invite.usage");
            return true;
        }
        
        String targetName = args[0];
        String reason = args.length > 1 ? 
                String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : 
                "Приглашен " + sender.getName();
        
        var targetUuid = plugin.getWhitelistManager().resolvePlayerUuid(targetName);
        
        plugin.getWhitelistManager().isWhitelisted(targetUuid, targetName).thenAccept(whitelisted -> {
            if (whitelisted) {
                msg.send(sender, "invite.already-invited", 
                        MessageManager.placeholder("player", targetName));
                return;
            }
            
            Long inviterTelegramId = null;
            if (sender instanceof Player player) {
                PlayerLink link = plugin.getWhitelistManager()
                        .getPlayerLink(player.getUniqueId()).join().orElse(null);
                if (link != null) {
                    inviterTelegramId = link.getTelegramId();
                }
            }
            
            final Long finalInviterTelegramId = inviterTelegramId;
            
            plugin.getWhitelistManager().addPlayerWithInvite(
                    targetUuid, 
                    targetName, 
                    reason, 
                    sender.getName(),
                    finalInviterTelegramId
            ).thenAccept(entry -> {
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    msg.send(sender, "invite.success", 
                            placeholders("player", targetName,
                                    "server", plugin.getPluginConfig().getServerName()));
                    msg.send(sender, "invite.success-reason", 
                                    placeholders("reason", reason));
                    
                    notifyAdmins(sender.getName(), targetName, reason);
                });
            });
        });
        
        return true;
    }
    
    private void notifyAdmins(String inviter, String player, String reason) {
        plugin.getServer().broadcast(
                msg.getComponent("notify.player-invited",
                        MessageManager.placeholder("inviter", inviter),
                        MessageManager.placeholder("player", player),
                        MessageManager.placeholder("reason", reason)), 
                "whitelist.admin");
    }
    
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
            @NotNull String alias, @NotNull String[] args) {
        
        if (!sender.hasPermission("whitelist.invite")) {
            return List.of();
        }
        
        if (args.length == 1) {
            return null;
        }
        
        return List.of();
    }
}

