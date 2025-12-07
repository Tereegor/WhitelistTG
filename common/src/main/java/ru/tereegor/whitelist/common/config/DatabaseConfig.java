package ru.tereegor.whitelist.common.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.tereegor.whitelist.common.storage.StorageType;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DatabaseConfig {
    
    @Builder.Default
    private StorageType storageType = StorageType.H2;
    
    @Builder.Default
    private String host = "localhost";
    
    @Builder.Default
    private int port = 3306;
    
    @Builder.Default
    private String database = "whitelist";
    
    @Builder.Default
    private String username = "root";
    
    @Builder.Default
    private String password = "";
    
    @Builder.Default
    private int maximumPoolSize = 10;
    
    @Builder.Default
    private int minimumIdle = 2;
    
    @Builder.Default
    private long connectionTimeout = 30000;
    
    @Builder.Default
    private long idleTimeout = 600000;
    
    @Builder.Default
    private long maxLifetime = 1800000;
    
    private String dataFolderPath;
    
    public String getJdbcUrl() {
        return switch (storageType) {
            case H2 -> "jdbc:h2:file:" + dataFolderPath + "/database;MODE=MySQL;TRACE_LEVEL_FILE=0;AUTO_SERVER=FALSE";
            case SQLITE -> "jdbc:sqlite:" + dataFolderPath + "/database.db";
            case MYSQL -> "jdbc:mysql://" + host + ":" + port + "/" + database + 
                    "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8";
            case MARIADB -> "jdbc:mariadb://" + host + ":" + port + "/" + database + 
                    "?serverTimezone=UTC&characterEncoding=UTF-8";
            case YAML -> null;
        };
    }
    
    public String getDriverClassName() {
        return switch (storageType) {
            case H2 -> "org.h2.Driver";
            case SQLITE -> "org.sqlite.JDBC";
            case MYSQL -> "com.mysql.cj.jdbc.Driver";
            case MARIADB -> "org.mariadb.jdbc.Driver";
            case YAML -> null;
        };
    }
}

