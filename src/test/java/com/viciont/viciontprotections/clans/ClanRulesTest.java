package com.viciont.viciontprotections.clans;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.viciont.viciontprotections.api.clan.*;
import java.io.FileInputStream;
import java.nio.file.*;
import java.util.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClanRulesTest {
  @TempDir Path temp;

  @Test
  void ranksParseInSpanishWithOrWithoutAccents() {
    assertEquals(ClanRank.CO_LEADER, ClanRank.parse("Sublíder").orElseThrow());
    assertEquals(ClanRank.LEADER, ClanRank.parse("lider").orElseThrow());
    assertEquals(ClanRank.GUEST, ClanRank.parse("INVITADO").orElseThrow());
    assertEquals(ClanRank.OFFICER, ClanRank.parse("officer").orElseThrow());
    assertTrue(ClanRank.parse("rey").isEmpty());
    assertTrue(ClanRank.LEADER.isAbove(ClanRank.CO_LEADER));
    assertEquals(Optional.of(ClanRank.OFFICER), ClanRank.APPRENTICE.next());
    assertTrue(ClanRank.GUEST.previous().isEmpty());
  }

  @Test
  void aClanHasExactlyOneLeader() {
    UUID a = UUID.randomUUID(), b = UUID.randomUUID();
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new Clan(UUID.randomUUID(), "X", "#FFFFFF", a, null, 0,
                Map.of(a, new ClanMember(a, ClanRank.OFFICER, 0, true)), Map.of(), Map.of(), Map.of()));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            new Clan(UUID.randomUUID(), "X", "#FFFFFF", a, null, 0,
                Map.of(a, new ClanMember(a, ClanRank.LEADER, 0, true), b, new ClanMember(b, ClanRank.LEADER, 0, true)),
                Map.of(), Map.of(), Map.of()));
  }

  @Test
  void permissionsFollowDefaultsLeaderChangesAndServerLocks() {
    Set<ClanRank> defaults = Set.of(ClanRank.CO_LEADER, ClanRank.OFFICER);
    assertTrue(ClanManager.resolve(ClanRank.LEADER, Set.of(), true, false), "el líder siempre");
    assertTrue(ClanManager.resolve(ClanRank.OFFICER, defaults, false, null));
    assertFalse(ClanManager.resolve(ClanRank.APPRENTICE, defaults, false, null));
    assertTrue(ClanManager.resolve(ClanRank.APPRENTICE, defaults, false, true), "el líder la activa");
    assertFalse(ClanManager.resolve(ClanRank.OFFICER, defaults, false, false), "el líder la quita");
    assertTrue(ClanManager.resolve(ClanRank.OFFICER, defaults, true, false), "fijada por el servidor");
  }

  @Test
  void settingsReadDefaultsLocksColorsAndGuideValues() throws Exception {
    Path resource = Path.of("src/main/resources/clans.yml");
    Files.copy(resource, temp.resolve("clans.yml"));
    JavaPlugin plugin = mock(JavaPlugin.class);
    when(plugin.getDataFolder()).thenReturn(temp.toFile());
    when(plugin.getResource("clans.yml")).thenAnswer(call -> new FileInputStream(resource.toFile()));
    var settings = new ClanSettings(plugin);
    assertEquals(Set.of(ClanRank.CO_LEADER, ClanRank.OFFICER), settings.defaults("cofres", Set.of()));
    assertEquals(
        Set.of(ClanRank.CO_LEADER, ClanRank.OFFICER, ClanRank.APPRENTICE, ClanRank.GUEST),
        settings.defaults("puertas", Set.of()));
    assertEquals(Set.of(ClanRank.GUEST), settings.defaults("banco-retirar", Set.of(ClanRank.GUEST)), "extensiones");
    assertFalse(settings.locked("construir"));
    assertEquals("Sublíder", settings.rankName(ClanRank.CO_LEADER));
    assertEquals(Optional.of("#C29BFF"), settings.color("LILA"));
    assertTrue(settings.color("negro").isEmpty());
    assertEquals("El PvP empieza en 24 horas", settings.guideValues("El PvP empieza en %preaviso% horas"));
    assertEquals("FFC94D".length(), settings.palette().get("dato").length());
    assertFalse(settings.topics().isEmpty());
    assertTrue(settings.prefix("publico").contains("Viciont Clans"));
  }

  @Test
  void textHelpers() {
    assertEquals("templo camaron", ClanManager.normalized("  Templo   Camarón "));
    assertEquals("2 días y 3 horas", ClanWars.duration(51 * 3_600_000L));
    assertEquals("5 horas", ClanWars.duration(5 * 3_600_000L));
    assertEquals("1 minuto", ClanWars.duration(10));
    Clan clan = com.viciont.viciontprotections.database.ClanStoreTestAccess.clan("Lobos");
    assertEquals("&#C29BFF[Lobos] ", ClanChat.fill("%color%[%clan%] ", clan));
  }
}
