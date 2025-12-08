package ru.tereegor.whitelist.bukkit.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.tereegor.whitelist.bukkit.WhitelistPlugin;
import ru.tereegor.whitelist.bukkit.manager.MessageManager;
import ru.tereegor.whitelist.common.model.RegistrationType;
import ru.tereegor.whitelist.common.model.WhitelistEntry;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static ru.tereegor.whitelist.bukkit.manager.MessageManager.placeholders;

public class WhitelistCommand implements CommandExecutor, TabCompleter {
    
    private final WhitelistPlugin plugin;
    private final MessageManager msg;
    private static final DateTimeFormatter DATE_FORMAT = 
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());
    
    private final Map<String, Boolean> pendingConfirmations = new HashMap<>();
    
    public WhitelistCommand(WhitelistPlugin plugin) {
        this.plugin = plugin;
        this.msg = plugin.getMessageManager();
    }
    
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, 
            @NotNull String label, @NotNull String[] args) {
        
        if (!sender.hasPermission("whitelist.admin")) {
            msg.send(sender, "general.no-permission");
            return true;
        }
        
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }
        
        String subCommand = args[0].toLowerCase();
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);
        
        switch (subCommand) {
            case "add" -> handleAdd(sender, subArgs);
            case "remove", "rem", "del" -> handleRemove(sender, subArgs);
            case "list", "ls" -> handleList(sender, subArgs);
            case "info", "check" -> handleInfo(sender, subArgs);
            case "on", "enable" -> handleEnable(sender);
            case "off", "disable" -> handleDisable(sender);
            case "autoadd" -> handleAutoAdd(sender, subArgs);
            case "confirm" -> handleConfirm(sender);
            case "reload" -> handleReload(sender);
            case "help" -> sendHelp(sender);
            default -> msg.send(sender, "general.invalid-args", 
                    placeholders("usage", "/wlt help"));
        }
        
        return true;
    }
    
    private void handleAdd(CommandSender sender, String[] args) {
        if (args.length < 2) {
            msg.send(sender, "general.invalid-args", 
                    placeholders("usage", "/wlt add <name|uuid> <значение> [причина]"));
            return;
        }
        
        String type = args[0].toLowerCase();
        String input = args[1];
        String reason = args.length > 2 ? 
                String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : "Добавлен вручную";
        
        PlayerIdentifier identifier = parsePlayerIdentifier(type, input, sender, "add");
        if (identifier == null) {
            return;
        }
        
        final String finalPlayerName = identifier.playerName;
        
        plugin.getWhitelistManager().isWhitelisted(identifier.uuid, finalPlayerName).thenAccept(whitelisted -> {
            if (whitelisted) {
                msg.send(sender, "already-added", 
                        MessageManager.placeholder("player", finalPlayerName));
                return;
            }
            
            plugin.getWhitelistManager().addPlayer(identifier.uuid, finalPlayerName, 
                    RegistrationType.MANUAL, reason, sender.getName()).thenAccept(entry -> {
                msg.send(sender, "added", 
                        placeholders("player", finalPlayerName, 
                                "server", plugin.getPluginConfig().getServerName()));
            });
        });
    }
    
    private void handleRemove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            msg.send(sender, "general.invalid-args", 
                    placeholders("usage", "/wlt remove <name|uuid> <значение>"));
            return;
        }
        
        String type = args[0].toLowerCase();
        String input = args[1];
        
        PlayerIdentifier identifier = parsePlayerIdentifier(type, input, sender, "remove");
        if (identifier == null) {
            return;
        }
        
        final String finalPlayerName = identifier.playerName;
        
        plugin.getWhitelistManager().removePlayer(identifier.uuid).thenAccept(removed -> {
            if (removed) {
                msg.send(sender, "removed", 
                        placeholders("player", finalPlayerName, 
                                "server", plugin.getPluginConfig().getServerName()));
            } else {
                msg.send(sender, "not-found", 
                        placeholders("player", finalPlayerName));
            }
        });
    }
    
    private void handleList(CommandSender sender, String[] args) {
        int page = 1;
        if (args.length > 0) {
            try {
                page = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {}
        }
        
        final int currentPage = page;
        final int pageSize = 10;
        
        plugin.getWhitelistManager().getAllEntries().thenAccept(entries -> {
            if (entries.isEmpty()) {
                msg.send(sender, "list-empty");
                return;
            }
            
            int totalPages = (int) Math.ceil((double) entries.size() / pageSize);
            int startIndex = (currentPage - 1) * pageSize;
            int endIndex = Math.min(startIndex + pageSize, entries.size());
            
            if (startIndex >= entries.size()) {
                msg.send(sender, "list-empty");
                return;
            }
            
            List<WhitelistEntry> visibleEntries = new ArrayList<>();
            for (int i = startIndex; i < endIndex; i++) {
                visibleEntries.add(entries.get(i));
            }
            
            int maxPlayerWidth = Math.max(6, visibleEntries.stream()
                    .mapToInt(e -> stripColors(e.getPlayerName()).length())
                    .max().orElse(6));
            int maxTypeWidth = Math.max(7, visibleEntries.stream()
                    .mapToInt(e -> stripColors(e.getRegistrationType().getDisplayName()).length())
                    .max().orElse(7));
            int maxAddedByWidth = Math.max(8, visibleEntries.stream()
                    .mapToInt(e -> stripColors(e.getAddedBy() != null ? e.getAddedBy() : "N/A").length())
                    .max().orElse(8));
            
            msg.sendNoPrefix(sender, "list-header", 
                    placeholders("server", plugin.getPluginConfig().getServerName(),
                            "count", String.valueOf(entries.size())));
            
            for (WhitelistEntry entry : visibleEntries) {
                String player = padRight(entry.getPlayerName(), maxPlayerWidth);
                String type = padRight(entry.getRegistrationType().getDisplayName(), maxTypeWidth);
                String addedBy = padRight(entry.getAddedBy() != null ? entry.getAddedBy() : "N/A", maxAddedByWidth);
                
                msg.sendNoPrefix(sender, "list-entry",
                        placeholders("player", player,
                                "type", type,
                                "added_by", addedBy));
            }
            
            msg.sendNoPrefix(sender, "list-footer",
                    placeholders("page", String.valueOf(currentPage),
                            "total", String.valueOf(totalPages)));
        });
    }
    
    private PlayerIdentifier parsePlayerIdentifier(String type, String input, CommandSender sender, String command) {
        if ("name".equals(type)) {
            String playerName = input;
            UUID uuid = plugin.getWhitelistManager().resolvePlayerUuid(playerName);
            return new PlayerIdentifier(uuid, playerName);
        } else if ("uuid".equals(type)) {
            try {
                String uuidStr = formatUuidString(input);
                UUID uuid = UUID.fromString(uuidStr);
                String playerName = getPlayerNameFromUuid(uuid);
                if (playerName == null) {
                    playerName = uuid.toString();
                }
                return new PlayerIdentifier(uuid, playerName);
            } catch (IllegalArgumentException e) {
                String usage = command.equals("add") ? "/wlt add uuid <uuid> [причина]" : "/wlt remove uuid <uuid>";
                msg.send(sender, "general.invalid-args", placeholders("usage", usage));
                return null;
            }
        } else {
            String usage = command.equals("add") ? "/wlt add <name|uuid> <значение> [причина]" : "/wlt remove <name|uuid> <значение>";
            msg.send(sender, "general.invalid-args", placeholders("usage", usage));
            return null;
        }
    }
    
    private String formatUuidString(String input) {
        if (input.length() == 32) {
            return input.substring(0, 8) + "-" + 
                   input.substring(8, 12) + "-" + 
                   input.substring(12, 16) + "-" + 
                   input.substring(16, 20) + "-" + 
                   input.substring(20, 32);
        }
        return input;
    }
    
    private String getPlayerNameFromUuid(UUID uuid) {
        var online = plugin.getServer().getPlayer(uuid);
        if (online != null) {
            return online.getName();
        }
        
        var offline = plugin.getServer().getOfflinePlayer(uuid);
        if (offline.hasPlayedBefore() || offline.isOnline()) {
            return offline.getName();
        }
        
        return null;
    }
    
    private record PlayerIdentifier(UUID uuid, String playerName) {}
    
    private String stripColors(String text) {
        if (text == null) return "";
        return text.replaceAll("<[^>]+>", "");
    }
    
    private String padRight(String text, int width) {
        if (text == null) text = "";
        int plainLength = stripColors(text).length();
        if (plainLength >= width) {
            return text;
        }
        int padding = width - plainLength;
        return text + " ".repeat(padding);
    }
    
    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 1) {
            msg.send(sender, "general.invalid-args", 
                    placeholders("usage", "/wlt info <ник>"));
            return;
        }
        
        String playerName = args[0];
        var uuid = plugin.getWhitelistManager().resolvePlayerUuid(playerName);
        
        plugin.getWhitelistManager().getEntries(uuid).thenAccept(entries -> {
            if (entries.isEmpty()) {
                msg.send(sender, "info-no-entries");
                return;
            }
            
            msg.sendNoPrefix(sender, "info-header", 
                    placeholders("player", playerName));
            
            for (WhitelistEntry entry : entries) {
                msg.sendNoPrefix(sender, "info-server", 
                        placeholders("server", entry.getServerName()));
                msg.sendNoPrefix(sender, "info-type", 
                        placeholders("type", entry.getRegistrationType().getDisplayName()));
                msg.sendNoPrefix(sender, "info-reason", 
                        placeholders("reason", entry.getReason() != null ? entry.getReason() : "N/A"));
                msg.sendNoPrefix(sender, "info-added-by", 
                        placeholders("added_by", entry.getAddedBy() != null ? entry.getAddedBy() : "N/A"));
                msg.sendNoPrefix(sender, "info-date", 
                        placeholders("date", DATE_FORMAT.format(entry.getCreatedAt())));
            }
        });
    }
    
    private void handleEnable(CommandSender sender) {
        plugin.getPluginConfig().setWhitelistEnabled(true);
        msg.send(sender, "enabled");
    }
    
    private void handleDisable(CommandSender sender) {
        plugin.getPluginConfig().setWhitelistEnabled(false);
        msg.send(sender, "disabled");
    }
    
    private void handleReload(CommandSender sender) {
        plugin.reload();
        msg.send(sender, "general.reload-success");
    }
    
    private void handleAutoAdd(CommandSender sender, String[] args) {
        if (args.length < 1) {
            boolean current = plugin.getPluginConfig().isAutoAdd();
            String statusKey = current ? "autoadd-status-on" : "autoadd-status-off";
            msg.send(sender, statusKey);
            return;
        }
        
        String action = args[0].toLowerCase();
        boolean enable;
        
        if (action.equals("on") || action.equals("enable") || action.equals("true")) {
            enable = true;
        } else if (action.equals("off") || action.equals("disable") || action.equals("false")) {
            enable = false;
        } else {
            msg.send(sender, "general.invalid-args",
                    placeholders("usage", "/wlt autoadd <on|off>"));
            return;
        }
        
        boolean current = plugin.getPluginConfig().isAutoAdd();
        if (current == enable) {
            String alreadyKey = enable ? "autoadd-already-on" : "autoadd-already-off";
            msg.send(sender, alreadyKey);
            return;
        }
        
        String senderName = sender.getName();
        pendingConfirmations.put(senderName, enable);
        
        String confirmKey = enable ? "autoadd-confirm-enable" : "autoadd-confirm-disable";
        msg.send(sender, confirmKey);
        msg.send(sender, "autoadd-confirm-hint");
    }
    
    private void handleConfirm(CommandSender sender) {
        String senderName = sender.getName();
        Boolean pendingAction = pendingConfirmations.remove(senderName);
        
        if (pendingAction == null) {
            msg.send(sender, "autoadd-no-confirmation");
            return;
        }
        
        plugin.getPluginConfig().setAutoAdd(pendingAction);
        
        if (pendingAction) {
            msg.send(sender, "autoadd-enabled");
        } else {
            msg.send(sender, "autoadd-disabled");
        }
    }
    
    private void sendHelp(CommandSender sender) {
        msg.sendNoPrefix(sender, "help.header");
        msg.sendNoPrefix(sender, "help.wl-add");
        msg.sendNoPrefix(sender, "help.wl-remove");
        msg.sendNoPrefix(sender, "help.wl-list");
        msg.sendNoPrefix(sender, "help.wl-info");
        msg.sendNoPrefix(sender, "help.wl-on");
        msg.sendNoPrefix(sender, "help.wl-off");
        msg.sendNoPrefix(sender, "help.wl-autoadd");
        msg.sendNoPrefix(sender, "help.wl-reload");
    }
    
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
            @NotNull String alias, @NotNull String[] args) {
        
        if (!sender.hasPermission("whitelist.admin")) {
            return List.of();
        }
        
        if (args.length == 1) {
            return filterCompletions(args[0], 
                    "add", "remove", "list", "info", "on", "off", "autoadd", "confirm", "reload", "help");
        }
        
        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("add") || sub.equals("remove")) {
                return filterCompletions(args[1], "name", "uuid");
            }
            if (sub.equals("autoadd")) {
                return filterCompletions(args[1], "on", "off");
            }
            if (sub.equals("info")) {
                return null;
            }
        }
        
        if (args.length == 3) {
            String sub = args[0].toLowerCase();
            if ((sub.equals("add") || sub.equals("remove")) && 
                (args[1].toLowerCase().equals("name") || args[1].toLowerCase().equals("uuid"))) {
                return null;
            }
        }
        
        return List.of();
    }
    
    private List<String> filterCompletions(String input, String... completions) {
        String lower = input.toLowerCase();
        return Arrays.stream(completions)
                .filter(c -> c.toLowerCase().startsWith(lower))
                .collect(Collectors.toList());
    }
}

