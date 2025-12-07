package ru.tereegor.whitelist.common.model;

public enum RegistrationType {
    TELEGRAM_CODE,
    
    INVITE,
    
    MANUAL,
    
    IMPORT;
    
    public String getDisplayName() {
        return switch (this) {
            case TELEGRAM_CODE -> "Telegram";
            case INVITE -> "Приглашение";
            case MANUAL -> "Вручную";
            case IMPORT -> "Импорт";
        };
    }
}

