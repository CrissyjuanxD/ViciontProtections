package com.viciont.viciontprotections.ui;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.google.gson.*;
import com.viciont.viciontprotections.clans.*;
import com.viciont.viciontprotections.database.ClanStoreTestAccess;
import java.io.FileInputStream;
import java.nio.file.*;
import java.util.*;
import net.md_5.bungee.api.dialog.MultiActionDialog;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

class ClanColorDialogTest {
  @TempDir Path temp;
  ClanViews views;
  ProtectionDialogs dialogs;
  Player player;

  @BeforeEach
  void setup() throws Exception {
    Path source = Path.of("src/main/resources/clans.yml");
    Files.copy(source, temp.resolve("clans.yml"));
    var plugin = mock(JavaPlugin.class);
    when(plugin.getDataFolder()).thenReturn(temp.toFile());
    when(plugin.getResource("clans.yml")).thenAnswer(call -> new FileInputStream(source.toFile()));
    var settings = new ClanSettings(plugin);
    var manager = mock(ClanManager.class);
    when(manager.settings()).thenReturn(settings);
    when(manager.messages()).thenReturn(new ClanMessages(plugin, settings));
    player = mock(Player.class);
    when(player.getUniqueId()).thenReturn(UUID.randomUUID());
    dialogs = mock(ProtectionDialogs.class);
    when(dialogs.supported(player)).thenReturn(true);
    when(dialogs.showJson(eq(player), any())).thenReturn(true);
    views = new ClanViews(manager, dialogs);
  }

  @Test
  void editDialogKeepsCustomColorAndOffersIndependentPaletteAndHexActions() {
    views.form(player, ClanStoreTestAccess.clan("Lobos").withColor("#12ABEF"), "color", null);
    JsonObject json = shown();
    JsonObject hex = input(json, "hex");
    assertEquals("minecraft:text", hex.get("type").getAsString());
    assertEquals(7, hex.get("max_length").getAsInt());
    assertEquals("#12ABEF", hex.get("initial").getAsString());
    var options = input(json, "color").getAsJsonArray("options");
    assertEquals("#12ABEF", options.get(0).getAsJsonObject().get("id").getAsString());
    assertTrue(options.asList().stream().anyMatch(o -> o.getAsJsonObject().get("id").getAsString().equals("lila")));
    assertTrue(templates(json).contains("/clan color $(hex) " + ClanViews.FROM_DIALOG));
    assertTrue(templates(json).contains("/clan color $(color) " + ClanViews.FROM_DIALOG));
    assertInstanceOf(MultiActionDialog.class, SpigotDialogs.convert(json));
  }

  @Test
  void foundingDialogSupportsBothColorsWithoutChangingTheNameCommand() {
    var founding = mock(ClanFounding.class);
    var draft = mock(ClanFounding.Draft.class, RETURNS_DEEP_STUBS);
    when(draft.spec().dimensions()).thenReturn("32 × 32");
    when(draft.expiresAt()).thenReturn(System.currentTimeMillis() + 300_000);
    when(founding.draft(player.getUniqueId())).thenReturn(Optional.of(draft));
    when(founding.requirements(player)).thenReturn(List.of());
    views.connect(founding);
    views.found(player, null);
    JsonObject json = shown();
    assertEquals(7, input(json, "hex").get("max_length").getAsInt());
    assertTrue(templates(json).contains("/clan fundar $(hex) $(nombre) " + ClanViews.FROM_DIALOG));
    assertTrue(templates(json).contains("/clan fundar $(color) $(nombre) " + ClanViews.FROM_DIALOG));
    assertInstanceOf(MultiActionDialog.class, SpigotDialogs.convert(json));
  }

  private JsonObject shown() {
    var capture = ArgumentCaptor.forClass(JsonObject.class);
    verify(dialogs).showJson(eq(player), capture.capture());
    return capture.getValue();
  }

  private JsonObject input(JsonObject json, String key) {
    return json.getAsJsonArray("inputs").asList().stream().map(JsonElement::getAsJsonObject)
        .filter(i -> i.get("key").getAsString().equals(key)).findFirst().orElseThrow();
  }

  private List<String> templates(JsonObject json) {
    return json.getAsJsonArray("actions").asList().stream().map(JsonElement::getAsJsonObject)
        .filter(b -> b.has("action")).map(b -> b.getAsJsonObject("action"))
        .filter(a -> a.has("template")).map(a -> a.get("template").getAsString()).toList();
  }
}
