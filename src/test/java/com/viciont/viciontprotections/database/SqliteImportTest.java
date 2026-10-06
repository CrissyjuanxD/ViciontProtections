package com.viciont.viciontprotections.database;

import static org.junit.jupiter.api.Assertions.*;

import com.viciont.viciontprotections.api.Bounds;
import com.viciont.viciontprotections.models.Protection;
import com.zaxxer.hikari.*;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Al cambiar a MySQL, las protecciones 2.x del archivo SQLite pasan a la base vacía. */
class SqliteImportTest {
  @TempDir Path temp;

  private DatabaseManager open(String file) throws Exception {
    var config = new HikariConfig();
    config.setJdbcUrl("jdbc:sqlite:" + temp.resolve(file));
    config.setMaximumPoolSize(1);
    config.setConnectionInitSql("PRAGMA foreign_keys=ON");
    return new DatabaseManager(new HikariDataSource(config), "vp_");
  }

  private static Protection protection(String name) {
    return new Protection(
        UUID.randomUUID(),
        name,
        "world",
        new Bounds(0, -64, 0, 9, 319, 9),
        UUID.randomUUID(),
        Set.of(UUID.randomUUID()),
        Set.of(),
        Map.of("pvp", "DENY"),
        null,
        5);
  }

  @Test
  void copiesOnceIntoEmptyTargetAndKeepsLegacyMarker() throws Exception {
    DatabaseManager local = open("local.db"), remote = open("remote.db");
    Protection casa = protection("Casa Ñandú"), granja = protection("Granja");
    local.save(casa).get(5, TimeUnit.SECONDS);
    local.save(granja).get(5, TimeUnit.SECONDS);
    try (var c = DriverManager.getConnection("jdbc:sqlite:" + temp.resolve("local.db"));
        var sql = c.createStatement()) {
      sql.executeUpdate("INSERT INTO vp_meta VALUES ('legacy_v1','2')");
      // Tablas 1.x en el mismo archivo: no deben volver a importarse tras la copia.
      sql.execute("CREATE TABLE protections(id INTEGER PRIMARY KEY,name TEXT,world TEXT,x INT,y INT,z INT,size INT)");
      sql.execute("CREATE TABLE protection_owners(protection_id INT,player_uuid TEXT,is_primary BOOLEAN)");
      sql.execute("CREATE TABLE protection_members(protection_id INT,player_uuid TEXT)");
      sql.execute("INSERT INTO protections VALUES(1,'Vieja','world',500,64,500,32)");
      sql.execute("INSERT INTO protection_owners VALUES(1,'" + UUID.randomUUID() + "',1)");
      assertEquals(2, remote.importSqlite(c));
      remote.migrateLegacy(c);
      assertEquals(0, remote.importSqlite(c));
    }
    var names = remote.loadAll().stream().map(Protection::name).sorted().toList();
    assertEquals(List.of("Casa Ñandú", "Granja"), names);
    assertTrue(remote.loadAll().contains(casa));
    local.close();
    remote.close();
  }
}
