package ru.tereegor.whitelist.common.storage;

import ru.tereegor.whitelist.common.model.ServerInfo;
import ru.tereegor.whitelist.common.model.WhitelistEntry;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface WhitelistStorage {
    
    CompletableFuture<Void> initialize();
    
    CompletableFuture<Void> close();
    
    CompletableFuture<WhitelistEntry> addEntry(WhitelistEntry entry);
    
    CompletableFuture<Boolean> removeEntry(UUID playerUuid, String serverName);
    
    CompletableFuture<Optional<WhitelistEntry>> getEntry(UUID playerUuid, String serverName);
    
    CompletableFuture<List<WhitelistEntry>> getEntriesByPlayer(UUID playerUuid);
    
    CompletableFuture<List<WhitelistEntry>> getEntriesByServer(String serverName);
    
    CompletableFuture<List<WhitelistEntry>> getAllActiveEntries();
    
    CompletableFuture<Boolean> isWhitelisted(UUID playerUuid, String serverName);
    
    CompletableFuture<Boolean> isWhitelistedByName(String playerName, String serverName);
    
    CompletableFuture<Boolean> isNicknameTaken(String playerName);
    
    CompletableFuture<Boolean> updateEntry(WhitelistEntry entry);
    
    CompletableFuture<List<String>> getPlayerServers(UUID playerUuid);
    
    CompletableFuture<Void> registerServer(ServerInfo server);
    
    CompletableFuture<Void> updateServerHeartbeat(String serverName);
    
    CompletableFuture<List<ServerInfo>> getAllServers();
    
    CompletableFuture<Optional<ServerInfo>> getServer(String serverName);
    
    CompletableFuture<Void> updateServerWhitelistStatus(String serverName, boolean enabled);
    
    CompletableFuture<Integer> getEntryCount(String serverName);
    
    CompletableFuture<Integer> getTotalEntryCount();
}

