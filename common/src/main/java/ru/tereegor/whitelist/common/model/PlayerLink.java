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
public class PlayerLink {
    
    private Long id;
    
    private UUID playerUuid;
    
    private String playerName;
    
    private Long telegramId;
    
    private String telegramUsername;
    
    private Instant linkedAt;
    
    private boolean active;
}

