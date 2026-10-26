package com.viciont.viciontprotections.database;

import static org.junit.jupiter.api.Assertions.*;

import com.viciont.viciontprotections.api.Bounds;
import com.viciont.viciontprotections.api.clan.*;
import com.viciont.viciontprotections.models.ClanBackup;
import com.viciont.viciontprotections.models.ClanRequest;
import com.viciont.viciontprotections.models.Protection;
import com.zaxxer.hikari.*;
import java.nio.file.*;
import java.sql.DriverManager;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Tablas de clanes: copia previa, ida y vuelta de clanes, solicitudes, guerras y copias de clanes
 * disueltos, e importación.
 */
class ClanStoreTest {
  @TempDir Path temp;

  private DatabaseManager open(String file) throws Exception {
    var config = new HikariConfig();
    config.setJdbcUrl("jdbc:sqlite:" + temp.resolve(file));
    config.setMaximumPoolSize(1);
    config.setConnectionInitSql("PRAGMA foreign_keys=ON");
    return new DatabaseManager(new HikariDataSource(config), "vp_");
  }

  static Clan clan(String name) {
    UUID leader = UUID.randomUUID(), officer = UUID.randomUUID(), home = UUID.randomUUID(), extra = UUID.randomUUID();
    return new Clan(
        UUID.randomUUID(),
        name,
        "#C29BFF",
        leader,
        home,
        1000,
        Map.of(
            leader, new ClanMember(leader, ClanRank.LEADER, 1000, true),
            officer, new ClanMember(officer, ClanRank.OFFICER, 2000, false)),
        Map.of(
            home, new ClanTerritory(home, leader, 1000, true, Set.of(), Set.of()),
            extra, new ClanTerritory(extra, officer, 3000, false, Set.of(UUID.randomUUID()), Set.of(UUID.randomUUID()))),
        Map.of(ClanRank.OFFICER, Map.of("cofres", false, "banco-retirar", true)),
        Map.of("pvp", "DENY"));
  }

  @Test
  void backsUpOnceAndRoundTripsClansRequestsAndWars() throws Exception {
    var database = open("clans.db");
    database.save(
            new Protection(UUID.randomUUID(), "Casa", "world", new Bounds(0, 0, 0, 1, 1, 1), UUID.randomUUID(),
                Set.of(), Set.of(), Map.of(), null, 1))
        .get(5, TimeUnit.SECONDS);
    Path backup = temp.resolve("protections-pre-clanes.db");
    var store = new ClanStore(database, backup);
    assertTrue(Files.exists(backup), "copia antes de crear las tablas de clanes");
    try (var copy = DriverManager.getConnection("jdbc:sqlite:" + backup);
        var rows = copy.createStatement().executeQuery("SELECT count(*) FROM vp_protections")) {
      assertEquals(1, rows.getInt(1));
    }

    Clan clan = clan("Templo Camarón");
    store.save(clan).get(5, TimeUnit.SECONDS);
    assertEquals(List.of(clan), store.loadClans());
    Clan renamed = clan.withName("Los Lobos").withColor("#FFD36B").withFlags(Map.of());
    store.save(renamed).get(5, TimeUnit.SECONDS);
    assertEquals(List.of(renamed), store.loadClans());

    var request =
        new ClanRequest(UUID.randomUUID(), ClanRequest.Type.INVITE, clan.id(), UUID.randomUUID(), null,
            ClanRank.APPRENTICE, clan.leader(), 10, 20);
    store.saveRequest(request).get(5, TimeUnit.SECONDS);
    assertEquals(List.of(request), store.loadRequests());

    var war =
        new ClanWar(UUID.randomUUID(), clan.id(), UUID.randomUUID(), "Los Lobos", "Templo", "por las ovejas", 1, 2, 0,
            null, null, 3, 1, null, 0, null, clan.id(), "devolver las ovejas", List.of("a", "b"));
    store.saveWar(war).get(5, TimeUnit.SECONDS);
    var ended =
        new ClanWar(war.id(), war.attacker(), war.defender(), war.attackerName(), war.defenderName(), war.reason(), 1, 2, 9,
            ClanWar.Ending.SURRENDER, war.defender(), 3, 1, "nada", 5, "35 DinoCoins", null, null, List.of("a", "b", "c"));
    store.saveWar(ended).get(5, TimeUnit.SECONDS);
    assertEquals(List.of(ended), store.loadWars());

    store.delete(clan.id()).get(5, TimeUnit.SECONDS);
    assertTrue(store.loadClans().isEmpty());
    assertTrue(store.loadRequests().isEmpty(), "las solicitudes del clan se borran con él");
    assertEquals(1, store.loadWars().size(), "la historia de guerras se conserva");

    // Abrir de nuevo no repite la copia.
    Files.delete(backup);
    new ClanStore(database, backup);
    assertFalse(Files.exists(backup));
    database.close();
  }

  @Test
  void keepsBackupsOfDissolvedClansUntilTheyExpire() throws Exception {
    var database = open("backups.db");
    var store = new ClanStore(database, temp.resolve("pre.db"));
    Clan clan = clan("Templo Camarón");
    store.saveBackup(new ClanBackup(clan, 1000, "Rompió el bloque", "150 × 150")).get(5, TimeUnit.SECONDS);
    // La copia guarda nombre, color, miembros, permisos y banderas; los territorios no, porque al
    // disolver vuelven a sus dueños, y todos los miembros vuelven a mostrar este clan.
    Map<UUID, ClanMember> members = new HashMap<>();
    clan.members().forEach((id, member) -> members.put(id, member.withActive(true)));
    Clan kept =
        new Clan(clan.id(), clan.name(), clan.color(), clan.leader(), null, clan.foundedAt(), members, Map.of(),
            clan.permissions(), clan.flags());
    assertEquals(List.of(new ClanBackup(kept, 1000, "Rompió el bloque", "150 × 150")), store.loadBackups());

    // Una segunda copia del mismo clan sustituye a la anterior.
    var again = new ClanBackup(kept.withName("Los Lobos"), 2000, "", "");
    store.saveBackup(again).get(5, TimeUnit.SECONDS);
    assertEquals(List.of(again), store.loadBackups());

    long days = 86_400_000L;
    assertFalse(again.expired(2000 + 5 * days - 1, 5 * days));
    assertTrue(again.expired(2000 + 5 * days, 5 * days));

    store.deleteBackup(clan.id()).get(5, TimeUnit.SECONDS);
    assertTrue(store.loadBackups().isEmpty());
    database.close();
  }

  @Test
  void customHexSurvivesDatabaseReopeningAndTheDissolvedClanBackup() throws Exception {
    var database = open("custom-color.db");
    var store = new ClanStore(database, null);
    Clan original = clan("Color propio").withColor("#12ABEF");
    store.save(original).get(5, TimeUnit.SECONDS);
    store.saveBackup(new ClanBackup(original, 2000, "Prueba", "150 × 150")).get(5, TimeUnit.SECONDS);
    database.close();

    database = open("custom-color.db");
    store = new ClanStore(database, null);
    assertEquals(original, store.loadClans().getFirst());
    assertEquals("#12ABEF", store.loadBackups().getFirst().clan().color());
    database.close();
  }

  @Test
  void copiesClansFromSqliteIntoAnEmptyDatabase() throws Exception {
    var local = open("local.db");
    var localClans = new ClanStore(local, null);
    Clan clan = clan("Templo");
    localClans.save(clan).get(5, TimeUnit.SECONDS);
    local.close();

    var remote = open("remote.db");
    var remoteClans = new ClanStore(remote, null);
    try (var connection = DriverManager.getConnection("jdbc:sqlite:" + temp.resolve("local.db"))) {
      assertEquals(-1, remoteClans.importSqlite(connection), "sin las protecciones no se copia");
      for (UUID territory : clan.territories().keySet())
        remote.save(
                new Protection(territory, "Zona", "world", new Bounds(0, 0, 0, 1, 1, 1), clan.leader(),
                    Set.of(), Set.of(), Map.of(), null, 1))
            .get(5, TimeUnit.SECONDS);
      assertEquals(1, remoteClans.importSqlite(connection));
      assertEquals(0, remoteClans.importSqlite(connection), "no se copia dos veces");
    }
    assertEquals(List.of(clan), remoteClans.loadClans());
    remote.close();
  }
}
