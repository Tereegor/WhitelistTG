package ru.tereegor.whitelist.common.config;

import java.nio.file.Path;
import java.nio.file.Paths;

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
            case H2 -> {
                String dbPath = normalizePath(dataFolderPath + "/database");
                yield "jdbc:h2:file:" + dbPath + ";MODE=MySQL;TRACE_LEVEL_FILE=0;AUTO_SERVER=FALSE";
            }
            case SQLITE -> {
                String dbPath = normalizePath(dataFolderPath + "/database.db");
                yield "jdbc:sqlite:" + dbPath;
            }
            case MYSQL -> "jdbc:mysql://" + host + ":" + port + "/" + database +
                    "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&characterEncoding=UTF-8";
            case MARIADB -> "jdbc:mariadb://" + host + ":" + port + "/" + database +
                    "?serverTimezone=UTC&characterEncoding=UTF-8";
            case YAML -> null;
        };
    }

    private String normalizePath(String path) {
        if (path == null || path.isEmpty()) {
            return path;
        }
        try {
            Path normalizedPath = Paths.get(path).toAbsolutePath().normalize();
            return normalizedPath.toString().replace('\\', '/');
        } catch (Exception e) {
            return path.replace('\\', '/');
        }
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
