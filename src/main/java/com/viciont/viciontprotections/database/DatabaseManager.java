package com.viciont.viciontprotections.database;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.viciont.viciontprotections.api.*;
import com.viciont.viciontprotections.models.Protection;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.java.JavaPlugin;

/** Tablas propias, escrituras atómicas y un único trabajador: nunca se consulta SQL al moverse. */
public final class DatabaseManager implements AutoCloseable {
  private static final Gson JSON = new Gson();
  private final HikariDataSource pool;
  private final String prefix;
  private final ExecutorService worker =
      new ThreadPoolExecutor(
          1,
          1,
          0,
          TimeUnit.SECONDS,
          new ArrayBlockingQueue<>(1024),
          task -> {
            Thread thread = new Thread(task, "ViciontProtections-SQL");
            thread.setDaemon(true);
            return thread;
          });

  public DatabaseManager(HikariDataSource pool, String prefix) throws SQLException {
    if (!prefix.matches("[a-zA-Z_][a-zA-Z0-9_]{0,30}"))
      throw new IllegalArgumentException("Prefijo SQL no válido.");
    this.pool = pool;
    this.prefix = prefix;
    initialize();
  }

  public static DatabaseManager open(JavaPlugin plugin) throws SQLException, IOException {
    ConfigurationSection config = plugin.getConfig().getConfigurationSection("database");
    String type =
        config == null ? "sqlite" : config.getString("type", "sqlite").toLowerCase(Locale.ROOT);
    HikariConfig settings = new HikariConfig();
    settings.setPoolName("ViciontProtections");
    settings.setConnectionTimeout(8000);
    settings.setMaximumPoolSize(2);
    settings.setMinimumIdle(1);
    settings.setAutoCommit(true);
    Path folder = plugin.getDataFolder().toPath().toAbsolutePath().normalize();
    Files.createDirectories(folder);
    String filename =
        config == null ? "protections.db" : config.getString("filename", "protections.db");
    Path sqlite = folder.resolve(filename).normalize();
    if (!sqlite.startsWith(folder))
      throw new IllegalArgumentException(
          "El archivo SQLite debe estar dentro de la carpeta del plugin.");
    if (type.equals("sqlite")) {
      if (Files.exists(sqlite) && !Files.exists(folder.resolve("protections-pre-2.0.db")))
        Files.copy(sqlite, folder.resolve("protections-pre-2.0.db"));
      settings.setJdbcUrl("jdbc:sqlite:" + sqlite);
      settings.setDriverClassName("org.sqlite.JDBC");
      settings.setMaximumPoolSize(1);
      settings.setConnectionInitSql("PRAGMA foreign_keys=ON");
      settings.addDataSourceProperty("busy_timeout", "8000");
    } else if (type.equals("mysql")) {
      String host = config.getString("mysql.host", "localhost"),
          database = config.getString("mysql.database", "viciontprotections");
      if (!host.matches("[a-zA-Z0-9.:-]+") || !database.matches("[a-zA-Z0-9_-]+"))
        throw new IllegalArgumentException("Host o base de datos MySQL no válidos.");
      int port = config.getInt("mysql.port", 3306);
      if (port < 1 || port > 65535) throw new IllegalArgumentException("Puerto MySQL no válido.");
      settings.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database);
      settings.setDriverClassName("com.mysql.cj.jdbc.Driver");
      settings.setUsername(config.getString("mysql.username", "viciont"));
      settings.setPassword(config.getString("mysql.password", ""));
      settings.addDataSourceProperty("sslMode", config.getString("mysql.ssl-mode", "PREFERRED"));
      settings.addDataSourceProperty("characterEncoding", "UTF-8");
      settings.addDataSourceProperty("connectionTimeZone", "UTC");
    } else throw new IllegalArgumentException("database.type debe ser sqlite o mysql.");
    HikariDataSource pool = new HikariDataSource(settings);
    try {
      DatabaseManager store =
          new DatabaseManager(
              pool, config == null ? "vp_" : config.getString("table-prefix", "vp_"));
      try (Connection connection =
          type.equals("sqlite")
              ? DriverManager.getConnection("jdbc:sqlite:" + sqlite)
              : pool.getConnection()) {
        store.migrateLegacy(connection);
      }
      if (type.equals("mysql") && Files.isRegularFile(sqlite)) {
        try (Connection local = DriverManager.getConnection("jdbc:sqlite:" + sqlite)) {
          int copied = store.importSqlite(local);
          if (copied > 0)
            plugin
                .getLogger()
                .info(
                    "Se copiaron "
                        + copied
                        + " protecciones de "
                        + filename
                        + " a MySQL. El archivo SQLite se conserva como copia.");
          store.migrateLegacy(local);
        }
      }
      return store;
    } catch (SQLException | RuntimeException failure) {
      pool.close();
      throw failure;
    }
  }

  private String table(String name) {
    return prefix + name;
  }

  private void initialize() throws SQLException {
    try (Connection connection = pool.getConnection();
        Statement sql = connection.createStatement()) {
      // MySQL/MariaDB: utf8mb4 para nombres con acentos u otros alfabetos aunque el servidor use otro.
      String product = connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
      String charset =
          product.contains("mysql") || product.contains("mariadb")
              ? " DEFAULT CHARSET=utf8mb4"
              : "";
      sql.executeUpdate(
          "CREATE TABLE IF NOT EXISTS "
              + table("meta")
              + " (meta_key VARCHAR(100) PRIMARY KEY, meta_value VARCHAR(255) NOT NULL)"
              + charset);
      sql.executeUpdate(
          "CREATE TABLE IF NOT EXISTS "
              + table("protections")
              + " (id VARCHAR(36) PRIMARY KEY, name VARCHAR(80) NOT NULL, world_name VARCHAR(255)"
              + " NOT NULL, min_x INT NOT NULL, min_y INT NOT NULL, min_z INT NOT NULL, max_x INT"
              + " NOT NULL, max_y INT NOT NULL, max_z INT NOT NULL, primary_owner VARCHAR(36) NOT"
              + " NULL, flags TEXT NOT NULL, anchor_x INT, anchor_y INT, anchor_z INT, material"
              + " VARCHAR(80), width INT, depth INT, height INT, created_at BIGINT NOT NULL)"
              + charset);
      sql.executeUpdate(
          "CREATE TABLE IF NOT EXISTS "
              + table("roles")
              + " (protection_id VARCHAR(36) NOT NULL, player_uuid VARCHAR(36) NOT NULL, role"
              + " VARCHAR(10) NOT NULL, PRIMARY KEY (protection_id, player_uuid), FOREIGN KEY"
              + " (protection_id) REFERENCES "
              + table("protections")
              + " (id) ON DELETE CASCADE)"
              + charset);
    }
  }

  public List<Protection> loadAll() throws SQLException {
    try (Connection connection = pool.getConnection()) {
      return loadAll(connection);
    }
  }

  /**
   * Al pasar de SQLite a MySQL, copia las protecciones 2.x del archivo local si la base MySQL aún
   * no tiene ninguna. Devuelve cuántas se copiaron.
   */
  int importSqlite(Connection local) throws SQLException {
    if (!hasTable(local, table("protections")) || !hasTable(local, table("roles"))) return 0;
    if (!loadAll().isEmpty()) return 0;
    List<Protection> found = loadAll(local);
    if (found.isEmpty()) return 0;
    // Marcas como legacy_v1: evitan que la importación de 1.x se repita sobre los datos copiados.
    Map<String, String> meta = new HashMap<>();
    if (hasTable(local, table("meta")))
      try (Statement sql = local.createStatement();
          ResultSet rows = sql.executeQuery("SELECT meta_key,meta_value FROM " + table("meta"))) {
        while (rows.next()) meta.put(rows.getString(1), rows.getString(2));
      }
    meta.remove("sqlite_import");
    transaction(
        connection -> {
          for (Protection protection : found) write(connection, protection);
          try (PreparedStatement sql =
              connection.prepareStatement(
                  "INSERT INTO " + table("meta") + " (meta_key,meta_value) VALUES (?,?)")) {
            for (var entry : meta.entrySet()) {
              sql.setString(1, entry.getKey());
              sql.setString(2, entry.getValue());
              sql.executeUpdate();
            }
          }
          try (PreparedStatement sql =
              connection.prepareStatement(
                  "INSERT INTO " + table("meta") + " (meta_key,meta_value) VALUES ('sqlite_import',?)")) {
            sql.setString(1, Integer.toString(found.size()));
            sql.executeUpdate();
          }
        });
    return found.size();
  }

  private List<Protection> loadAll(Connection connection) throws SQLException {
    {
      Map<UUID, Set<UUID>> owners = new HashMap<>(), members = new HashMap<>();
      try (Statement sql = connection.createStatement();
          ResultSet rows = sql.executeQuery("SELECT * FROM " + table("roles"))) {
        while (rows.next()) {
          Map<UUID, Set<UUID>> target = rows.getString("role").equals("OWNER") ? owners : members;
          target
              .computeIfAbsent(
                  UUID.fromString(rows.getString("protection_id")), key -> new HashSet<>())
              .add(UUID.fromString(rows.getString("player_uuid")));
        }
      }
      List<Protection> result = new ArrayList<>();
      try (Statement sql = connection.createStatement();
          ResultSet rows =
              sql.executeQuery(
                  "SELECT * FROM " + table("protections") + " ORDER BY created_at,id")) {
        while (rows.next()) {
          UUID id = UUID.fromString(rows.getString("id"));
          Bounds bounds =
              new Bounds(
                  rows.getInt("min_x"),
                  rows.getInt("min_y"),
                  rows.getInt("min_z"),
                  rows.getInt("max_x"),
                  rows.getInt("max_y"),
                  rows.getInt("max_z"));
          Anchor anchor =
              rows.getObject("anchor_x") == null
                  ? null
                  : new Anchor(
                      rows.getInt("anchor_x"),
                      rows.getInt("anchor_y"),
                      rows.getInt("anchor_z"),
                      new ProtectionBlock(
                          Material.valueOf(rows.getString("material")),
                          rows.getInt("width"),
                          rows.getInt("depth"),
                          rows.getInt("height")));
          Map<String, String> flags =
              JSON.fromJson(
                  rows.getString("flags"), new TypeToken<Map<String, String>>() {}.getType());
          result.add(
              new Protection(
                  id,
                  rows.getString("name"),
                  rows.getString("world_name"),
                  bounds,
                  UUID.fromString(rows.getString("primary_owner")),
                  owners.getOrDefault(id, Set.of()),
                  members.getOrDefault(id, Set.of()),
                  flags,
                  anchor,
                  rows.getLong("created_at")));
        }
      }
      return List.copyOf(result);
    }
  }

  public CompletableFuture<Void> save(Protection protection) {
    return submit(() -> transaction(connection -> write(connection, protection)));
  }

  public CompletableFuture<Void> delete(UUID id) {
    return submit(() -> transaction(connection -> delete(connection, id)));
  }

  private CompletableFuture<Void> submit(SqlRunnable work) {
    try {
      return CompletableFuture.runAsync(
          () -> {
            try {
              work.run();
            } catch (SQLException failure) {
              throw new CompletionException(failure);
            }
          },
          worker);
    } catch (RejectedExecutionException failure) {
      return CompletableFuture.failedFuture(failure);
    }
  }

  private void transaction(SqlWork work) throws SQLException {
    try (Connection connection = pool.getConnection()) {
      connection.setAutoCommit(false);
      try {
        work.run(connection);
        connection.commit();
      } catch (SQLException | RuntimeException failure) {
        connection.rollback();
        throw failure;
      } finally {
        connection.setAutoCommit(true);
      }
    }
  }

  private void delete(Connection connection, UUID id) throws SQLException {
    try (PreparedStatement roles =
            connection.prepareStatement(
                "DELETE FROM " + table("roles") + " WHERE protection_id=?");
        PreparedStatement protection =
            connection.prepareStatement("DELETE FROM " + table("protections") + " WHERE id=?")) {
      roles.setString(1, id.toString());
      roles.executeUpdate();
      protection.setString(1, id.toString());
      protection.executeUpdate();
    }
  }

  private void write(Connection connection, Protection protection) throws SQLException {
    delete(connection, protection.id());
    String query =
        "INSERT INTO "
            + table("protections")
            + " (id,name,world_name,min_x,min_y,min_z,max_x,max_y,max_z,primary_owner,flags,anchor_x,anchor_y,anchor_z,material,width,depth,height,created_at)"
            + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
    try (PreparedStatement sql = connection.prepareStatement(query)) {
      sql.setString(1, protection.id().toString());
      sql.setString(2, protection.name());
      sql.setString(3, protection.worldName());
      Bounds b = protection.bounds();
      sql.setInt(4, b.minX());
      sql.setInt(5, b.minY());
      sql.setInt(6, b.minZ());
      sql.setInt(7, b.maxX());
      sql.setInt(8, b.maxY());
      sql.setInt(9, b.maxZ());
      sql.setString(10, protection.primaryOwner().toString());
      sql.setString(11, JSON.toJson(protection.flags()));
      Anchor a = protection.anchor();
      if (a == null)
        for (int i = 12; i <= 18; i++) sql.setNull(i, i == 15 ? Types.VARCHAR : Types.INTEGER);
      else {
        sql.setInt(12, a.x());
        sql.setInt(13, a.y());
        sql.setInt(14, a.z());
        sql.setString(15, a.block().material().name());
        sql.setInt(16, a.block().width());
        sql.setInt(17, a.block().depth());
        sql.setInt(18, a.block().height());
      }
      sql.setLong(19, protection.createdAt());
      sql.executeUpdate();
    }
    try (PreparedStatement sql =
        connection.prepareStatement(
            "INSERT INTO " + table("roles") + " (protection_id,player_uuid,role) VALUES (?,?,?)")) {
      for (String role : List.of("OWNER", "MEMBER"))
        for (UUID uuid : role.equals("OWNER") ? protection.owners() : protection.members()) {
          sql.setString(1, protection.id().toString());
          sql.setString(2, uuid.toString());
          sql.setString(3, role);
          sql.addBatch();
        }
      sql.executeBatch();
    }
  }

  /**
   * Importación idempotente de 1.x. Conserva tablas, IDs derivados y los límites inclusivos
   * originales.
   */
  public void migrateLegacy(Connection legacy) throws SQLException {
    if (prefix.equals("") || !hasTable(legacy, "protections")) return;
    try (Connection connection = pool.getConnection();
        PreparedStatement sql =
            connection.prepareStatement(
                "SELECT meta_value FROM " + table("meta") + " WHERE meta_key='legacy_v1'")) {
      try (ResultSet rows = sql.executeQuery()) {
        if (rows.next()) return;
      }
    }
    List<Protection> imports = new ArrayList<>();
    try (Statement sql = legacy.createStatement();
        ResultSet rows = sql.executeQuery("SELECT * FROM protections ORDER BY id")) {
      while (rows.next()) {
        int oldId = rows.getInt("id"), size = rows.getInt("size"), half = size / 2;
        String world = rows.getString("world");
        UUID id =
            UUID.nameUUIDFromBytes(
                ("viciont-legacy:" + world + ":" + oldId).getBytes(StandardCharsets.UTF_8));
        Set<UUID> owners = new HashSet<>(), members = new HashSet<>();
        UUID primary = null;
        try (PreparedStatement roleQuery =
            legacy.prepareStatement(
                "SELECT player_uuid,is_primary FROM protection_owners WHERE protection_id=?")) {
          roleQuery.setInt(1, oldId);
          try (ResultSet role = roleQuery.executeQuery()) {
            while (role.next()) {
              UUID uuid = UUID.fromString(role.getString(1));
              owners.add(uuid);
              if (role.getBoolean(2)) {
                if (primary != null && !primary.equals(uuid))
                  throw new SQLException(
                      "La protección antigua #" + oldId + " tiene varios dueños principales.");
                primary = uuid;
              }
            }
          }
        }
        if (primary == null)
          throw new SQLException(
              "La protección antigua #"
                  + oldId
                  + " no tiene dueño principal; se conservan sus datos para corregirla.");
        owners.remove(primary);
        try (PreparedStatement roleQuery =
            legacy.prepareStatement(
                "SELECT player_uuid FROM protection_members WHERE protection_id=?")) {
          roleQuery.setInt(1, oldId);
          try (ResultSet role = roleQuery.executeQuery()) {
            while (role.next()) members.add(UUID.fromString(role.getString(1)));
          }
        }
        members.remove(primary);
        members.removeAll(owners);
        int x = rows.getInt("x"), y = rows.getInt("y"), z = rows.getInt("z");
        String name = rows.getString("name");
        if (name == null || name.isBlank()) name = "Protección #" + oldId;
        imports.add(
            new Protection(
                id,
                name,
                world,
                new Bounds(
                    x - half, Short.MIN_VALUE, z - half, x + half, Short.MAX_VALUE, z + half),
                primary,
                owners,
                members,
                Map.of(),
                new Anchor(x, y, z, ProtectionBlock.square(Material.REDSTONE_BLOCK, size)),
                System.currentTimeMillis()));
      }
    }
    if (imports.isEmpty()) return;
    transaction(
        connection -> {
          for (Protection protection : imports) write(connection, protection);
          try (PreparedStatement sql =
              connection.prepareStatement(
                  "INSERT INTO "
                      + table("meta")
                      + " (meta_key,meta_value) VALUES ('legacy_v1',?)")) {
            sql.setString(1, Integer.toString(imports.size()));
            sql.executeUpdate();
          }
        });
  }

  private boolean hasTable(Connection connection, String name) throws SQLException {
    try (ResultSet rows =
        connection
            .getMetaData()
            .getTables(connection.getCatalog(), null, name, new String[] {"TABLE"})) {
      return rows.next();
    }
  }

  @Override
  public void close() {
    worker.shutdown();
    try {
      if (!worker.awaitTermination(15, TimeUnit.SECONDS)) worker.shutdownNow();
    } catch (InterruptedException failure) {
      Thread.currentThread().interrupt();
      worker.shutdownNow();
    }
    pool.close();
  }

  @FunctionalInterface
  private interface SqlWork {
    void run(Connection connection) throws SQLException;
  }

  @FunctionalInterface
  private interface SqlRunnable {
    void run() throws SQLException;
  }
}
