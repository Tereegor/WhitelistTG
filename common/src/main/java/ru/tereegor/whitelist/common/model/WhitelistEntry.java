package ru.tereegor.whitelist.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WhitelistEntry {
    
    private Long id;
    
    private UUID playerUuid;
    
    private String playerName;
    
    private String serverName;
    
    @Builder.Default
    private RegistrationType registrationType = RegistrationType.MANUAL;
    
    private String reason;
    
    private String addedBy;
    
    private Long inviterTelegramId;
    
    private Instant createdAt;
    
    private Instant expiresAt;
    
    private boolean active;
    
    public boolean isExpired() {
        if (expiresAt == null) {
            return false;
        }
        return Instant.now().isAfter(expiresAt);
    }
    
    public boolean isValid() {
        return active && !isExpired();
    }
}

