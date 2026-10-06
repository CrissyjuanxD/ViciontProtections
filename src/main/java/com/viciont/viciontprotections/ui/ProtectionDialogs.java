package com.viciont.viciontprotections.ui;

import com.google.gson.*;
import com.viciont.viciontprotections.managers.ProtectionManager;
import com.viciont.viciontprotections.models.Protection;
import com.viciont.viciontprotections.security.AccessPolicy;
import java.util.*;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.chat.ComponentSerializer;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Diálogos vanilla JSON: misma implementación en Spigot/Paper, sin enlazar clases exclusivas de
 * 1.21.6.
 */
public class ProtectionDialogs {
  private final JavaPlugin plugin;
  private final Messages messages;
  private final ProtectionManager manager;

  public ProtectionDialogs(JavaPlugin plugin, Messages messages, ProtectionManager manager) {
    this.plugin = plugin;
    this.messages = messages;
    this.manager = manager;
  }

  public static boolean supportsVersion(String version) {
    try {
      String[] n = version.split("-")[0].split("\\.");
      int major = Integer.parseInt(n[0]);
      if (major > 1) return true;
      int minor = Integer.parseInt(n[1]), patch = n.length > 2 ? Integer.parseInt(n[2]) : 0;
      return major > 1 || (minor > 21 || minor == 21 && patch >= 6);
    } catch (RuntimeException unknown) {
      return false;
    }
  }

  public boolean supported(Player player) {
    if (!supportsVersion(Bukkit.getBukkitVersion())
        || !plugin.getConfig().getBoolean("dialogs.enabled", true)) return false;
    var via = Bukkit.getPluginManager().getPlugin("ViaVersion");
    if (via != null)
      try {
        Class<?> type =
            Class.forName(
                "com.viaversion.viaversion.api.Via", true, via.getClass().getClassLoader());
        Object api = type.getMethod("getAPI").invoke(null);
        Class<?> apiType =
            Class.forName(
                "com.viaversion.viaversion.api.ViaAPI", true, via.getClass().getClassLoader());
        return ((Number)
                    apiType
                        .getMethod("getPlayerVersion", UUID.class)
                        .invoke(api, player.getUniqueId()))
                .intValue()
            >= 771;
      } catch (ReflectiveOperationException unknown) {
        return false;
      }
    return true;
  }

  private JsonElement text(String value) {
    return JsonParser.parseString(
        ComponentSerializer.toString(TextComponent.fromLegacyText(Messages.color(value))));
  }

  private JsonObject base(String title) {
    var dialog = new JsonObject();
    dialog.addProperty("type", "minecraft:multi_action");
    dialog.add("title", text("&#D4A7FF۞ " + title));
    dialog.addProperty("columns", 2);
    dialog.addProperty("can_close_with_escape", true);
    dialog.add("body", new JsonArray());
    dialog.add("actions", new JsonArray());
    return dialog;
  }

  private void body(JsonObject dialog, String line) {
    var item = new JsonObject();
    item.addProperty("type", "minecraft:plain_message");
    item.add("contents", text(line));
    item.addProperty("width", 310);
    dialog.getAsJsonArray("body").add(item);
  }

  private JsonObject button(String label, String command) {
    var button = new JsonObject();
    button.add("label", text("&#EFC2E6" + label));
    button.addProperty("width", 150);
    if (command != null) {
      var action = new JsonObject();
      action.addProperty("type", "run_command");
      action.addProperty("command", command);
      button.add("action", action);
    }
    return button;
  }

  private void add(JsonObject dialog, String label, String command) {
    dialog.getAsJsonArray("actions").add(button(label, command));
  }

  public void guide(Player player) {
    if (!supported(player)) {
      messages.guideChat(player);
      return;
    }
    JsonObject dialog = base("Guía de protecciones");
    for (String line : messages.guideLines()) body(dialog, line);
    for (String site : List.of("spigot", "modrinth")) {
      String url = messages.guideUrl(site);
      if (url.isEmpty()) continue;
      JsonObject link = button("Guía en " + site, null);
      JsonObject action = new JsonObject();
      action.addProperty("type", "open_url");
      action.addProperty("url", url);
      link.add("action", action);
      dialog.getAsJsonArray("actions").add(link);
    }
    add(dialog, "Mis protecciones", "/pr lista");
    if (player.hasPermission("viciontprotections.admin.give"))
      add(dialog, "Entregar bloque", "/pr formulario dar");
    if (player.hasPermission("viciontprotections.admin.selection"))
      add(dialog, "Proteger selección WE", "/pr formulario seleccion");
    if (player.hasPermission("viciontprotections.admin.reload"))
      add(dialog, "Recargar ajustes", "/pr recargar");
    dialog.add("exit_action", button("Cerrar", null));
    show(player, dialog);
  }

  public void open(Player player, Protection protection) {
    if (!supported(player)) {
      messages.guideChat(player);
      return;
    }
    if (protection == null) {
      guide(player);
      return;
    }
    JsonObject dialog = base(protection.name());
    body(dialog, "&#E6CCFFCreador: &#F2BBDC" + ownerName(protection.primaryOwner()));
    body(
        dialog,
        "&#D2ACFF"
            + protection.bounds().width()
            + " × "
            + protection.bounds().depth()
            + " · "
            + protection.worldName()
            + " · #"
            + protection.shortId());
    String root = "/pr @" + protection.id() + " ";
    if (AccessPolicy.allows(player, protection, AccessPolicy.Action.MEMBERS)) {
      add(dialog, "Añadir miembro", root + "formulario miembro");
      add(dialog, "Quitar miembro", root + "formulario quitar-miembro");
    }
    if (AccessPolicy.allows(player, protection, AccessPolicy.Action.OWNERS)) {
      add(dialog, "Añadir propietario", root + "formulario propietario");
      add(dialog, "Quitar propietario", root + "formulario quitar-propietario");
    }
    if (AccessPolicy.allows(player, protection, AccessPolicy.Action.RENAME))
      add(dialog, "Cambiar nombre", root + "formulario nombre");
    if (AccessPolicy.allows(player, protection, AccessPolicy.Action.BORDERS))
      add(dialog, "Mostrar / ocultar límites", root + "limites");
    if (AccessPolicy.allows(player, protection, AccessPolicy.Action.INFO))
      add(dialog, "Ver miembros y propietarios", root + "info");
    if (AccessPolicy.allows(player, protection, AccessPolicy.Action.DELETE))
      add(dialog, "Eliminar protección", root + "confirmacion");
    if (AccessPolicy.allows(player, protection, AccessPolicy.Action.FLAGS))
      add(dialog, "Permisos de WorldGuard", root + "formulario bandera");
    if (AccessPolicy.allows(player, protection, AccessPolicy.Action.TRANSFER))
      add(dialog, "Transferir creador", root + "formulario transferir");
    if (player.hasPermission("viciontprotections.admin.give"))
      add(dialog, "Entregar bloque", "/pr formulario dar");
    if (player.hasPermission("viciontprotections.admin.selection"))
      add(dialog, "Proteger selección WE", "/pr formulario seleccion");
    if (player.hasPermission("viciontprotections.admin.reload"))
      add(dialog, "Recargar ajustes", "/pr recargar");
    // La guía queda debajo de las acciones, en el botón inferior del diálogo.
    dialog.add("exit_action", button("۞ Guía de protecciones", "/pr guia"));
    show(player, dialog);
  }

  public void info(Player player, Protection protection) {
    JsonObject dialog = base(protection.name());
    body(dialog, "&#E6CCFFCreador: &#F2BBDC" + ownerName(protection.primaryOwner()));
    body(dialog, "&#E6CCFFPropietarios: &#F2BBDC" + roleNames(protection.owners()));
    body(dialog, "&#E6CCFFMiembros: &#F2BBDC" + roleNames(protection.members()));
    body(
        dialog,
        "&#D2ACFF"
            + protection.worldName()
            + " · "
            + protection.bounds().width()
            + " × "
            + protection.bounds().height()
            + " × "
            + protection.bounds().depth()
            + " · #"
            + protection.shortId());
    add(dialog, "Volver a la protección", "/pr @" + protection.id() + " menu");
    dialog.add("exit_action", button("۞ Guía de protecciones", "/pr guia"));
    show(player, dialog);
  }

  private String roleNames(Set<UUID> ids) {
    return ids.isEmpty()
        ? "ninguno"
        : String.join(
            ", ",
            ids.stream()
                .map(ProtectionDialogs::ownerName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList());
  }

  public void list(Player player, List<Protection> protections, int page, int pages) {
    JsonObject dialog = base("Protecciones · " + page + "/" + pages);
    body(dialog, "&#E6CCFFSelecciona una protección para ver sus opciones.");
    if (protections.isEmpty()) body(dialog, "&#ECC7FFAún no tienes protecciones.");
    for (Protection p : protections.subList((page - 1) * 8, Math.min(page * 8, protections.size())))
      add(dialog, p.name() + " · #" + p.shortId(), "/pr @" + p.id() + " menu");
    if (page > 1) add(dialog, "Anterior", "/pr lista " + (page - 1));
    if (page < pages) add(dialog, "Siguiente", "/pr lista " + (page + 1));
    dialog.add("exit_action", button("۞ Guía de protecciones", "/pr guia"));
    show(player, dialog);
  }

  public void form(Player player, Protection protection, String operation) {
    if (!supported(player))
      throw new IllegalArgumentException("Usa /pr ayuda; tu versión no admite diálogos.");
    String root = protection == null ? "/pr " : "/pr @" + protection.id() + " ";
    String suffix =
        switch (operation) {
          case "miembro" -> "miembro añadir $(valor)";
          case "quitar-miembro" -> "miembro quitar $(valor)";
          case "propietario" -> "propietario añadir $(valor)";
          case "quitar-propietario" -> "propietario quitar $(valor)";
          case "nombre" -> "nombre $(valor)";
          case "transferir" -> "transferir $(valor)";
          case "bandera" -> "bandera $(valor)";
          case "dar" -> "dar $(valor)";
          case "seleccion" -> "seleccion $(valor)";
          default -> throw new IllegalArgumentException("Formulario desconocido.");
        };
    JsonObject dialog = base("Protecciones · " + operation);
    body(
        dialog,
        switch (operation) {
          case "dar" ->
              "&#E6CCFFJugador material ancho [profundidad] [altura/todo] [cantidad]. Ejemplo: Alex"
                  + " DIAMOND_BLOCK 32";
          case "seleccion" ->
              "&#E6CCFFJugador y nombre para la selección de WorldEdit: Alex Mi base";
          case "bandera" ->
              "&#E6CCFFBandera y valor: pvp denegar. Valores: permitir, denegar, restablecer.";
          case "nombre" -> "&#E6CCFFEscribe el nuevo nombre (hasta 48 caracteres).";
          default -> "&#E6CCFFEscribe el nombre de un jugador conocido o su UUID.";
        });
    var inputs = new JsonArray();
    var input = new JsonObject();
    input.addProperty("type", "minecraft:text");
    input.addProperty("key", "valor");
    input.add("label", text("&#E6CCFFValor"));
    input.addProperty("max_length", 180);
    inputs.add(input);
    dialog.add("inputs", inputs);
    var actionButton = button("Confirmar", null);
    var action = new JsonObject();
    action.addProperty("type", "minecraft:dynamic/run_command");
    action.addProperty("template", root + suffix);
    actionButton.add("action", action);
    dialog.getAsJsonArray("actions").add(actionButton);
    dialog.add("exit_action", button("Volver", root + "menu"));
    show(player, dialog);
  }

  public void confirmDelete(Player player, Protection protection) {
    var dialog = base("Eliminar protección");
    body(dialog, "&#F1B6D9¿Eliminar " + protection.name() + "? La zona dejará de estar protegida.");
    add(dialog, "Sí, eliminar", "/pr @" + protection.id() + " eliminar confirmar");
    add(dialog, "Cancelar", "/pr @" + protection.id() + " menu");
    dialog.add("exit_action", button("Guía", "/pr guia"));
    show(player, dialog);
  }

  protected void show(Player player, JsonObject dialog) {
    if (!Bukkit.dispatchCommand(
        Bukkit.getConsoleSender(), "minecraft:dialog show " + player.getUniqueId() + " " + dialog))
      messages.guideChat(player);
  }

  public static String ownerName(UUID id) {
    String name = Bukkit.getOfflinePlayer(id).getName();
    return name == null ? id.toString() : name;
  }
}
