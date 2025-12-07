package ru.tereegor.whitelist.velocity.command;

import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import ru.tereegor.whitelist.velocity.WhitelistVelocityPlugin;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class WhitelistVelocityCommand implements SimpleCommand {
    
    private final WhitelistVelocityPlugin plugin;
    private final LegacyComponentSerializer serializer = LegacyComponentSerializer.legacyAmpersand();
    
    public WhitelistVelocityCommand(WhitelistVelocityPlugin plugin) {
        this.plugin = plugin;
    }
    
    @Override
    public void execute(Invocation invocation) {
        var source = invocation.source();
        String[] args = invocation.arguments();
        
        if (!source.hasPermission("whitelist.admin")) {
            source.sendMessage(Component.text("У вас нет прав для этой команды.", NamedTextColor.RED));
            return;
        }
        
        if (args.length == 0) {
            sendHelp(source);
            return;
        }
        
        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "servers" -> handleServers(source);
            case "check" -> handleCheck(source, args);
            case "reload" -> handleReload(source);
            case "cache" -> handleCache(source, args);
            case "help" -> sendHelp(source);
            default -> source.sendMessage(Component.text("Неизвестная команда. Используйте /wlv help", NamedTextColor.RED));
        }
    }
    
    private void handleServers(com.velocitypowered.api.command.CommandSource source) {
        plugin.getStorage().getAllServers().thenAccept(servers -> {
            if (servers.isEmpty()) {
                source.sendMessage(Component.text("Нет зарегистрированных серверов.", NamedTextColor.YELLOW));
                return;
            }
            
            source.sendMessage(serializer.deserialize("&6=== Зарегистрированные серверы ==="));
            for (var server : servers) {
                String status = server.isOnline() ? "&a●" : "&c●";
                String whitelist = server.isWhitelistEnabled() ? "&aВкл" : "&cВыкл";
                source.sendMessage(serializer.deserialize(String.format(
                        "%s &e%s &8| &7Вайтлист: %s", 
                        status, server.getName(), whitelist)));
            }
        });
    }
    
    private void handleCheck(com.velocitypowered.api.command.CommandSource source, String[] args) {
        if (args.length < 2) {
            source.sendMessage(Component.text("Использование: /wlv check <игрок> [сервер]", NamedTextColor.RED));
            return;
        }
        
        String playerName = args[1];
        String serverName = args.length > 2 ? args[2] : null;
        
        var optPlayer = plugin.getProxy().getPlayer(playerName);
        if (optPlayer.isEmpty()) {
            source.sendMessage(Component.text("Игрок не в сети: " + playerName, NamedTextColor.RED));
            return;
        }
        
        Player player = optPlayer.get();
        
        if (serverName != null) {
            plugin.getCache().isWhitelisted(player.getUniqueId(), serverName).thenAccept(whitelisted -> {
                String msg = whitelisted ? 
                        "&aИгрок &e%s &aв вайтлисте сервера &6%s" :
                        "&cИгрок &e%s &cНЕ в вайтлисте сервера &6%s";
                source.sendMessage(serializer.deserialize(String.format(msg, playerName, serverName)));
            });
        } else {
            plugin.getStorage().getPlayerServers(player.getUniqueId()).thenAccept(servers -> {
                if (servers.isEmpty()) {
                    source.sendMessage(serializer.deserialize(
                            "&cИгрок &e" + playerName + " &cне в вайтлисте ни одного сервера."));
                } else {
                    source.sendMessage(serializer.deserialize(
                            "&6Игрок &e" + playerName + " &6в вайтлисте серверов:"));
                    for (String server : servers) {
                        source.sendMessage(serializer.deserialize("&7- &e" + server));
                    }
                }
            });
        }
    }
    
    private void handleReload(com.velocitypowered.api.command.CommandSource source) {
        plugin.reload();
        source.sendMessage(Component.text("Конфигурация перезагружена!", NamedTextColor.GREEN));
    }
    
    private void handleCache(com.velocitypowered.api.command.CommandSource source, String[] args) {
        if (args.length < 2) {
            source.sendMessage(Component.text("Использование: /wlv cache <clear|player|server>", NamedTextColor.RED));
            return;
        }
        
        String action = args[1].toLowerCase();
        
        switch (action) {
            case "clear" -> {
                plugin.getCache().invalidateAll();
                source.sendMessage(Component.text("Кэш очищен!", NamedTextColor.GREEN));
            }
            case "player" -> {
                if (args.length < 3) {
                    source.sendMessage(Component.text("Укажите имя игрока", NamedTextColor.RED));
                    return;
                }
                var optPlayer = plugin.getProxy().getPlayer(args[2]);
                if (optPlayer.isPresent()) {
                    plugin.getCache().invalidatePlayer(optPlayer.get().getUniqueId());
                    source.sendMessage(Component.text("Кэш игрока очищен!", NamedTextColor.GREEN));
                } else {
                    source.sendMessage(Component.text("Игрок не найден", NamedTextColor.RED));
                }
            }
            case "server" -> {
                if (args.length < 3) {
                    source.sendMessage(Component.text("Укажите имя сервера", NamedTextColor.RED));
                    return;
                }
                plugin.getCache().invalidateServer(args[2]);
                source.sendMessage(Component.text("Кэш сервера очищен!", NamedTextColor.GREEN));
            }
            default -> source.sendMessage(Component.text("Неизвестное действие: " + action, NamedTextColor.RED));
        }
    }
    
    private void sendHelp(com.velocitypowered.api.command.CommandSource source) {
        source.sendMessage(serializer.deserialize("&6=== WhitelistTG Velocity ==="));
        source.sendMessage(serializer.deserialize("&e/wlv servers &7- Список серверов"));
        source.sendMessage(serializer.deserialize("&e/wlv check <игрок> [сервер] &7- Проверить игрока"));
        source.sendMessage(serializer.deserialize("&e/wlv cache <clear|player|server> &7- Управление кэшем"));
        source.sendMessage(serializer.deserialize("&e/wlv reload &7- Перезагрузить конфиг"));
    }
    
    @Override
    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        String[] args = invocation.arguments();
        
        if (args.length <= 1) {
            String prefix = args.length == 0 ? "" : args[0].toLowerCase();
            return CompletableFuture.completedFuture(
                    List.of("servers", "check", "cache", "reload", "help").stream()
                            .filter(s -> s.startsWith(prefix))
                            .collect(Collectors.toList())
            );
        }
        
        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            String prefix = args[1].toLowerCase();
            
            if (sub.equals("check")) {
                return CompletableFuture.completedFuture(
                        plugin.getProxy().getAllPlayers().stream()
                                .map(Player::getUsername)
                                .filter(s -> s.toLowerCase().startsWith(prefix))
                                .collect(Collectors.toList())
                );
            }
            
            if (sub.equals("cache")) {
                return CompletableFuture.completedFuture(
                        List.of("clear", "player", "server").stream()
                                .filter(s -> s.startsWith(prefix))
                                .collect(Collectors.toList())
                );
            }
        }
        
        if (args.length == 3 && args[0].equalsIgnoreCase("check")) {
            return plugin.getStorage().getAllServers().thenApply(servers ->
                    servers.stream()
                            .map(s -> s.getName())
                            .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                            .collect(Collectors.toList())
            );
        }
        
        return CompletableFuture.completedFuture(List.of());
    }
    
    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("whitelist.admin");
    }
}

