package ru.tereegor.whitelist.common.storage;

public enum StorageType {
    YAML,
    
    H2,
    
    SQLITE,
    
    MYSQL,
    
    MARIADB;
    
    public static StorageType fromString(String value) {
        if (value == null || value.isEmpty()) {
            return H2;
        }
        try {
            return valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return H2;
        }
    }
}

