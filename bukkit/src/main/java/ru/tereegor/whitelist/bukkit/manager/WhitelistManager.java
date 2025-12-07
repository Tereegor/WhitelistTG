package ru.tereegor.whitelist.bukkit.manager;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import ru.tereegor.whitelist.bukkit.WhitelistPlugin;
import ru.tereegor.whitelist.common.model.PlayerLink;
import ru.tereegor.whitelist.common.model.RegistrationCode;
import ru.tereegor.whitelist.common.model.RegistrationType;
import ru.tereegor.whitelist.common.model.WhitelistEntry;
import ru.tereegor.whitelist.common.storage.SqlStorage;
import ru.tereegor.whitelist.common.util.CodeGenerator;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

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
        
        WhitelistEntry entry = WhitelistEntry.builder()
                .playerUuid(playerUuid)
                .playerName(playerName)
                .serverName(getServerName())
                .registrationType(type)
                .reason(reason)
                .addedBy(addedBy)
                .createdAt(Instant.now())
                .active(true)
                .build();
        
        return storage.addEntry(entry);
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
        return generateCode(telegramId, telegramUsername, null);
    }
    
    public CompletableFuture<RegistrationCode> generateCode(Long telegramId, String telegramUsername, String playerName) {
        return storage.invalidateCodesForTelegramId(telegramId)
                .thenCompose(v -> {
                    int expirationMinutes = plugin.getPluginConfig().getCodeExpirationMinutes();
                    
                    RegistrationCode code = RegistrationCode.builder()
                            .code(CodeGenerator.generateFormatted())
                            .telegramId(telegramId)
                            .telegramUsername(telegramUsername)
                            .playerName(playerName)
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
        return storage.getCode(code).thenCompose(optCode -> {
            if (optCode.isEmpty()) {
                return CompletableFuture.completedFuture(
                        new ActivationResult(false, "code.invalid", null));
            }
            
            RegistrationCode regCode = optCode.get();
            
            if (!regCode.isValid()) {
                return CompletableFuture.completedFuture(
                        new ActivationResult(false, "code.invalid", null));
            }
            
            return isWhitelisted(playerUuid).thenCompose(whitelisted -> {
                if (whitelisted) {
                    return CompletableFuture.completedFuture(
                            new ActivationResult(false, "code.already-whitelisted", null));
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
                                (regCode.getTelegramUsername() != null ? regCode.getTelegramUsername() : regCode.getTelegramId());
                        
                        return addPlayer(playerUuid, playerName, RegistrationType.TELEGRAM_CODE, 
                                reason, "Telegram").thenApply(entry -> 
                                    new ActivationResult(true, "code.success", entry));
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
    
    public record ActivationResult(boolean success, String messageKey, WhitelistEntry entry) {}
}

