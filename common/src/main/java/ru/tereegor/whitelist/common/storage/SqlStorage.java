package ru.tereegor.whitelist.common.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import ru.tereegor.whitelist.common.config.DatabaseConfig;
import ru.tereegor.whitelist.common.model.*;

import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class SqlStorage implements WhitelistStorage, TelegramStorage {
    
    private final DatabaseConfig config;
    private final ExecutorService executor;
    private HikariDataSource dataSource;
    private Consumer<String> logger;
    
    public SqlStorage(DatabaseConfig config) {
        this(config, msg -> System.out.println("[WhitelistStorage] " + msg));
    }
    
    public SqlStorage(DatabaseConfig config, Consumer<String> logger) {
        this.config = config;
        this.executor = Executors.newFixedThreadPool(4);
        this.logger = logger;
    }
    
    @Override
    public CompletableFuture<Void> initialize() {
        return CompletableFuture.runAsync(() -> {
            try {
                HikariConfig hikariConfig = new HikariConfig();
                hikariConfig.setJdbcUrl(config.getJdbcUrl());
                hikariConfig.setDriverClassName(config.getDriverClassName());
                
                if (config.getStorageType() != StorageType.H2 && config.getStorageType() != StorageType.SQLITE) {
                    hikariConfig.setUsername(config.getUsername());
                    hikariConfig.setPassword(config.getPassword());
                }
                
                hikariConfig.setMaximumPoolSize(config.getMaximumPoolSize());
                hikariConfig.setMinimumIdle(config.getMinimumIdle());
                hikariConfig.setConnectionTimeout(config.getConnectionTimeout());
                hikariConfig.setIdleTimeout(config.getIdleTimeout());
                hikariConfig.setMaxLifetime(config.getMaxLifetime());
                hikariConfig.setPoolName("WhitelistPool");
                
                this.dataSource = new HikariDataSource(hikariConfig);
                
                createTables();
                logger.accept("Database initialized successfully with " + config.getStorageType());
            } catch (Exception e) {
                throw new RuntimeException("Failed to initialize database", e);
            }
        }, executor);
    }
    
    private void createTables() throws SQLException {
        boolean isSqlite = config.getStorageType() == StorageType.SQLITE;
        
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            
            String entriesTable = isSqlite ? """
                CREATE TABLE IF NOT EXISTS whitelist_entries (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    player_uuid TEXT NOT NULL,
                    player_name TEXT NOT NULL,
                    server_name TEXT NOT NULL,
                    registration_type TEXT DEFAULT 'MANUAL',
                    reason TEXT,
                    added_by TEXT,
                    inviter_telegram_id INTEGER NULL,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    expires_at DATETIME NULL,
                    active INTEGER DEFAULT 1,
                    UNIQUE (player_uuid, server_name)
                )
            """ : """
                CREATE TABLE IF NOT EXISTS whitelist_entries (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    player_uuid VARCHAR(36) NOT NULL,
                    player_name VARCHAR(32) NOT NULL,
                    server_name VARCHAR(64) NOT NULL,
                    registration_type VARCHAR(32) DEFAULT 'MANUAL',
                    reason TEXT,
                    added_by VARCHAR(64),
                    inviter_telegram_id BIGINT NULL,
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    expires_at TIMESTAMP NULL,
                    active BOOLEAN DEFAULT TRUE,
                    UNIQUE KEY unique_player_server (player_uuid, server_name)
                )
            """;
            stmt.execute(entriesTable);
            
            String serversTable = isSqlite ? """
                CREATE TABLE IF NOT EXISTS whitelist_servers (
                    name TEXT PRIMARY KEY,
                    display_name TEXT,
                    whitelist_enabled INTEGER DEFAULT 1,
                    last_heartbeat DATETIME DEFAULT CURRENT_TIMESTAMP
                )
            """ : """
                CREATE TABLE IF NOT EXISTS whitelist_servers (
                    name VARCHAR(64) PRIMARY KEY,
                    display_name VARCHAR(128),
                    whitelist_enabled BOOLEAN DEFAULT TRUE,
                    last_heartbeat TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
            """;
            stmt.execute(serversTable);
            
            String codesTable = isSqlite ? """
                CREATE TABLE IF NOT EXISTS registration_codes (
                    code TEXT PRIMARY KEY,
                    telegram_id INTEGER NOT NULL,
                    telegram_username TEXT,
                    player_name TEXT,
                    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    expires_at DATETIME NOT NULL,
                    used INTEGER DEFAULT 0,
                    used_by_uuid TEXT,
                    used_by_name TEXT,
                    used_at DATETIME NULL
                )
            """ : """
                CREATE TABLE IF NOT EXISTS registration_codes (
                    code VARCHAR(16) PRIMARY KEY,
                    telegram_id BIGINT NOT NULL,
                    telegram_username VARCHAR(64),
                    player_name VARCHAR(32),
                    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    expires_at TIMESTAMP NOT NULL,
                    used BOOLEAN DEFAULT FALSE,
                    used_by_uuid VARCHAR(36),
                    used_by_name VARCHAR(32),
                    used_at TIMESTAMP NULL
                )
            """;
            stmt.execute(codesTable);
            
            if (!isSqlite) {
                try {
                    stmt.execute("ALTER TABLE registration_codes ADD COLUMN player_name VARCHAR(32)");
                } catch (SQLException ignored) {}
            }
            
            String linksTable = isSqlite ? """
                CREATE TABLE IF NOT EXISTS player_links (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    player_uuid TEXT NOT NULL UNIQUE,
                    player_name TEXT NOT NULL,
                    telegram_id INTEGER NOT NULL UNIQUE,
                    telegram_username TEXT,
                    linked_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    active INTEGER DEFAULT 1
                )
            """ : """
                CREATE TABLE IF NOT EXISTS player_links (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    player_uuid VARCHAR(36) NOT NULL UNIQUE,
                    player_name VARCHAR(32) NOT NULL,
                    telegram_id BIGINT NOT NULL UNIQUE,
                    telegram_username VARCHAR(64),
                    linked_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    active BOOLEAN DEFAULT TRUE
                )
            """;
            stmt.execute(linksTable);
            
            try {
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_entries_uuid ON whitelist_entries(player_uuid)");
            } catch (SQLException ignored) {}
            
            try {
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_entries_server ON whitelist_entries(server_name)");
            } catch (SQLException ignored) {}
            
            try {
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_codes_telegram ON registration_codes(telegram_id)");
            } catch (SQLException ignored) {}
            
            try {
                stmt.execute("CREATE INDEX IF NOT EXISTS idx_links_telegram ON player_links(telegram_id)");
            } catch (SQLException ignored) {}
        }
    }
    
    @Override
    public CompletableFuture<Void> close() {
        return CompletableFuture.runAsync(() -> {
            if (dataSource != null && !dataSource.isClosed()) {
                dataSource.close();
            }
            executor.shutdown();
        });
    }
    
    @Override
    public CompletableFuture<WhitelistEntry> addEntry(WhitelistEntry entry) {
        return CompletableFuture.supplyAsync(() -> {
            boolean isSqlite = config.getStorageType() == StorageType.SQLITE;
            String sql = isSqlite ? """
                INSERT INTO whitelist_entries 
                (player_uuid, player_name, server_name, registration_type, reason, added_by, inviter_telegram_id, created_at, expires_at, active)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(player_uuid, server_name) DO UPDATE SET
                player_name = excluded.player_name,
                registration_type = excluded.registration_type,
                reason = excluded.reason,
                added_by = excluded.added_by,
                inviter_telegram_id = excluded.inviter_telegram_id,
                created_at = excluded.created_at,
                expires_at = excluded.expires_at,
                active = excluded.active
            """ : """
                INSERT INTO whitelist_entries 
                (player_uuid, player_name, server_name, registration_type, reason, added_by, inviter_telegram_id, created_at, expires_at, active)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE 
                player_name = VALUES(player_name),
                registration_type = VALUES(registration_type),
                reason = VALUES(reason),
                added_by = VALUES(added_by),
                inviter_telegram_id = VALUES(inviter_telegram_id),
                created_at = VALUES(created_at),
                expires_at = VALUES(expires_at),
                active = VALUES(active)
            """;
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                
                ps.setString(1, entry.getPlayerUuid().toString());
                ps.setString(2, entry.getPlayerName());
                ps.setString(3, entry.getServerName());
                ps.setString(4, entry.getRegistrationType().name());
                ps.setString(5, entry.getReason());
                ps.setString(6, entry.getAddedBy());
                if (entry.getInviterTelegramId() != null) {
                    ps.setLong(7, entry.getInviterTelegramId());
                } else {
                    ps.setNull(7, Types.BIGINT);
                }
                ps.setTimestamp(8, Timestamp.from(entry.getCreatedAt() != null ? entry.getCreatedAt() : Instant.now()));
                ps.setTimestamp(9, entry.getExpiresAt() != null ? Timestamp.from(entry.getExpiresAt()) : null);
                ps.setBoolean(10, entry.isActive());
                
                ps.executeUpdate();
                
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        entry.setId(rs.getLong(1));
                    }
                }
                
                return entry;
            } catch (SQLException e) {
                throw new RuntimeException("Failed to add whitelist entry", e);
            }
        }, executor);
    }
    
    @Override
    public CompletableFuture<Boolean> removeEntry(UUID playerUuid, String serverName) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM whitelist_entries WHERE player_uuid = ? AND server_name = ?";
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                ps.setString(1, playerUuid.toString());
                ps.setString(2, serverName);
                
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                throw new RuntimeException("Failed to remove whitelist entry", e);
            }
        }, executor);
    }
    
    @Override
    public CompletableFuture<Optional<WhitelistEntry>> getEntry(UUID playerUuid, String serverName) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM whitelist_entries WHERE player_uuid = ? AND server_name = ?";
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                ps.setString(1, playerUuid.toString());
                ps.setString(2, serverName);
                
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapEntry(rs));
                    }
                }
                return Optional.empty();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to get whitelist entry", e);
            }
        }, executor);
    }
    
    @Override
    public CompletableFuture<List<WhitelistEntry>> getEntriesByPlayer(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM whitelist_entries WHERE player_uuid = ?";
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                ps.setString(1, playerUuid.toString());
                
                List<WhitelistEntry> entries = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        entries.add(mapEntry(rs));
                    }
                }
                return entries;
            } catch (SQLException e) {
                throw new RuntimeException("Failed to get entries by player", e);
            }
        }, executor);
    }
    
    @Override
    public CompletableFuture<List<WhitelistEntry>> getEntriesByServer(String serverName) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM whitelist_entries WHERE server_name = ? AND active = TRUE";
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                ps.setString(1, serverName);
                
                List<WhitelistEntry> entries = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        entries.add(mapEntry(rs));
                    }
                }
                return entries;
            } catch (SQLException e) {
                throw new RuntimeException("Failed to get entries by server", e);
            }
        }, executor);
    }
    
    @Override
    public CompletableFuture<List<WhitelistEntry>> getAllActiveEntries() {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM whitelist_entries WHERE active = TRUE";
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                
                List<WhitelistEntry> entries = new ArrayList<>();
                while (rs.next()) {
                    entries.add(mapEntry(rs));
                }
                return entries;
            } catch (SQLException e) {
                throw new RuntimeException("Failed to get all active entries", e);
            }
        }, executor);
    }
    
    @Override
    public CompletableFuture<Boolean> isWhitelisted(UUID playerUuid, String serverName) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                SELECT 1 FROM whitelist_entries 
                WHERE player_uuid = ? AND server_name = ? AND active = TRUE
                AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP)
            """;
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                ps.setString(1, playerUuid.toString());
                ps.setString(2, serverName);
                
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to check whitelist status", e);
            }
        }, executor);
    }
    
    @Override
    public CompletableFuture<Boolean> isWhitelistedByName(String playerName, String serverName) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                SELECT 1 FROM whitelist_entries 
                WHERE LOWER(player_name) = LOWER(?) AND server_name = ? AND active = TRUE
                AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP)
            """;
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                ps.setString(1, playerName);
                ps.setString(2, serverName);
                
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to check whitelist status by name", e);
            }
        }, executor);
    }
    
    @Override
    public CompletableFuture<Boolean> isNicknameTaken(String playerName) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                SELECT 1 FROM whitelist_entries 
                WHERE LOWER(player_name) = LOWER(?) AND active = TRUE
                LIMIT 1
            """;
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                ps.setString(1, playerName);
                
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to check if nickname is taken", e);
            }
        }, executor);
    }
    
    @Override
    public CompletableFuture<Boolean> updateEntry(WhitelistEntry entry) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                UPDATE whitelist_entries 
                SET player_name = ?, reason = ?, added_by = ?, expires_at = ?, active = ?
                WHERE player_uuid = ? AND server_name = ?
            """;
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                ps.setString(1, entry.getPlayerName());
                ps.setString(2, entry.getReason());
                ps.setString(3, entry.getAddedBy());
                ps.setTimestamp(4, entry.getExpiresAt() != null ? Timestamp.from(entry.getExpiresAt()) : null);
                ps.setBoolean(5, entry.isActive());
                ps.setString(6, entry.getPlayerUuid().toString());
                ps.setString(7, entry.getServerName());
                
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                throw new RuntimeException("Failed to update whitelist entry", e);
            }
        }, executor);
    }
    
    @Override
    public CompletableFuture<List<String>> getPlayerServers(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                SELECT server_name FROM whitelist_entries 
                WHERE player_uuid = ? AND active = TRUE
                AND (expires_at IS NULL OR expires_at > CURRENT_TIMESTAMP)
            """;
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                ps.setString(1, playerUuid.toString());
                
                List<String> servers = new ArrayList<>();
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        servers.add(rs.getString("server_name"));
                    }
                }
                return servers;
            } catch (SQLException e) {
                throw new RuntimeException("Failed to get player servers", e);
            }
        }, executor);
    }
    
    @Override
    public CompletableFuture<Void> registerServer(ServerInfo server) {
        return CompletableFuture.runAsync(() -> {
            boolean isSqlite = config.getStorageType() == StorageType.SQLITE;
            String sql = isSqlite ? """
                INSERT INTO whitelist_servers (name, display_name, whitelist_enabled, last_heartbeat)
                VALUES (?, ?, ?, ?)
                ON CONFLICT(name) DO UPDATE SET
                display_name = excluded.display_name,
                whitelist_enabled = excluded.whitelist_enabled,
                last_heartbeat = excluded.last_heartbeat
            """ : """
                INSERT INTO whitelist_servers (name, display_name, whitelist_enabled, last_heartbeat)
                VALUES (?, ?, ?, ?)
                ON DUPLICATE KEY UPDATE 
                display_name = VALUES(display_name),
                whitelist_enabled = VALUES(whitelist_enabled),
                last_heartbeat = VALUES(last_heartbeat)
            """;
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                ps.setString(1, server.getName());
                ps.setString(2, server.getDisplayName());
                ps.setBoolean(3, server.isWhitelistEnabled());
                ps.setTimestamp(4, Timestamp.from(Instant.now()));
                
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to register server", e);
            }
        }, executor);
    }
    
    @Override
    public CompletableFuture<Void> updateServerHeartbeat(String serverName) {
        return CompletableFuture.runAsync(() -> {
            String sql = "UPDATE whitelist_servers SET last_heartbeat = ? WHERE name = ?";
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                ps.setTimestamp(1, Timestamp.from(Instant.now()));
                ps.setString(2, serverName);
                
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to update server heartbeat", e);
            }
        }, executor);
    }
    
    @Override
    public CompletableFuture<List<ServerInfo>> getAllServers() {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM whitelist_servers";
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                
                List<ServerInfo> servers = new ArrayList<>();
                while (rs.next()) {
                    servers.add(mapServer(rs));
                }
                return servers;
            } catch (SQLException e) {
                throw new RuntimeException("Failed to get all servers", e);
            }
        }, executor);
    }
    
    @Override
    public CompletableFuture<Optional<ServerInfo>> getServer(String serverName) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM whitelist_servers WHERE name = ?";
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                ps.setString(1, serverName);
                
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapServer(rs));
                    }
                }
                return Optional.empty();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to get server", e);
            }
        }, executor);
    }
    
    @Override
    public CompletableFuture<Void> updateServerWhitelistStatus(String serverName, boolean enabled) {
        return CompletableFuture.runAsync(() -> {
            String sql = "UPDATE whitelist_servers SET whitelist_enabled = ? WHERE name = ?";
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                ps.setBoolean(1, enabled);
                ps.setString(2, serverName);
                
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to update server whitelist status", e);
            }
        }, executor);
    }
    
    @Override
    public CompletableFuture<Integer> getEntryCount(String serverName) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM whitelist_entries WHERE server_name = ? AND active = TRUE";
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                ps.setString(1, serverName);
                
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt(1);
                    }
                }
                return 0;
            } catch (SQLException e) {
                throw new RuntimeException("Failed to get entry count", e);
            }
        }, executor);
    }
    
    @Override
    public CompletableFuture<Integer> getTotalEntryCount() {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT COUNT(*) FROM whitelist_entries WHERE active = TRUE";
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                
                if (rs.next()) {
                    return rs.getInt(1);
                }
                return 0;
            } catch (SQLException e) {
                throw new RuntimeException("Failed to get total entry count", e);
            }
        }, executor);
    }
    
    private WhitelistEntry mapEntry(ResultSet rs) throws SQLException {
        Long inviterTgId = rs.getLong("inviter_telegram_id");
        if (rs.wasNull()) inviterTgId = null;
        
        String regType = rs.getString("registration_type");
        RegistrationType registrationType = regType != null ? 
                RegistrationType.valueOf(regType) : RegistrationType.MANUAL;
        
        return WhitelistEntry.builder()
                .id(rs.getLong("id"))
                .playerUuid(UUID.fromString(rs.getString("player_uuid")))
                .playerName(rs.getString("player_name"))
                .serverName(rs.getString("server_name"))
                .registrationType(registrationType)
                .reason(rs.getString("reason"))
                .addedBy(rs.getString("added_by"))
                .inviterTelegramId(inviterTgId)
                .createdAt(rs.getTimestamp("created_at").toInstant())
                .expiresAt(rs.getTimestamp("expires_at") != null ? 
                        rs.getTimestamp("expires_at").toInstant() : null)
                .active(rs.getBoolean("active"))
                .build();
    }
    
    private ServerInfo mapServer(ResultSet rs) throws SQLException {
        return ServerInfo.builder()
                .name(rs.getString("name"))
                .displayName(rs.getString("display_name"))
                .whitelistEnabled(rs.getBoolean("whitelist_enabled"))
                .lastHeartbeat(rs.getTimestamp("last_heartbeat").toInstant())
                .build();
    }
    
    @Override
    public CompletableFuture<RegistrationCode> createCode(RegistrationCode code) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                INSERT INTO registration_codes 
                (code, telegram_id, telegram_username, player_name, created_at, expires_at, used)
                VALUES (?, ?, ?, ?, ?, ?, FALSE)
            """;
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                ps.setString(1, code.getCode());
                ps.setLong(2, code.getTelegramId());
                ps.setString(3, code.getTelegramUsername());
                ps.setString(4, code.getPlayerName());
                ps.setTimestamp(5, Timestamp.from(code.getCreatedAt() != null ? code.getCreatedAt() : Instant.now()));
                ps.setTimestamp(6, Timestamp.from(code.getExpiresAt()));
                
                ps.executeUpdate();
                return code;
            } catch (SQLException e) {
                throw new RuntimeException("Failed to create registration code", e);
            }
        }, executor);
    }
    
    @Override
    public CompletableFuture<Optional<RegistrationCode>> getCode(String code) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM registration_codes WHERE code = ?";
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                ps.setString(1, code);
                
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapCode(rs));
                    }
                }
                return Optional.empty();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to get registration code", e);
            }
        }, executor);
    }
    
    @Override
    public CompletableFuture<Optional<RegistrationCode>> getActiveCodeByTelegramId(Long telegramId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                SELECT * FROM registration_codes 
                WHERE telegram_id = ? AND used = FALSE AND expires_at > CURRENT_TIMESTAMP
                ORDER BY created_at DESC LIMIT 1
            """;
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                ps.setLong(1, telegramId);
                
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapCode(rs));
                    }
                }
                return Optional.empty();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to get active code by telegram id", e);
            }
        }, executor);
    }
    
    @Override
    public CompletableFuture<Boolean> useCode(String code, UUID playerUuid, String playerName) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                UPDATE registration_codes 
                SET used = TRUE, used_by_uuid = ?, used_by_name = ?, used_at = CURRENT_TIMESTAMP
                WHERE code = ? AND used = FALSE AND expires_at > CURRENT_TIMESTAMP
            """;
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                ps.setString(1, playerUuid.toString());
                ps.setString(2, playerName);
                ps.setString(3, code);
                
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                throw new RuntimeException("Failed to use registration code", e);
            }
        }, executor);
    }
    
    @Override
    public CompletableFuture<Integer> deleteExpiredCodes() {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "DELETE FROM registration_codes WHERE expires_at < CURRENT_TIMESTAMP";
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                return ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to delete expired codes", e);
            }
        }, executor);
    }
    
    @Override
    public CompletableFuture<Void> invalidateCodesForTelegramId(Long telegramId) {
        return CompletableFuture.runAsync(() -> {
            String sql = "DELETE FROM registration_codes WHERE telegram_id = ? AND used = FALSE";
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                ps.setLong(1, telegramId);
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to invalidate codes", e);
            }
        }, executor);
    }
    
    @Override
    public CompletableFuture<PlayerLink> createLink(PlayerLink link) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = """
                INSERT INTO player_links 
                (player_uuid, player_name, telegram_id, telegram_username, linked_at, active)
                VALUES (?, ?, ?, ?, ?, ?)
            """;
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                
                ps.setString(1, link.getPlayerUuid().toString());
                ps.setString(2, link.getPlayerName());
                ps.setLong(3, link.getTelegramId());
                ps.setString(4, link.getTelegramUsername());
                ps.setTimestamp(5, Timestamp.from(link.getLinkedAt() != null ? link.getLinkedAt() : Instant.now()));
                ps.setBoolean(6, link.isActive());
                
                ps.executeUpdate();
                
                try (ResultSet rs = ps.getGeneratedKeys()) {
                    if (rs.next()) {
                        link.setId(rs.getLong(1));
                    }
                }
                return link;
            } catch (SQLException e) {
                throw new RuntimeException("Failed to create player link", e);
            }
        }, executor);
    }
    
    @Override
    public CompletableFuture<Optional<PlayerLink>> getLinkByPlayer(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM player_links WHERE player_uuid = ? AND active = TRUE";
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                ps.setString(1, playerUuid.toString());
                
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapLink(rs));
                    }
                }
                return Optional.empty();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to get player link", e);
            }
        }, executor);
    }
    
    @Override
    public CompletableFuture<Optional<PlayerLink>> getLinkByTelegramId(Long telegramId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM player_links WHERE telegram_id = ? AND active = TRUE";
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                ps.setLong(1, telegramId);
                
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return Optional.of(mapLink(rs));
                    }
                }
                return Optional.empty();
            } catch (SQLException e) {
                throw new RuntimeException("Failed to get player link by telegram id", e);
            }
        }, executor);
    }
    
    @Override
    public CompletableFuture<Boolean> isPlayerLinked(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT 1 FROM player_links WHERE player_uuid = ? AND active = TRUE";
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                ps.setString(1, playerUuid.toString());
                
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to check if player is linked", e);
            }
        }, executor);
    }
    
    @Override
    public CompletableFuture<Boolean> isTelegramLinked(Long telegramId) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT 1 FROM player_links WHERE telegram_id = ? AND active = TRUE";
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                ps.setLong(1, telegramId);
                
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            } catch (SQLException e) {
                throw new RuntimeException("Failed to check if telegram is linked", e);
            }
        }, executor);
    }
    
    @Override
    public CompletableFuture<Boolean> unlinkPlayer(UUID playerUuid) {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "UPDATE player_links SET active = FALSE WHERE player_uuid = ?";
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                
                ps.setString(1, playerUuid.toString());
                
                return ps.executeUpdate() > 0;
            } catch (SQLException e) {
                throw new RuntimeException("Failed to unlink player", e);
            }
        }, executor);
    }
    
    @Override
    public CompletableFuture<List<PlayerLink>> getAllLinks() {
        return CompletableFuture.supplyAsync(() -> {
            String sql = "SELECT * FROM player_links WHERE active = TRUE";
            
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql);
                 ResultSet rs = ps.executeQuery()) {
                
                List<PlayerLink> links = new ArrayList<>();
                while (rs.next()) {
                    links.add(mapLink(rs));
                }
                return links;
            } catch (SQLException e) {
                throw new RuntimeException("Failed to get all player links", e);
            }
        }, executor);
    }
    
    private RegistrationCode mapCode(ResultSet rs) throws SQLException {
        return RegistrationCode.builder()
                .code(rs.getString("code"))
                .telegramId(rs.getLong("telegram_id"))
                .telegramUsername(rs.getString("telegram_username"))
                .playerName(rs.getString("player_name"))
                .createdAt(rs.getTimestamp("created_at").toInstant())
                .expiresAt(rs.getTimestamp("expires_at").toInstant())
                .used(rs.getBoolean("used"))
                .usedByUuid(rs.getString("used_by_uuid"))
                .usedByName(rs.getString("used_by_name"))
                .usedAt(rs.getTimestamp("used_at") != null ? rs.getTimestamp("used_at").toInstant() : null)
                .build();
    }
    
    private PlayerLink mapLink(ResultSet rs) throws SQLException {
        return PlayerLink.builder()
                .id(rs.getLong("id"))
                .playerUuid(UUID.fromString(rs.getString("player_uuid")))
                .playerName(rs.getString("player_name"))
                .telegramId(rs.getLong("telegram_id"))
                .telegramUsername(rs.getString("telegram_username"))
                .linkedAt(rs.getTimestamp("linked_at").toInstant())
                .active(rs.getBoolean("active"))
                .build();
    }
}

