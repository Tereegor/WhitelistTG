package ru.tereegor.whitelist.velocity.command;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import ru.tereegor.whitelist.velocity.WhitelistVelocityPlugin;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class WhitelistVelocityCommand implements SimpleCommand {
    
    private static final LegacyComponentSerializer SERIALIZER = LegacyComponentSerializer.legacyAmpersand();
    
    private final WhitelistVelocityPlugin plugin;
    
    @Override
    public void execute(Invocation invocation) {
        CommandSource source = invocation.source();
        String[] args = invocation.arguments();
        
        if (!source.hasPermission("whitelist.admin")) {
            source.sendMessage(Component.text("У вас нет прав для этой команды.", NamedTextColor.RED));
            return;
        }
        
        if (args.length == 0) {
            sendHelp(source);
            return;
        }
        
        switch (args[0].toLowerCase()) {
            case "check" -> handleCheck(source, args);
            case "reload" -> handleReload(source);
            case "cache" -> handleCache(source, args);
            case "help" -> sendHelp(source);
            default -> source.sendMessage(Component.text("Неизвестная команда. Используйте /wlv help", NamedTextColor.RED));
        }
    }
    
    private void handleCheck(CommandSource source, String[] args) {
        if (args.length < 2) {
            source.sendMessage(Component.text("Использование: /wlv check <игрок>", NamedTextColor.RED));
            return;
        }
        
        String playerName = args[1];
        
        plugin.getProxy().getPlayer(playerName).ifPresentOrElse(
                player -> showPlayerServers(source, player, playerName),
                () -> source.sendMessage(Component.text("Игрок не в сети: " + playerName, NamedTextColor.RED))
        );
    }
    
    private void showPlayerServers(CommandSource source, Player player, String playerName) {
        plugin.getStorage().getPlayerServers(player.getUniqueId()).thenAccept(servers -> {
            if (servers.isEmpty()) {
                source.sendMessage(SERIALIZER.deserialize(
                        "&cИгрок &e" + playerName + " &cне в вайтлисте ни одного сервера."));
            } else {
                source.sendMessage(SERIALIZER.deserialize(
                        "&6Игрок &e" + playerName + " &6в вайтлисте серверов:"));
                servers.forEach(server -> 
                        source.sendMessage(SERIALIZER.deserialize("&7- &e" + server)));
            }
        });
    }
    
    private void handleReload(CommandSource source) {
        plugin.reload();
        source.sendMessage(Component.text("Конфигурация перезагружена!", NamedTextColor.GREEN));
    }
    
    private void handleCache(CommandSource source, String[] args) {
        if (args.length < 2) {
            source.sendMessage(Component.text("Использование: /wlv cache <clear|player|server>", NamedTextColor.RED));
            return;
        }
        
        switch (args[1].toLowerCase()) {
            case "clear" -> {
                plugin.getCache().invalidateAll();
                source.sendMessage(Component.text("Кэш очищен!", NamedTextColor.GREEN));
            }
            case "player" -> handleCachePlayer(source, args);
            case "server" -> handleCacheServer(source, args);
            default -> source.sendMessage(Component.text("Неизвестное действие: " + args[1], NamedTextColor.RED));
        }
    }
    
    private void handleCachePlayer(CommandSource source, String[] args) {
        if (args.length < 3) {
            source.sendMessage(Component.text("Укажите имя игрока", NamedTextColor.RED));
            return;
        }
        
        plugin.getProxy().getPlayer(args[2]).ifPresentOrElse(
                player -> {
                    plugin.getCache().invalidatePlayer(player.getUniqueId());
                    source.sendMessage(Component.text("Кэш игрока очищен!", NamedTextColor.GREEN));
                },
                () -> source.sendMessage(Component.text("Игрок не найден", NamedTextColor.RED))
        );
    }
    
    private void handleCacheServer(CommandSource source, String[] args) {
        if (args.length < 3) {
            source.sendMessage(Component.text("Укажите имя сервера", NamedTextColor.RED));
            return;
        }
        
        plugin.getCache().invalidateServer(args[2]);
        source.sendMessage(Component.text("Кэш сервера очищен!", NamedTextColor.GREEN));
    }
    
    private void sendHelp(CommandSource source) {
        source.sendMessage(SERIALIZER.deserialize("&6=== WhitelistTG Velocity ==="));
        source.sendMessage(SERIALIZER.deserialize("&e/wlv check <игрок> &7- Проверить игрока"));
        source.sendMessage(SERIALIZER.deserialize("&e/wlv cache <clear|player|server> &7- Управление кэшем"));
        source.sendMessage(SERIALIZER.deserialize("&e/wlv reload &7- Перезагрузить конфиг"));
    }
    
    @Override
    public CompletableFuture<List<String>> suggestAsync(Invocation invocation) {
        String[] args = invocation.arguments();
        
        if (args.length <= 1) {
            String prefix = args.length == 0 ? "" : args[0].toLowerCase();
            return CompletableFuture.completedFuture(
                    filterCompletions(prefix, "check", "cache", "reload", "help"));
        }
        
        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            String prefix = args[1].toLowerCase();
            
            return switch (sub) {
                case "check" -> CompletableFuture.completedFuture(
                        plugin.getProxy().getAllPlayers().stream()
                                .map(Player::getUsername)
                                .filter(s -> s.toLowerCase().startsWith(prefix))
                                .collect(Collectors.toList()));
                case "cache" -> CompletableFuture.completedFuture(
                        filterCompletions(prefix, "clear", "player", "server"));
                default -> CompletableFuture.completedFuture(List.of());
            };
        }
        
        return CompletableFuture.completedFuture(List.of());
    }
    
    private List<String> filterCompletions(String prefix, String... options) {
        return java.util.Arrays.stream(options)
                .filter(s -> s.startsWith(prefix))
                .collect(Collectors.toList());
    }
    
    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("whitelist.admin");
    }
}
