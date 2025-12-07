package ru.tereegor.whitelist.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegistrationCode {
    
    private String code;
    
    private Long telegramId;
    
    private String telegramUsername;
    
    private String playerName;
    
    private Instant createdAt;
    
    private Instant expiresAt;
    
    private boolean used;
    
    private String usedByUuid;
    
    private String usedByName;
    
    private Instant usedAt;
    
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }
    
    public boolean isValid() {
        return !used && !isExpired();
    }
}

