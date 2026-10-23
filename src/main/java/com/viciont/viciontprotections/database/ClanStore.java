package com.viciont.viciontprotections.database;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.viciont.viciontprotections.api.clan.*;
import com.viciont.viciontprotections.models.ClanBackup;
import com.viciont.viciontprotections.models.ClanRequest;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Siete tablas de clanes junto a las de protecciones, con el mismo prefijo. La primera vez que se
 * crean se guarda una copia de la base: {@code protections-pre-clanes.db} en SQLite o tablas
 * {@code *_pre_clanes} en MySQL. Las protecciones de los territorios siguen en las tablas de la
 * 2.x con sus propietarios y miembros, así que volver a un JAR 2.0.x no pierde zonas ni accesos.
 */
public final class ClanStore {
  private static final Gson JSON = new Gson();
  static final List<String> TABLES =
      List.of(
          "clans",
          "clan_members",
          "clan_permissions",
          "clan_territories",
          "clan_requests",
          "clan_wars",
          "clan_backups");

  private final DatabaseManager database;

  ClanStore(DatabaseManager database, Path sqliteBackup) throws SQLException {
    this.database = database;
    try (Connection connection = database.connection()) {
      if (!database.hasTable(connection, table("clans"))) backup(connection, sqliteBackup);
      create(connection);
    }
  }

  private String table(String name) {
    return database.table(name);
  }

  private void backup(Connection connection, Path sqliteBackup) throws SQLException {
    if (!database.hasTable(connection, table("protections"))) return;
    try (Statement sql = connection.createStatement()) {
      if (sqliteBackup != null) {
        if (!Files.exists(sqliteBackup))
          sql.executeUpdate("VACUUM INTO '" + sqliteBackup.toString().replace("'", "''") + "'");
        return;
      }
      for (String name : List.of("meta", "protections", "roles")) {
        String copy = table(name) + "_pre_clanes";
        if (database.hasTable(connection, table(name)) && !database.hasTable(connection, copy))
          sql.executeUpdate("CREATE TABLE " + copy + " AS SELECT * FROM " + table(name));
      }
    }
  }

  private void create(Connection connection) throws SQLException {
    String product = connection.getMetaData().getDatabaseProductName().toLowerCase(Locale.ROOT);
    String charset =
        product.contains("mysql") || product.contains("mariadb") ? " DEFAULT CHARSET=utf8mb4" : "";
    try (Statement sql = connection.createStatement()) {
      sql.executeUpdate(
          "CREATE TABLE IF NOT EXISTS "
              + table("clans")
              + " (id VARCHAR(36) PRIMARY KEY, name VARCHAR(48) NOT NULL, color VARCHAR(7) NOT"
              + " NULL, leader VARCHAR(36) NOT NULL, home VARCHAR(36), founded_at BIGINT NOT NULL,"
              + " flags TEXT NOT NULL)"
              + charset);
      sql.executeUpdate(
          "CREATE TABLE IF NOT EXISTS "
              + table("clan_members")
              + " (clan_id VARCHAR(36) NOT NULL, player_uuid VARCHAR(36) NOT NULL, clan_rank"
              + " VARCHAR(16) NOT NULL, joined_at BIGINT NOT NULL, active INT NOT NULL, PRIMARY"
              + " KEY (clan_id, player_uuid), FOREIGN KEY (clan_id) REFERENCES "
              + table("clans")
              + " (id) ON DELETE CASCADE)"
              + charset);
      sql.executeUpdate(
          "CREATE TABLE IF NOT EXISTS "
              + table("clan_permissions")
              + " (clan_id VARCHAR(36) NOT NULL, clan_rank VARCHAR(16) NOT NULL, permission"
              + " VARCHAR(48) NOT NULL, allowed INT NOT NULL, PRIMARY KEY (clan_id, clan_rank,"
              + " permission), FOREIGN KEY (clan_id) REFERENCES "
              + table("clans")
              + " (id) ON DELETE CASCADE)"
              + charset);
      sql.executeUpdate(
          "CREATE TABLE IF NOT EXISTS "
              + table("clan_territories")
              + " (protection_id VARCHAR(36) PRIMARY KEY, clan_id VARCHAR(36) NOT NULL,"
              + " contributed_by VARCHAR(36) NOT NULL, contributed_at BIGINT NOT NULL, home INT NOT"
              + " NULL, previous_owners TEXT NOT NULL, previous_members TEXT NOT NULL, FOREIGN KEY"
              + " (clan_id) REFERENCES "
              + table("clans")
              + " (id) ON DELETE CASCADE)"
              + charset);
      sql.executeUpdate(
          "CREATE TABLE IF NOT EXISTS "
              + table("clan_requests")
              + " (id VARCHAR(36) PRIMARY KEY, type VARCHAR(16) NOT NULL, clan_id VARCHAR(36) NOT"
              + " NULL, player_uuid VARCHAR(36) NOT NULL, protection_id VARCHAR(36), clan_rank"
              + " VARCHAR(16), created_by VARCHAR(36), created_at BIGINT NOT NULL, expires_at BIGINT"
              + " NOT NULL)"
              + charset);
      sql.executeUpdate(
          "CREATE TABLE IF NOT EXISTS "
              + table("clan_wars")
              + " (id VARCHAR(36) PRIMARY KEY, attacker VARCHAR(36) NOT NULL, defender VARCHAR(36)"
              + " NOT NULL, attacker_name VARCHAR(48) NOT NULL, defender_name VARCHAR(48) NOT NULL,"
              + " reason TEXT NOT NULL, declared_at BIGINT NOT NULL, starts_at BIGINT NOT NULL,"
              + " ended_at BIGINT NOT NULL, ending VARCHAR(16), winner VARCHAR(36), attacker_kills"
              + " INT NOT NULL, defender_kills INT NOT NULL, terms TEXT, tribute INT NOT NULL,"
              + " reward TEXT, peace_by VARCHAR(36), peace_terms TEXT, chronicle TEXT NOT NULL)"
              + charset);
      sql.executeUpdate(
          "CREATE TABLE IF NOT EXISTS "
              + table("clan_backups")
              + " (clan_id VARCHAR(36) PRIMARY KEY, leader VARCHAR(36) NOT NULL, name VARCHAR(48) NOT"
              + " NULL, data TEXT NOT NULL, dissolved_at BIGINT NOT NULL, reason TEXT, home_size"
              + " VARCHAR(80))"
              + charset);
    }
  }

  // ------------------------------------------------------------------ copias de clanes disueltos

  public List<ClanBackup> loadBackups() throws SQLException {
    List<ClanBackup> result = new ArrayList<>();
    try (Connection connection = database.connection();
        Statement sql = connection.createStatement();
        ResultSet rows = sql.executeQuery("SELECT * FROM " + table("clan_backups"))) {
      while (rows.next())
        result.add(
            new ClanBackup(
                fromJson(UUID.fromString(rows.getString("clan_id")), rows.getString("data")),
                rows.getLong("dissolved_at"),
                rows.getString("reason"),
                rows.getString("home_size")));
    }
    return result;
  }

  public CompletableFuture<Void> saveBackup(ClanBackup backup) {
    return database.submit(
        () ->
            database.transaction(
                connection -> {
                  deleteBackup(connection, backup.clan().id());
                  try (PreparedStatement sql =
                      connection.prepareStatement(
                          "INSERT INTO "
                              + table("clan_backups")
                              + " (clan_id,leader,name,data,dissolved_at,reason,home_size) VALUES (?,?,?,?,?,?,?)")) {
                    sql.setString(1, backup.clan().id().toString());
                    sql.setString(2, backup.clan().leader().toString());
                    sql.setString(3, backup.clan().name());
                    sql.setString(4, toJson(backup.clan()));
                    sql.setLong(5, backup.dissolvedAt());
                    sql.setString(6, backup.reason());
                    sql.setString(7, backup.homeSize());
                    sql.executeUpdate();
                  }
                }));
  }

  public CompletableFuture<Void> deleteBackup(UUID clan) {
    return database.submit(() -> database.transaction(connection -> deleteBackup(connection, clan)));
  }

  private void deleteBackup(Connection connection, UUID clan) throws SQLException {
    try (PreparedStatement sql =
        connection.prepareStatement("DELETE FROM " + table("clan_backups") + " WHERE clan_id=?")) {
      sql.setString(1, clan.toString());
      sql.executeUpdate();
    }
  }

  /** El clan sin territorios: nombre, color, líder, fundación, miembros, permisos y banderas. */
  static String toJson(Clan clan) {
    JsonObject json = new JsonObject();
    json.addProperty("name", clan.name());
    json.addProperty("color", clan.color());
    json.addProperty("leader", clan.leader().toString());
    json.addProperty("foundedAt", clan.foundedAt());
    JsonArray members = new JsonArray();
    for (ClanMember member : clan.members().values()) {
      JsonObject item = new JsonObject();
      item.addProperty("player", member.player().toString());
      item.addProperty("rank", member.rank().key());
      item.addProperty("joinedAt", member.joinedAt());
      members.add(item);
    }
    json.add("members", members);
    JsonObject permissions = new JsonObject();
    clan.permissions()
        .forEach(
            (rank, values) -> {
              JsonObject item = new JsonObject();
              values.forEach(item::addProperty);
              permissions.add(rank.key(), item);
            });
    json.add("permissions", permissions);
    JsonObject flags = new JsonObject();
    clan.flags().forEach(flags::addProperty);
    json.add("flags", flags);
    return json.toString();
  }

  static Clan fromJson(UUID id, String data) {
    JsonObject json = JsonParser.parseString(data).getAsJsonObject();
    Map<UUID, ClanMember> members = new HashMap<>();
    for (var element : json.getAsJsonArray("members")) {
      JsonObject item = element.getAsJsonObject();
      UUID player = UUID.fromString(item.get("player").getAsString());
      members.put(
          player,
          new ClanMember(
              player,
              ClanRank.parse(item.get("rank").getAsString()).orElse(ClanRank.GUEST),
              item.get("joinedAt").getAsLong(),
              true));
    }
    Map<ClanRank, Map<String, Boolean>> permissions = new EnumMap<>(ClanRank.class);
    for (var entry : json.getAsJsonObject("permissions").entrySet()) {
      var rank = ClanRank.parse(entry.getKey());
      if (rank.isEmpty()) continue;
      Map<String, Boolean> values = new HashMap<>();
      for (var value : entry.getValue().getAsJsonObject().entrySet())
        values.put(value.getKey(), value.getValue().getAsBoolean());
      permissions.put(rank.get(), values);
    }
    Map<String, String> flags = new HashMap<>();
    for (var entry : json.getAsJsonObject("flags").entrySet()) flags.put(entry.getKey(), entry.getValue().getAsString());
    return new Clan(
        id,
        json.get("name").getAsString(),
        json.get("color").getAsString(),
        UUID.fromString(json.get("leader").getAsString()),
        null,
        json.get("foundedAt").getAsLong(),
        members,
        Map.of(),
        permissions,
        flags);
  }

  // ------------------------------------------------------------------ lectura

  public List<Clan> loadClans() throws SQLException {
    try (Connection connection = database.connection()) {
      return loadClans(connection);
    }
  }

  private List<Clan> loadClans(Connection connection) throws SQLException {
    Map<UUID, Map<UUID, ClanMember>> members = new HashMap<>();
    try (Statement sql = connection.createStatement();
        ResultSet rows = sql.executeQuery("SELECT * FROM " + table("clan_members"))) {
      while (rows.next()) {
        UUID player = UUID.fromString(rows.getString("player_uuid"));
        ClanRank rank = ClanRank.parse(rows.getString("clan_rank")).orElse(ClanRank.GUEST);
        members
            .computeIfAbsent(UUID.fromString(rows.getString("clan_id")), k -> new HashMap<>())
            .put(
                player,
                new ClanMember(player, rank, rows.getLong("joined_at"), rows.getInt("active") != 0));
      }
    }
    Map<UUID, Map<ClanRank, Map<String, Boolean>>> permissions = new HashMap<>();
    try (Statement sql = connection.createStatement();
        ResultSet rows = sql.executeQuery("SELECT * FROM " + table("clan_permissions"))) {
      while (rows.next()) {
        var rank = ClanRank.parse(rows.getString("clan_rank"));
        if (rank.isEmpty()) continue;
        permissions
            .computeIfAbsent(UUID.fromString(rows.getString("clan_id")), k -> new HashMap<>())
            .computeIfAbsent(rank.get(), k -> new HashMap<>())
            .put(rows.getString("permission"), rows.getInt("allowed") != 0);
      }
    }
    Map<UUID, Map<UUID, ClanTerritory>> territories = new HashMap<>();
    try (Statement sql = connection.createStatement();
        ResultSet rows = sql.executeQuery("SELECT * FROM " + table("clan_territories"))) {
      while (rows.next()) {
        UUID protection = UUID.fromString(rows.getString("protection_id"));
        territories
            .computeIfAbsent(UUID.fromString(rows.getString("clan_id")), k -> new HashMap<>())
            .put(
                protection,
                new ClanTerritory(
                    protection,
                    UUID.fromString(rows.getString("contributed_by")),
                    rows.getLong("contributed_at"),
                    rows.getInt("home") != 0,
                    uuids(rows.getString("previous_owners")),
                    uuids(rows.getString("previous_members"))));
      }
    }
    List<Clan> result = new ArrayList<>();
    try (Statement sql = connection.createStatement();
        ResultSet rows =
            sql.executeQuery("SELECT * FROM " + table("clans") + " ORDER BY founded_at,id")) {
      while (rows.next()) {
        UUID id = UUID.fromString(rows.getString("id"));
        String home = rows.getString("home");
        result.add(
            new Clan(
                id,
                rows.getString("name"),
                rows.getString("color"),
                UUID.fromString(rows.getString("leader")),
                home == null ? null : UUID.fromString(home),
                rows.getLong("founded_at"),
                members.getOrDefault(id, Map.of()),
                territories.getOrDefault(id, Map.of()),
                permissions.getOrDefault(id, Map.of()),
                JSON.fromJson(
                    rows.getString("flags"), new TypeToken<Map<String, String>>() {}.getType())));
      }
    }
    return List.copyOf(result);
  }

  public List<ClanRequest> loadRequests() throws SQLException {
    List<ClanRequest> result = new ArrayList<>();
    try (Connection connection = database.connection();
        Statement sql = connection.createStatement();
        ResultSet rows = sql.executeQuery("SELECT * FROM " + table("clan_requests"))) {
      while (rows.next()) {
        String protection = rows.getString("protection_id"), by = rows.getString("created_by");
        result.add(
            new ClanRequest(
                UUID.fromString(rows.getString("id")),
                ClanRequest.Type.valueOf(rows.getString("type")),
                UUID.fromString(rows.getString("clan_id")),
                UUID.fromString(rows.getString("player_uuid")),
                protection == null ? null : UUID.fromString(protection),
                ClanRank.parse(rows.getString("clan_rank")).orElse(null),
                by == null ? null : UUID.fromString(by),
                rows.getLong("created_at"),
                rows.getLong("expires_at")));
      }
    }
    return result;
  }

  public List<ClanWar> loadWars() throws SQLException {
    List<ClanWar> result = new ArrayList<>();
    try (Connection connection = database.connection();
        Statement sql = connection.createStatement();
        ResultSet rows =
            sql.executeQuery("SELECT * FROM " + table("clan_wars") + " ORDER BY declared_at")) {
      while (rows.next()) {
        String ending = rows.getString("ending"),
            winner = rows.getString("winner"),
            peace = rows.getString("peace_by");
        result.add(
            new ClanWar(
                UUID.fromString(rows.getString("id")),
                UUID.fromString(rows.getString("attacker")),
                UUID.fromString(rows.getString("defender")),
                rows.getString("attacker_name"),
                rows.getString("defender_name"),
                rows.getString("reason"),
                rows.getLong("declared_at"),
                rows.getLong("starts_at"),
                rows.getLong("ended_at"),
                ending == null ? null : ClanWar.Ending.valueOf(ending),
                winner == null ? null : UUID.fromString(winner),
                rows.getInt("attacker_kills"),
                rows.getInt("defender_kills"),
                rows.getString("terms"),
                rows.getInt("tribute"),
                rows.getString("reward"),
                peace == null ? null : UUID.fromString(peace),
                rows.getString("peace_terms"),
                JSON.fromJson(rows.getString("chronicle"), new TypeToken<List<String>>() {}.getType())));
      }
    }
    return result;
  }

  private static Set<UUID> uuids(String json) {
    List<String> values = JSON.fromJson(json, new TypeToken<List<String>>() {}.getType());
    Set<UUID> result = new HashSet<>();
    if (values != null) for (String value : values) result.add(UUID.fromString(value));
    return result;
  }

  private static String json(Set<UUID> values) {
    return JSON.toJson(values.stream().map(UUID::toString).sorted().toList());
  }

  // ------------------------------------------------------------------ escritura

  /** Reescribe el clan completo (miembros, permisos y territorios) en una transacción. */
  public CompletableFuture<Void> save(Clan clan) {
    return database.submit(() -> database.transaction(connection -> write(connection, clan)));
  }

  public CompletableFuture<Void> delete(UUID clan) {
    return database.submit(
        () ->
            database.transaction(
                connection -> {
                  deleteRows(connection, clan);
                  try (PreparedStatement sql =
                      connection.prepareStatement(
                          "DELETE FROM " + table("clan_requests") + " WHERE clan_id=?")) {
                    sql.setString(1, clan.toString());
                    sql.executeUpdate();
                  }
                }));
  }

  private void deleteRows(Connection connection, UUID clan) throws SQLException {
    for (String name : List.of("clan_members", "clan_permissions", "clan_territories"))
      try (PreparedStatement sql =
          connection.prepareStatement("DELETE FROM " + table(name) + " WHERE clan_id=?")) {
        sql.setString(1, clan.toString());
        sql.executeUpdate();
      }
    try (PreparedStatement sql =
        connection.prepareStatement("DELETE FROM " + table("clans") + " WHERE id=?")) {
      sql.setString(1, clan.toString());
      sql.executeUpdate();
    }
  }

  private void write(Connection connection, Clan clan) throws SQLException {
    deleteRows(connection, clan.id());
    String id = clan.id().toString();
    try (PreparedStatement sql =
        connection.prepareStatement(
            "INSERT INTO "
                + table("clans")
                + " (id,name,color,leader,home,founded_at,flags) VALUES (?,?,?,?,?,?,?)")) {
      sql.setString(1, id);
      sql.setString(2, clan.name());
      sql.setString(3, clan.color());
      sql.setString(4, clan.leader().toString());
      sql.setString(5, clan.home() == null ? null : clan.home().toString());
      sql.setLong(6, clan.foundedAt());
      sql.setString(7, JSON.toJson(new TreeMap<>(clan.flags())));
      sql.executeUpdate();
    }
    try (PreparedStatement sql =
        connection.prepareStatement(
            "INSERT INTO "
                + table("clan_members")
                + " (clan_id,player_uuid,clan_rank,joined_at,active) VALUES (?,?,?,?,?)")) {
      for (ClanMember member : clan.members().values()) {
        sql.setString(1, id);
        sql.setString(2, member.player().toString());
        sql.setString(3, member.rank().key());
        sql.setLong(4, member.joinedAt());
        sql.setInt(5, member.active() ? 1 : 0);
        sql.addBatch();
      }
      sql.executeBatch();
    }
    try (PreparedStatement sql =
        connection.prepareStatement(
            "INSERT INTO "
                + table("clan_permissions")
                + " (clan_id,clan_rank,permission,allowed) VALUES (?,?,?,?)")) {
      for (var rank : clan.permissions().entrySet())
        for (var value : rank.getValue().entrySet()) {
          sql.setString(1, id);
          sql.setString(2, rank.getKey().key());
          sql.setString(3, value.getKey());
          sql.setInt(4, value.getValue() ? 1 : 0);
          sql.addBatch();
        }
      sql.executeBatch();
    }
    try (PreparedStatement sql =
        connection.prepareStatement(
            "INSERT INTO "
                + table("clan_territories")
                + " (protection_id,clan_id,contributed_by,contributed_at,home,previous_owners,previous_members)"
                + " VALUES (?,?,?,?,?,?,?)")) {
      for (ClanTerritory territory : clan.territories().values()) {
        sql.setString(1, territory.protection().toString());
        sql.setString(2, id);
        sql.setString(3, territory.contributor().toString());
        sql.setLong(4, territory.contributedAt());
        sql.setInt(5, territory.home() ? 1 : 0);
        sql.setString(6, json(territory.previousOwners()));
        sql.setString(7, json(territory.previousMembers()));
        sql.addBatch();
      }
      sql.executeBatch();
    }
  }

  public CompletableFuture<Void> saveRequest(ClanRequest request) {
    return database.submit(
        () ->
            database.transaction(
                connection -> {
                  deleteRequest(connection, request.id());
                  try (PreparedStatement sql =
                      connection.prepareStatement(
                          "INSERT INTO "
                              + table("clan_requests")
                              + " (id,type,clan_id,player_uuid,protection_id,clan_rank,created_by,created_at,expires_at)"
                              + " VALUES (?,?,?,?,?,?,?,?,?)")) {
                    sql.setString(1, request.id().toString());
                    sql.setString(2, request.type().name());
                    sql.setString(3, request.clan().toString());
                    sql.setString(4, request.player().toString());
                    sql.setString(5, request.protection() == null ? null : request.protection().toString());
                    sql.setString(6, request.rank() == null ? null : request.rank().key());
                    sql.setString(7, request.createdBy() == null ? null : request.createdBy().toString());
                    sql.setLong(8, request.createdAt());
                    sql.setLong(9, request.expiresAt());
                    sql.executeUpdate();
                  }
                }));
  }

  public CompletableFuture<Void> deleteRequest(UUID id) {
    return database.submit(() -> database.transaction(connection -> deleteRequest(connection, id)));
  }

  private void deleteRequest(Connection connection, UUID id) throws SQLException {
    try (PreparedStatement sql =
        connection.prepareStatement("DELETE FROM " + table("clan_requests") + " WHERE id=?")) {
      sql.setString(1, id.toString());
      sql.executeUpdate();
    }
  }

  public CompletableFuture<Void> saveWar(ClanWar war) {
    return database.submit(
        () ->
            database.transaction(
                connection -> {
                  try (PreparedStatement delete =
                      connection.prepareStatement("DELETE FROM " + table("clan_wars") + " WHERE id=?")) {
                    delete.setString(1, war.id().toString());
                    delete.executeUpdate();
                  }
                  try (PreparedStatement sql =
                      connection.prepareStatement(
                          "INSERT INTO "
                              + table("clan_wars")
                              + " (id,attacker,defender,attacker_name,defender_name,reason,declared_at,starts_at,ended_at,ending,winner,attacker_kills,defender_kills,terms,tribute,reward,peace_by,peace_terms,chronicle)"
                              + " VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
                    sql.setString(1, war.id().toString());
                    sql.setString(2, war.attacker().toString());
                    sql.setString(3, war.defender().toString());
                    sql.setString(4, war.attackerName());
                    sql.setString(5, war.defenderName());
                    sql.setString(6, war.reason());
                    sql.setLong(7, war.declaredAt());
                    sql.setLong(8, war.startsAt());
                    sql.setLong(9, war.endedAt());
                    sql.setString(10, war.ending() == null ? null : war.ending().name());
                    sql.setString(11, war.winner() == null ? null : war.winner().toString());
                    sql.setInt(12, war.attackerKills());
                    sql.setInt(13, war.defenderKills());
                    sql.setString(14, war.terms());
                    sql.setInt(15, war.tribute());
                    sql.setString(16, war.reward());
                    sql.setString(17, war.peaceProposedBy() == null ? null : war.peaceProposedBy().toString());
                    sql.setString(18, war.peaceTerms());
                    sql.setString(19, JSON.toJson(war.chronicle()));
                    sql.executeUpdate();
                  }
                }));
  }

  /**
   * Al pasar de SQLite a MySQL copia las tablas de clanes del archivo local si las de MySQL están
   * vacías y ya tienen las protecciones de sus territorios. Devuelve cuántos clanes se copiaron, o
   * -1 si faltan protecciones y no se copió nada.
   */
  int importSqlite(Connection local) throws SQLException {
    if (!database.hasTable(local, table("clans"))) return 0;
    try (Connection remote = database.connection()) {
      try (Statement sql = remote.createStatement();
          ResultSet rows = sql.executeQuery("SELECT COUNT(*) FROM " + table("clans"))) {
        if (rows.next() && rows.getInt(1) > 0) return 0;
      }
      // Sin sus protecciones, los clanes perderían su territorio: solo se copian si todas están.
      Set<String> protections = new HashSet<>();
      try (Statement sql = remote.createStatement();
          ResultSet rows = sql.executeQuery("SELECT id FROM " + table("protections"))) {
        while (rows.next()) protections.add(rows.getString(1));
      }
      if (database.hasTable(local, table("clan_territories")))
        try (Statement sql = local.createStatement();
            ResultSet rows = sql.executeQuery("SELECT protection_id FROM " + table("clan_territories"))) {
          while (rows.next())
            if (!protections.contains(rows.getString(1))) return -1;
        }
      int clans = 0;
      remote.setAutoCommit(false);
      try {
        for (String name : TABLES) {
          if (!database.hasTable(local, table(name))) continue;
          try (Statement read = local.createStatement();
              ResultSet rows = read.executeQuery("SELECT * FROM " + table(name))) {
            var meta = rows.getMetaData();
            int columns = meta.getColumnCount();
            StringBuilder names = new StringBuilder(), marks = new StringBuilder();
            for (int i = 1; i <= columns; i++) {
              if (i > 1) {
                names.append(',');
                marks.append(',');
              }
              names.append(meta.getColumnName(i));
              marks.append('?');
            }
            try (PreparedStatement write =
                remote.prepareStatement(
                    "INSERT INTO " + table(name) + " (" + names + ") VALUES (" + marks + ")")) {
              while (rows.next()) {
                for (int i = 1; i <= columns; i++) write.setObject(i, rows.getObject(i));
                write.addBatch();
                if (name.equals("clans")) clans++;
              }
              write.executeBatch();
            }
          }
        }
        remote.commit();
      } catch (SQLException | RuntimeException failure) {
        remote.rollback();
        throw failure;
      } finally {
        remote.setAutoCommit(true);
      }
      return clans;
    }
  }
}
