package ru.tereegor.whitelist.bukkit.manager;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import ru.tereegor.whitelist.bukkit.WhitelistPlugin;
import ru.tereegor.whitelist.common.model.PlayerLink;
import ru.tereegor.whitelist.common.model.RegistrationCode;
import ru.tereegor.whitelist.common.model.RegistrationType;
import ru.tereegor.whitelist.common.model.WhitelistEntry;
import ru.tereegor.whitelist.common.storage.SqlStorage;
import ru.tereegor.whitelist.common.util.CodeGenerator;

public class WhitelistManager {

    private final WhitelistPlugin plugin;
    private final SqlStorage storage;

    public WhitelistManager(WhitelistPlugin plugin, SqlStorage storage) {
        this.plugin = plugin;
        this.storage = storage;
    }

    public String getServerName() {
        return plugin.getPluginConfig().getServerName();
    }

    public CompletableFuture<Boolean> isWhitelisted(UUID playerUuid) {
        return storage.isWhitelisted(playerUuid, getServerName());
    }

    public CompletableFuture<Boolean> isWhitelisted(UUID playerUuid, String playerName) {
        String serverName = getServerName();
        return storage.isWhitelisted(playerUuid, serverName)
                .thenCompose(byUuid -> {
                    if (byUuid) {
                        return CompletableFuture.completedFuture(true);
                    }
                    return storage.isWhitelistedByName(playerName, serverName);
                });
    }

    public CompletableFuture<WhitelistEntry> addPlayer(UUID playerUuid, String playerName,
            RegistrationType type, String reason, String addedBy) {
        return addPlayerToServer(playerUuid, playerName, getServerName(), type, reason, addedBy);
    }
    
    public CompletableFuture<WhitelistEntry> addPlayerToServer(UUID playerUuid, String playerName,
            String serverName, RegistrationType type, String reason, String addedBy) {

        WhitelistEntry entry = WhitelistEntry.builder()
                .playerUuid(playerUuid)
                .playerName(playerName)
                .serverName(serverName)
                .registrationType(type)
                .reason(reason)
                .addedBy(addedBy)
                .createdAt(Instant.now())
                .active(true)
                .build();

        return storage.addEntry(entry);
    }
    
    public CompletableFuture<WhitelistEntry> addPlayerToServers(UUID playerUuid, String playerName,
            List<String> servers, RegistrationType type, String reason, String addedBy) {
        
        if (servers.isEmpty()) {
            return addPlayer(playerUuid, playerName, type, reason, addedBy);
        }
        
        CompletableFuture<WhitelistEntry> result = CompletableFuture.completedFuture(null);
        WhitelistEntry[] lastEntry = new WhitelistEntry[1];
        
        for (String server : servers) {
            result = result.thenCompose(entry -> {
                if (entry != null) lastEntry[0] = entry;
                return addPlayerToServer(playerUuid, playerName, server, type, reason, addedBy);
            });
        }
        
        return result.thenApply(entry -> entry != null ? entry : lastEntry[0]);
    }

    public CompletableFuture<WhitelistEntry> addPlayerWithInvite(UUID playerUuid, String playerName,
            String reason, String inviterName, Long inviterTelegramId) {

        WhitelistEntry entry = WhitelistEntry.builder()
                .playerUuid(playerUuid)
                .playerName(playerName)
                .serverName(getServerName())
                .registrationType(RegistrationType.INVITE)
                .reason(reason)
                .addedBy(inviterName)
                .inviterTelegramId(inviterTelegramId)
                .createdAt(Instant.now())
                .active(true)
                .build();

        return storage.addEntry(entry);
    }

    public CompletableFuture<Boolean> removePlayer(UUID playerUuid) {
        return storage.removeEntry(playerUuid, getServerName());
    }

    public CompletableFuture<Optional<WhitelistEntry>> getEntry(UUID playerUuid) {
        return storage.getEntry(playerUuid, getServerName());
    }

    public CompletableFuture<List<WhitelistEntry>> getEntries(UUID playerUuid) {
        return storage.getEntriesByPlayer(playerUuid);
    }

    public CompletableFuture<List<WhitelistEntry>> getAllEntries() {
        return storage.getEntriesByServer(getServerName());
    }

    public CompletableFuture<Integer> getEntryCount() {
        return storage.getEntryCount(getServerName());
    }

    public CompletableFuture<RegistrationCode> generateCode(Long telegramId, String telegramUsername) {
        return storage.invalidateCodesForTelegramId(telegramId)
                .thenCompose(v -> {
                    int expirationMinutes = plugin.getPluginConfig().getCodeExpirationMinutes();

                    RegistrationCode code = RegistrationCode.builder()
                            .code(CodeGenerator.generateFormatted())
                            .telegramId(telegramId)
                            .telegramUsername(telegramUsername)
                            .playerName(null)
                            .createdAt(Instant.now())
                            .expiresAt(Instant.now().plus(expirationMinutes, ChronoUnit.MINUTES))
                            .used(false)
                            .build();

                    return storage.createCode(code);
                });
    }

    public CompletableFuture<Optional<RegistrationCode>> getActiveCode(Long telegramId) {
        return storage.getActiveCodeByTelegramId(telegramId);
    }

    public CompletableFuture<ActivationResult> activateCode(String code, UUID playerUuid, String playerName) {
        if (plugin.getPluginConfig().isDebug()) {
            plugin.getLogger().info("[DEBUG] Activating code: '" + code + "' for player: " + playerName);
            plugin.getLogger().info("[DEBUG] Database path: " + plugin.getDataFolder().getAbsolutePath());
        }
        
        return storage.getCode(code).thenCompose(optCode -> {
            if (optCode.isEmpty()) {
                if (plugin.getPluginConfig().isDebug()) {
                    plugin.getLogger().info("[DEBUG] Code NOT FOUND in database: '" + code + "'");
                }
                return CompletableFuture.completedFuture(
                        new ActivationResult(false, "code.invalid", null));
            }

            RegistrationCode regCode = optCode.get();
            
            if (plugin.getPluginConfig().isDebug()) {
                plugin.getLogger().info("[DEBUG] Found code: " + regCode.getCode() + 
                        ", used=" + regCode.isUsed() + 
                        ", expired=" + regCode.isExpired() +
                        ", expiresAt=" + regCode.getExpiresAt() +
                        ", telegramId=" + regCode.getTelegramId());
            }

            if (!regCode.isValid()) {
                if (plugin.getPluginConfig().isDebug()) {
                    plugin.getLogger().info("[DEBUG] Code is invalid (used or expired)");
                }
                return CompletableFuture.completedFuture(
                        new ActivationResult(false, "code.invalid", null));
            }

            return isWhitelisted(playerUuid).thenCompose(whitelisted -> {
                if (whitelisted) {
                    return CompletableFuture.completedFuture(
                            new ActivationResult(false, "code.already-whitelisted", null));
                }

                return storage.isTelegramLinked(regCode.getTelegramId()).thenCompose(telegramLinked -> {
                    if (telegramLinked) {
                        return CompletableFuture.completedFuture(
                                new ActivationResult(false, "code.telegram-already-linked", null));
                    }

                    return storage.useCode(code, playerUuid, playerName).thenCompose(used -> {
                        if (!used) {
                            return CompletableFuture.completedFuture(
                                    new ActivationResult(false, "code.invalid", null));
                        }

                        PlayerLink link = PlayerLink.builder()
                                .playerUuid(playerUuid)
                                .playerName(playerName)
                                .telegramId(regCode.getTelegramId())
                                .telegramUsername(regCode.getTelegramUsername())
                                .linkedAt(Instant.now())
                                .active(true)
                                .build();

                        return storage.createLink(link).thenCompose(savedLink -> {
                            String reason = "Telegram: @" +
                                    (regCode.getTelegramUsername() != null ? regCode.getTelegramUsername()
                                            : regCode.getTelegramId());

                            List<String> serversToAdd = plugin.getPluginConfig().getServersToAddOnActivation();
                            return addPlayerToServers(playerUuid, playerName, serversToAdd,
                                    RegistrationType.TELEGRAM_CODE, reason, "Telegram")
                                    .thenApply(entry -> new ActivationResult(true, "code.success", entry));
                        });
                    });
                });
            });
        });
    }

    public CompletableFuture<Optional<PlayerLink>> getPlayerLink(UUID playerUuid) {
        return storage.getLinkByPlayer(playerUuid);
    }

    public CompletableFuture<Optional<PlayerLink>> getLinkByTelegram(Long telegramId) {
        return storage.getLinkByTelegramId(telegramId);
    }

    public UUID resolvePlayerUuid(String playerName) {
        var online = Bukkit.getPlayerExact(playerName);
        if (online != null) {
            return online.getUniqueId();
        }

        OfflinePlayer offline = Bukkit.getOfflinePlayer(playerName);
        if (offline.hasPlayedBefore() || offline.isOnline()) {
            return offline.getUniqueId();
        }

        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + playerName).getBytes());
    }

    public record ActivationResult(boolean success, String messageKey, WhitelistEntry entry) {
    }
}
