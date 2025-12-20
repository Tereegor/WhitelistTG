package ru.tereegor.whitelist.bukkit.command;

import lombok.RequiredArgsConstructor;
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
import java.util.*;
import java.util.stream.Collectors;

import static ru.tereegor.whitelist.bukkit.manager.MessageManager.placeholder;
import static ru.tereegor.whitelist.bukkit.manager.MessageManager.placeholders;

@RequiredArgsConstructor
public class WhitelistCommand implements CommandExecutor, TabCompleter {
    
    private static final DateTimeFormatter DATE_FORMAT = 
            DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm").withZone(ZoneId.systemDefault());
    private static final int PAGE_SIZE = 10;
    private static final Set<String> ENABLE_KEYWORDS = Set.of("on", "enable", "true");
    private static final Set<String> DISABLE_KEYWORDS = Set.of("off", "disable", "false");
    
    private final WhitelistPlugin plugin;
    private final Map<String, Boolean> pendingConfirmations = new HashMap<>();
    
    private MessageManager msg() {
        return plugin.getMessageManager();
    }
    
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, 
            @NotNull String label, @NotNull String[] args) {
        
        if (!sender.hasPermission("whitelist.admin")) {
            msg().send(sender, "general.no-permission");
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
            default -> msg().send(sender, "general.invalid-args", placeholders("usage", "/wlt help"));
        }
        
        return true;
    }
    
    private void handleAdd(CommandSender sender, String[] args) {
        if (args.length < 2) {
            msg().send(sender, "general.invalid-args", 
                    placeholders("usage", "/wlt add <name|uuid> <значение> [причина]"));
            return;
        }
        
        String type = args[0].toLowerCase();
        String input = args[1];
        String reason = args.length > 2 
                ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) 
                : "Добавлен вручную";
        
        parsePlayerIdentifier(type, input).ifPresentOrElse(
                id -> addPlayerToWhitelist(sender, id, reason),
                () -> sendInvalidArgsMessage(sender, "add")
        );
    }
    
    private void addPlayerToWhitelist(CommandSender sender, PlayerIdentifier id, String reason) {
        plugin.getWhitelistManager().isWhitelisted(id.uuid(), id.playerName())
                .thenAccept(whitelisted -> {
                    if (whitelisted) {
                        msg().send(sender, "already-added", placeholder("player", id.playerName()));
                        return;
                    }
                    
                    plugin.getWhitelistManager().addPlayer(id.uuid(), id.playerName(), 
                            RegistrationType.MANUAL, reason, sender.getName())
                            .thenAccept(entry -> msg().send(sender, "added", 
                                    placeholders("player", id.playerName(), 
                                            "server", plugin.getPluginConfig().getServerName())));
                });
    }
    
    private void handleRemove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            msg().send(sender, "general.invalid-args", 
                    placeholders("usage", "/wlt remove <name|uuid> <значение>"));
            return;
        }
        
        String type = args[0].toLowerCase();
        String input = args[1];
        
        parsePlayerIdentifier(type, input).ifPresentOrElse(
                id -> removePlayerFromWhitelist(sender, id),
                () -> sendInvalidArgsMessage(sender, "remove")
        );
    }
    
    private void removePlayerFromWhitelist(CommandSender sender, PlayerIdentifier id) {
        plugin.getWhitelistManager().removePlayer(id.uuid())
                .thenAccept(removed -> {
                    String key = removed ? "removed" : "not-found";
                    msg().send(sender, key, placeholders("player", id.playerName(), 
                            "server", plugin.getPluginConfig().getServerName()));
                });
    }
    
    private void handleList(CommandSender sender, String[] args) {
        int page = args.length > 0 ? parsePageNumber(args[0]) : 1;
        
        plugin.getWhitelistManager().getAllEntries().thenAccept(entries -> {
            if (entries.isEmpty()) {
                msg().send(sender, "list-empty");
                return;
            }
            
            int totalPages = (int) Math.ceil((double) entries.size() / PAGE_SIZE);
            int startIndex = (page - 1) * PAGE_SIZE;
            
            if (startIndex >= entries.size()) {
                msg().send(sender, "list-empty");
                return;
            }
            
            List<WhitelistEntry> visibleEntries = entries.subList(
                    startIndex, Math.min(startIndex + PAGE_SIZE, entries.size()));
            
            displayEntryList(sender, entries.size(), visibleEntries, page, totalPages);
        });
    }
    
    private void displayEntryList(CommandSender sender, int totalCount, 
            List<WhitelistEntry> entries, int page, int totalPages) {
        
        int maxPlayerWidth = calculateMaxWidth(entries, e -> e.getPlayerName(), 6);
        int maxTypeWidth = calculateMaxWidth(entries, e -> e.getRegistrationType().getDisplayName(), 7);
        int maxAddedByWidth = calculateMaxWidth(entries, e -> e.getAddedBy(), 8);
        
        msg().sendNoPrefix(sender, "list-header", 
                placeholders("server", plugin.getPluginConfig().getServerName(),
                        "count", String.valueOf(totalCount)));
        
        for (WhitelistEntry entry : entries) {
            msg().sendNoPrefix(sender, "list-entry",
                    placeholders("player", padRight(entry.getPlayerName(), maxPlayerWidth),
                            "type", padRight(entry.getRegistrationType().getDisplayName(), maxTypeWidth),
                            "added_by", padRight(Optional.ofNullable(entry.getAddedBy()).orElse("N/A"), maxAddedByWidth)));
        }
        
        msg().sendNoPrefix(sender, "list-footer",
                placeholders("page", String.valueOf(page), "total", String.valueOf(totalPages)));
    }
    
    private int calculateMaxWidth(List<WhitelistEntry> entries, 
            java.util.function.Function<WhitelistEntry, String> extractor, int minWidth) {
        return Math.max(minWidth, entries.stream()
                .mapToInt(e -> stripColors(extractor.apply(e)).length())
                .max().orElse(minWidth));
    }
    
    private Optional<PlayerIdentifier> parsePlayerIdentifier(String type, String input) {
        return switch (type) {
            case "name" -> Optional.of(new PlayerIdentifier(
                    plugin.getWhitelistManager().resolvePlayerUuid(input), input));
            case "uuid" -> parseUuidIdentifier(input);
            default -> Optional.empty();
        };
    }
    
    private Optional<PlayerIdentifier> parseUuidIdentifier(String input) {
        try {
            String uuidStr = formatUuidString(input);
            UUID uuid = UUID.fromString(uuidStr);
            String playerName = getPlayerNameFromUuid(uuid);
            return Optional.of(new PlayerIdentifier(uuid, playerName != null ? playerName : uuid.toString()));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
    
    private void sendInvalidArgsMessage(CommandSender sender, String command) {
        String usage = switch (command) {
            case "add" -> "/wlt add <name|uuid> <значение> [причина]";
            case "remove" -> "/wlt remove <name|uuid> <значение>";
            default -> "/wlt help";
        };
        msg().send(sender, "general.invalid-args", placeholders("usage", usage));
    }
    
    private String formatUuidString(String input) {
        if (input.length() == 32) {
            return "%s-%s-%s-%s-%s".formatted(
                    input.substring(0, 8), input.substring(8, 12),
                    input.substring(12, 16), input.substring(16, 20),
                    input.substring(20, 32));
        }
        return input;
    }
    
    private String getPlayerNameFromUuid(UUID uuid) {
        var online = plugin.getServer().getPlayer(uuid);
        if (online != null) return online.getName();
        
        var offline = plugin.getServer().getOfflinePlayer(uuid);
        return (offline.hasPlayedBefore() || offline.isOnline()) ? offline.getName() : null;
    }
    
    private record PlayerIdentifier(UUID uuid, String playerName) {}
    
    private String stripColors(String text) {
        return text != null ? text.replaceAll("<[^>]+>", "") : "";
    }
    
    private String padRight(String text, int width) {
        String safeText = text != null ? text : "";
        int plainLength = stripColors(safeText).length();
        return plainLength >= width ? safeText : safeText + " ".repeat(width - plainLength);
    }
    
    private int parsePageNumber(String input) {
        try {
            return Math.max(1, Integer.parseInt(input));
        } catch (NumberFormatException e) {
            return 1;
        }
    }
    
    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length < 1) {
            msg().send(sender, "general.invalid-args", placeholders("usage", "/wlt info <ник>"));
            return;
        }
        
        String playerName = args[0];
        var uuid = plugin.getWhitelistManager().resolvePlayerUuid(playerName);
        
        plugin.getWhitelistManager().getEntries(uuid).thenAccept(entries -> {
            if (entries.isEmpty()) {
                msg().send(sender, "info-no-entries");
                return;
            }
            
            msg().sendNoPrefix(sender, "info-header", placeholders("player", playerName));
            entries.forEach(entry -> displayEntryInfo(sender, entry));
        });
    }
    
    private void displayEntryInfo(CommandSender sender, WhitelistEntry entry) {
        msg().sendNoPrefix(sender, "info-server", placeholders("server", entry.getServerName()));
        msg().sendNoPrefix(sender, "info-type", 
                placeholders("type", entry.getRegistrationType().getDisplayName()));
        msg().sendNoPrefix(sender, "info-reason", 
                placeholders("reason", Optional.ofNullable(entry.getReason()).orElse("N/A")));
        msg().sendNoPrefix(sender, "info-added-by", 
                placeholders("added_by", Optional.ofNullable(entry.getAddedBy()).orElse("N/A")));
        msg().sendNoPrefix(sender, "info-date", 
                placeholders("date", DATE_FORMAT.format(entry.getCreatedAt())));
    }
    
    private void handleEnable(CommandSender sender) {
        plugin.getPluginConfig().setWhitelistEnabled(true);
        msg().send(sender, "enabled");
    }
    
    private void handleDisable(CommandSender sender) {
        plugin.getPluginConfig().setWhitelistEnabled(false);
        msg().send(sender, "disabled");
    }
    
    private void handleReload(CommandSender sender) {
        plugin.reload();
        msg().send(sender, "general.reload-success");
    }
    
    private void handleAutoAdd(CommandSender sender, String[] args) {
        if (args.length < 1) {
            String statusKey = plugin.getPluginConfig().isAutoAdd() ? "autoadd-status-on" : "autoadd-status-off";
            msg().send(sender, statusKey);
            return;
        }
        
        String action = args[0].toLowerCase();
        Optional<Boolean> enableOpt = parseEnableAction(action);
        
        if (enableOpt.isEmpty()) {
            msg().send(sender, "general.invalid-args", placeholders("usage", "/wlt autoadd <on|off>"));
            return;
        }
        
        boolean enable = enableOpt.get();
        boolean current = plugin.getPluginConfig().isAutoAdd();
        
        if (current == enable) {
            msg().send(sender, enable ? "autoadd-already-on" : "autoadd-already-off");
            return;
        }
        
        pendingConfirmations.put(sender.getName(), enable);
        msg().send(sender, enable ? "autoadd-confirm-enable" : "autoadd-confirm-disable");
        msg().send(sender, "autoadd-confirm-hint");
    }
    
    private Optional<Boolean> parseEnableAction(String action) {
        if (ENABLE_KEYWORDS.contains(action)) return Optional.of(true);
        if (DISABLE_KEYWORDS.contains(action)) return Optional.of(false);
        return Optional.empty();
    }
    
    private void handleConfirm(CommandSender sender) {
        Boolean pendingAction = pendingConfirmations.remove(sender.getName());
        
        if (pendingAction == null) {
            msg().send(sender, "autoadd-no-confirmation");
            return;
        }
        
        plugin.getPluginConfig().setAutoAdd(pendingAction);
        msg().send(sender, pendingAction ? "autoadd-enabled" : "autoadd-disabled");
    }
    
    private void sendHelp(CommandSender sender) {
        List.of("help.header", "help.wl-add", "help.wl-remove", "help.wl-list", 
                "help.wl-info", "help.wl-on", "help.wl-off", "help.wl-autoadd", "help.wl-reload")
                .forEach(key -> msg().sendNoPrefix(sender, key));
    }
    
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
            @NotNull String alias, @NotNull String[] args) {
        
        if (!sender.hasPermission("whitelist.admin")) {
            return List.of();
        }
        
        return switch (args.length) {
            case 1 -> filterCompletions(args[0], 
                    "add", "remove", "list", "info", "on", "off", "autoadd", "confirm", "reload", "help");
            case 2 -> getSecondArgCompletions(args[0].toLowerCase(), args[1]);
            case 3 -> getThirdArgCompletions(args[0].toLowerCase(), args[1].toLowerCase());
            default -> List.of();
        };
    }
    
    private List<String> getSecondArgCompletions(String subCommand, String input) {
        return switch (subCommand) {
            case "add", "remove" -> filterCompletions(input, "name", "uuid");
            case "autoadd" -> filterCompletions(input, "on", "off");
            case "info" -> null;
            default -> List.of();
        };
    }
    
    private List<String> getThirdArgCompletions(String subCommand, String secondArg) {
        if ((subCommand.equals("add") || subCommand.equals("remove")) 
                && (secondArg.equals("name") || secondArg.equals("uuid"))) {
            return null;
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
