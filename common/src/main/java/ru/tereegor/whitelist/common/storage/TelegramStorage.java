package ru.tereegor.whitelist.common.storage;

import ru.tereegor.whitelist.common.model.PlayerLink;
import ru.tereegor.whitelist.common.model.RegistrationCode;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface TelegramStorage {
    
    CompletableFuture<RegistrationCode> createCode(RegistrationCode code);
    
    CompletableFuture<Optional<RegistrationCode>> getCode(String code);
    
    CompletableFuture<Optional<RegistrationCode>> getActiveCodeByTelegramId(Long telegramId);
    
    CompletableFuture<Boolean> useCode(String code, UUID playerUuid, String playerName);
    
    CompletableFuture<Integer> deleteExpiredCodes();
    
    CompletableFuture<Void> invalidateCodesForTelegramId(Long telegramId);
    
    CompletableFuture<PlayerLink> createLink(PlayerLink link);
    
    CompletableFuture<Optional<PlayerLink>> getLinkByPlayer(UUID playerUuid);
    
    CompletableFuture<Optional<PlayerLink>> getLinkByTelegramId(Long telegramId);
    
    CompletableFuture<Boolean> isPlayerLinked(UUID playerUuid);
    
    CompletableFuture<Boolean> isTelegramLinked(Long telegramId);
    
    CompletableFuture<Boolean> unlinkPlayer(UUID playerUuid);
    
    CompletableFuture<List<PlayerLink>> getAllLinks();
}

