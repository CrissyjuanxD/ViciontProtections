package com.viciont.viciontprotections;

import static org.junit.jupiter.api.Assertions.*;

import com.viciont.viciontprotections.api.Bounds;
import com.viciont.viciontprotections.database.DatabaseManager;
import com.viciont.viciontprotections.models.Protection;
import com.zaxxer.hikari.*;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class DatabaseTest {
  @TempDir Path temp;
  private HikariDataSource pool;
  private DatabaseManager store;

  @BeforeEach
  void open() throws Exception {
    var config = new HikariConfig();
    config.setJdbcUrl("jdbc:sqlite:" + temp.resolve("test.db"));
    config.setMaximumPoolSize(1);
    config.setConnectionInitSql("PRAGMA foreign_keys=ON");
    pool = new HikariDataSource(config);
    store = new DatabaseManager(pool, "vp_");
  }

  @AfterEach
  void close() {
    store.close();
  }

  @Test
  void savesRolesNamesAndFlagsAndDeletesAtomically() throws Exception {
    var p =
        new Protection(
            UUID.randomUUID(),
            "Casa de Ana",
            "mundo_aun_no_cargado",
            new Bounds(-3, -64, -3, 3, 319, 3),
            UUID.randomUUID(),
            Set.of(UUID.randomUUID()),
            Set.of(UUID.randomUUID()),
            Map.of("pvp", "DENY"),
            null,
            17);
    store.save(p).get(5, TimeUnit.SECONDS);
    assertEquals(List.of(p), store.loadAll());
    var renamed = p.withName("Nueva casa");
    store.save(renamed).get(5, TimeUnit.SECONDS);
    assertEquals(List.of(renamed), store.loadAll());
    store.delete(p.id()).get(5, TimeUnit.SECONDS);
    assertTrue(store.loadAll().isEmpty());
    try (var c = pool.getConnection();
        var s = c.createStatement();
        var r = s.executeQuery("SELECT count(*) FROM vp_roles")) {
      assertTrue(r.next());
      assertEquals(0, r.getInt(1));
    }
  }

  @Test
  void refusesSqlIdentifierInjection() {
    assertThrows(
        IllegalArgumentException.class, () -> new DatabaseManager(pool, "bad;DROP TABLE x;"));
  }

  @Test
  void importsOldDatabaseOnceWithoutLosingOriginalBoundsOrUnnamedRegions() throws Exception {
    UUID creator = UUID.randomUUID(), member = UUID.randomUUID();
    try (var source = DriverManager.getConnection("jdbc:sqlite:" + temp.resolve("legacy.db"));
        var sql = source.createStatement()) {
      sql.execute(
          "CREATE TABLE protections(id INTEGER PRIMARY KEY,name TEXT,world TEXT,x INT,y INT,z"
              + " INT,size INT)");
      sql.execute(
          "CREATE TABLE protection_owners(protection_id INT,player_uuid TEXT,is_primary BOOLEAN)");
      sql.execute("CREATE TABLE protection_members(protection_id INT,player_uuid TEXT)");
      sql.execute("INSERT INTO protections VALUES(1,NULL,'missing_world',0,64,0,32)");
      sql.execute("INSERT INTO protection_owners VALUES(1,'" + creator + "',1)");
      sql.execute("INSERT INTO protection_members VALUES(1,'" + member + "')");
      store.migrateLegacy(source);
      store.migrateLegacy(source);
      var loaded = store.loadAll();
      assertEquals(1, loaded.size());
      var p = loaded.getFirst();
      assertEquals("Protección #1", p.name());
      assertEquals(33, p.bounds().width());
      assertEquals(creator, p.primaryOwner());
      assertEquals(Set.of(member), p.members());
      try (var old = sql.executeQuery("SELECT count(*) FROM protections")) {
        assertTrue(old.next());
        assertEquals(1, old.getInt(1));
      }
    }
  }
}
