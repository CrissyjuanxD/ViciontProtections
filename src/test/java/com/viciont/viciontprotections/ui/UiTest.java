package com.viciont.viciontprotections.ui;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.*;
import net.md_5.bungee.api.dialog.*;
import net.md_5.bungee.api.dialog.action.*;
import net.md_5.bungee.api.dialog.input.*;
import org.junit.jupiter.api.Test;

class UiTest {
  @Test
  void paletteTokensAndHexBecomeMinecraftColors() {
    String colored = Messages.color("{dato}Hola &#B07CFFmundo");
    assertTrue(colored.startsWith("§x§F§5§B§3§D§AHola"), colored);
    assertTrue(colored.contains("§x§B§0§7§C§F§Fmundo"), colored);
    assertEquals("{desconocido}", Messages.color("{desconocido}"));
  }

  @Test
  void gradientInterpolatesFromFirstToLastColorAndKeepsFormats() {
    String result = Messages.gradient("A&lBC", "000000", "FFFFFF");
    assertTrue(result.startsWith("&#000000A"), result);
    assertTrue(result.contains("&#808080&lB"), result);
    assertTrue(result.endsWith("&#FFFFFF&lC"), result);
  }

  @Test
  void prefixDeclaresViciontProtections() {
    String plain = net.md_5.bungee.api.ChatColor.stripColor(
        Messages.color(
            "{titulo}۞ {suave}[<gradient:#C29BFF:#F5A8D6>Viciont Protections</gradient>{suave}] "));
    assertEquals("۞ [Viciont Protections] ", plain);
  }

  @Test
  void dialogsConvertToSpigotApiIncludingButtonsWithoutAction() {
    JsonObject json =
        JsonParser.parseString(
                """
                {"type":"minecraft:multi_action","title":{"text":"Casa"},"columns":2,
                 "can_close_with_escape":true,
                 "body":[{"type":"minecraft:plain_message","contents":{"text":"hola"},"width":320}],
                 "inputs":[{"type":"minecraft:text","key":"jugador","label":{"text":"Jugador"},"width":260,"initial":"","max_length":36},
                           {"type":"minecraft:single_option","key":"valor","label":{"text":"Valor"},"width":260,
                            "options":[{"id":"permitir","display":{"text":"Permitir"},"initial":true},{"id":"denegar","display":{"text":"Denegar"}}]}],
                 "actions":[{"label":{"text":"Añadir"},"width":150,"action":{"type":"minecraft:dynamic/run_command","template":"/pr miembro añadir $(jugador) -d"}},
                            {"label":{"text":"Menú"},"tooltip":{"text":"abre"},"width":150,"action":{"type":"minecraft:run_command","command":"/pr menu"}}],
                 "exit_action":{"label":{"text":"Cerrar"},"width":150}}
                """)
            .getAsJsonObject();
    var dialog = assertInstanceOf(MultiActionDialog.class, SpigotDialogs.convert(json));
    assertEquals(1, dialog.getBase().body().size());
    assertEquals(2, dialog.getBase().inputs().size());
    assertInstanceOf(SingleOptionInput.class, dialog.getBase().inputs().get(1));
    assertEquals(2, dialog.actions().size());
    assertEquals(
        "/pr miembro añadir $(jugador) -d",
        assertInstanceOf(RunCommandAction.class, dialog.actions().get(0).action()).template());
    var close = assertInstanceOf(StaticAction.class, dialog.exitAction().action());
    assertEquals("/pr cerrar", close.clickEvent().getValue());
  }

  @Test
  void confirmationDialogsKeepBothButtons() {
    JsonObject json =
        JsonParser.parseString(
                """
                {"type":"minecraft:confirmation","title":{"text":"Eliminar"},"body":[],
                 "yes":{"label":{"text":"Sí"},"width":150,"action":{"type":"minecraft:run_command","command":"/pr @x eliminar confirmar"}},
                 "no":{"label":{"text":"No"},"width":150,"action":{"type":"minecraft:run_command","command":"/pr @x menu"}}}
                """)
            .getAsJsonObject();
    var dialog = assertInstanceOf(ConfirmationDialog.class, SpigotDialogs.convert(json));
    assertEquals(
        "/pr @x eliminar confirmar",
        ((StaticAction) dialog.yes().action()).clickEvent().getValue());
  }

  @Test
  void dialogWithoutActionsKeepsASingleCloseButton() {
    JsonObject guide =
        JsonParser.parseString(
                """
                {"type":"minecraft:multi_action","title":{"text":"Guía"},"body":[],"actions":[],
                 "exit_action":{"label":{"text":"Cerrar"},"width":150}}
                """)
            .getAsJsonObject();
    ProtectionDialogs.withOneAction(guide);
    assertEquals(1, guide.getAsJsonArray("actions").size());
    assertFalse(guide.has("exit_action"));
    assertEquals(
        "Cerrar",
        guide.getAsJsonArray("actions").get(0).getAsJsonObject().getAsJsonObject("label").get("text").getAsString());

    JsonObject empty =
        JsonParser.parseString(
                """
                {"type":"minecraft:multi_action","title":{"text":"Lista"},"body":[],"actions":[]}
                """)
            .getAsJsonObject();
    ProtectionDialogs.withOneAction(empty);
    assertEquals(1, empty.getAsJsonArray("actions").size());
  }

  @Test
  void escapeAlwaysClosesAndBackButtonsMoveToTheList() {
    JsonObject menu =
        JsonParser.parseString(
                """
                {"type":"minecraft:multi_action","title":{"text":"Casa"},"body":[],
                 "actions":[{"label":{"text":"Miembros"},"width":150,"action":{"type":"minecraft:run_command","command":"/pr @x miembros"}}],
                 "exit_action":{"label":{"text":"Guía"},"width":150,"action":{"type":"minecraft:run_command","command":"/pr @x guia"}}}
                """)
            .getAsJsonObject();
    ProtectionDialogs.escapeCloses(menu);
    var actions = menu.getAsJsonArray("actions");
    assertEquals(2, actions.size(), "la guía pasa a la lista de botones");
    assertEquals("/pr @x guia", actions.get(1).getAsJsonObject().getAsJsonObject("action").get("command").getAsString());
    assertFalse(menu.getAsJsonObject("exit_action").has("action"), "el botón inferior (y ESC) solo cierra");

    JsonObject confirm =
        JsonParser.parseString(
                """
                {"type":"minecraft:confirmation","title":{"text":"Eliminar"},"body":[],
                 "yes":{"label":{"text":"Sí"},"width":150,"action":{"type":"minecraft:run_command","command":"/pr @x eliminar confirmar"}},
                 "no":{"label":{"text":"No"},"width":150,"action":{"type":"minecraft:run_command","command":"/pr @x menu"}}}
                """)
            .getAsJsonObject();
    ProtectionDialogs.escapeCloses(confirm);
    assertEquals("minecraft:multi_action", confirm.get("type").getAsString());
    assertEquals(2, confirm.getAsJsonArray("actions").size());
    assertFalse(confirm.getAsJsonObject("exit_action").has("action"));
    assertInstanceOf(MultiActionDialog.class, SpigotDialogs.convert(confirm));

    JsonObject list =
        JsonParser.parseString(
                """
                {"type":"minecraft:multi_action","title":{"text":"Lista"},"body":[],
                 "actions":[{"label":{"text":"Casa"},"width":150,"action":{"type":"minecraft:run_command","command":"/pr @x menu"}}],
                 "exit_action":{"label":{"text":"Cerrar"},"width":150}}
                """)
            .getAsJsonObject();
    ProtectionDialogs.escapeCloses(list);
    assertEquals(1, list.getAsJsonArray("actions").size());
    assertEquals("Cerrar", list.getAsJsonObject("exit_action").getAsJsonObject("label").get("text").getAsString());
  }

  @Test
  void backButtonGoesLastRightAboveExit() {
    JsonObject page =
        JsonParser.parseString(
                """
                {"type":"minecraft:multi_action","title":{"text":"Miembros"},"body":[],
                 "actions":[{"label":{"text":"Alex"},"width":150,"action":{"type":"minecraft:run_command","command":"/pr @x miembro quitar Alex"}}]}
                """)
            .getAsJsonObject();
    page.add(
        ProtectionDialogs.BACK,
        JsonParser.parseString(
                """
                {"label":{"text":"Volver"},"width":150,"action":{"type":"minecraft:run_command","command":"/pr @x menu"}}
                """)
            .getAsJsonObject());
    ProtectionDialogs.escapeCloses(page);
    var actions = page.getAsJsonArray("actions");
    assertEquals(2, actions.size());
    assertEquals("/pr @x menu", actions.get(1).getAsJsonObject().getAsJsonObject("action").get("command").getAsString());
    assertFalse(page.has(ProtectionDialogs.BACK), "no llega al cliente");
    assertFalse(page.getAsJsonObject("exit_action").has("action"), "abajo del todo, «Salir» solo cierra");
  }
}
