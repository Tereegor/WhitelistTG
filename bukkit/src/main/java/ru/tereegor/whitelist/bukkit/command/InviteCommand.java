package ru.tereegor.whitelist.bukkit.command;

import lombok.RequiredArgsConstructor;
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

import static ru.tereegor.whitelist.bukkit.manager.MessageManager.placeholder;
import static ru.tereegor.whitelist.bukkit.manager.MessageManager.placeholders;

@RequiredArgsConstructor
public class InviteCommand implements CommandExecutor, TabCompleter {
    
    private final WhitelistPlugin plugin;
    
    private MessageManager msg() {
        return plugin.getMessageManager();
    }
    
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
            @NotNull String label, @NotNull String[] args) {
        
        if (!sender.hasPermission("whitelist.invite")) {
            msg().send(sender, "general.no-permission");
            return true;
        }
        
        if (args.length < 1) {
            msg().send(sender, "invite.usage");
            return true;
        }
        
        String targetName = args[0];
        String reason = args.length > 1 
                ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) 
                : "Приглашен " + sender.getName();
        
        var targetUuid = plugin.getWhitelistManager().resolvePlayerUuid(targetName);
        
        plugin.getWhitelistManager().isWhitelisted(targetUuid, targetName)
                .thenAccept(whitelisted -> {
                    if (whitelisted) {
                        msg().send(sender, "invite.already-invited", placeholder("player", targetName));
                        return;
                    }
                    processInvite(sender, targetName, targetUuid, reason);
                });
        
        return true;
    }
    
    private void processInvite(CommandSender sender, String targetName, 
            java.util.UUID targetUuid, String reason) {
        
        Long inviterTelegramId = getInviterTelegramId(sender);
        
        plugin.getWhitelistManager().addPlayerWithInvite(
                targetUuid, targetName, reason, sender.getName(), inviterTelegramId)
                .thenAccept(entry -> runSync(() -> {
                    msg().send(sender, "invite.success", 
                            placeholders("player", targetName,
                                    "server", plugin.getPluginConfig().getServerName()));
                    msg().send(sender, "invite.success-reason", placeholders("reason", reason));
                    notifyAdmins(sender.getName(), targetName, reason);
                }));
    }
    
    private Long getInviterTelegramId(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            return null;
        }
        
        return plugin.getWhitelistManager()
                .getPlayerLink(player.getUniqueId())
                .join()
                .map(PlayerLink::getTelegramId)
                .orElse(null);
    }
    
    private void notifyAdmins(String inviter, String player, String reason) {
        plugin.getServer().broadcast(
                msg().getComponent("notify.player-invited",
                        placeholder("inviter", inviter),
                        placeholder("player", player),
                        placeholder("reason", reason)), 
                "whitelist.admin");
    }
    
    private void runSync(Runnable task) {
        plugin.getServer().getScheduler().runTask(plugin, task);
    }
    
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
            @NotNull String alias, @NotNull String[] args) {
        
        if (!sender.hasPermission("whitelist.invite")) {
            return List.of();
        }
        
        return args.length == 1 ? null : List.of();
    }
}
