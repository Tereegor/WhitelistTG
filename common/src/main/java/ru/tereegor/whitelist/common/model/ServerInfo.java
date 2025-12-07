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
public class ServerInfo {
    
    private String name;
    
    private String displayName;
    
    private boolean whitelistEnabled;
    
    private Instant lastHeartbeat;
    
    public boolean isOnline() {
        if (lastHeartbeat == null) {
            return false;
        }
        return Instant.now().minusSeconds(60).isBefore(lastHeartbeat);
    }
}

