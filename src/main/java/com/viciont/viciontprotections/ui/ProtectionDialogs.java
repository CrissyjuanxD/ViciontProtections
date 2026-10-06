package com.viciont.viciontprotections.ui;

import com.google.gson.*;
import com.viciont.viciontprotections.managers.ProtectionManager;
import com.viciont.viciontprotections.models.Protection;
import com.viciont.viciontprotections.security.AccessPolicy;
import com.viciont.viciontprotections.security.AccessPolicy.Action;
import java.util.*;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.chat.ComponentSerializer;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Diálogos vanilla de 1.21.6+ construidos como JSON: la misma definición sirve en Spigot y Paper.
 * Cada método devuelve falso cuando el jugador no puede verlos, para usar el chat en su lugar.
 * Los botones ejecutan los mismos comandos que se pueden escribir, así que los permisos se vuelven
 * a comprobar al pulsarlos.
 */
public class ProtectionDialogs {
  /** Marca final de los comandos lanzados desde un diálogo: al terminar se reabre el menú. */
  public static final String FROM_DIALOG = "-d";

  private static final int PAGE = 10;
  private static final List<String> COMMON_FLAGS =
      List.of(
          "pvp",
          "mob-spawning",
          "mob-damage",
          "creeper-explosion",
          "tnt",
          "fire-spread",
          "lava-fire",
          "lighter",
          "use",
          "interact",
          "chest-access",
          "entry",
          "exit",
          "item-drop",
          "item-pickup",
          "damage-animals",
          "vehicle-place",
          "vehicle-destroy",
          "ride",
          "sleep",
          "enderpearl",
          "chorus-fruit-teleport",
          "fall-damage",
          "leaf-decay",
          "ice-melt",
          "snow-fall",
          "crop-growth");

  private final JavaPlugin plugin;
  private final Messages messages;
  private final ProtectionManager manager;
  private final Boundaries boundaries;
  private final DialogTransport transport;

  public ProtectionDialogs(
      JavaPlugin plugin, Messages messages, ProtectionManager manager, Boundaries boundaries) {
    this.plugin = plugin;
    this.messages = messages;
    this.manager = manager;
    this.boundaries = boundaries;
    this.transport = new DialogTransport(plugin);
  }

  public String mode() {
    return transport.mode();
  }

  public static boolean supportsVersion(String version) {
    try {
      String[] n = version.split("-")[0].split("\\.");
      int major = Integer.parseInt(n[0]);
      if (major > 1) return true;
      int minor = Integer.parseInt(n[1]), patch = n.length > 2 ? Integer.parseInt(n[2]) : 0;
      return minor > 21 || minor == 21 && patch >= 6;
    } catch (RuntimeException unknown) {
      return false;
    }
  }

  public boolean supported(Player player) {
    if (!supportsVersion(Bukkit.getBukkitVersion())
        || !plugin.getConfig().getBoolean("dialogs.enabled", true)) return false;
    var via = Bukkit.getPluginManager().getPlugin("ViaVersion");
    if (via == null || !via.isEnabled()) return true;
    try {
      ClassLoader loader = via.getClass().getClassLoader();
      Object api =
          Class.forName("com.viaversion.viaversion.api.Via", true, loader)
              .getMethod("getAPI")
              .invoke(null);
      Class<?> type = Class.forName("com.viaversion.viaversion.api.ViaAPI", true, loader);
      int protocol;
      try {
        protocol = ((Number) type.getMethod("getPlayerVersion", UUID.class).invoke(api, player.getUniqueId())).intValue();
      } catch (NoSuchMethodException removed) {
        Object version =
            type.getMethod("getPlayerProtocolVersion", UUID.class)
                .invoke(api, player.getUniqueId());
        protocol = ((Number) version.getClass().getMethod("getVersion").invoke(version)).intValue();
      }
      // 771 = 1.21.6, primer cliente con diálogos. -1 = desconocido: se asume el del servidor.
      return protocol < 0 || protocol >= 771;
    } catch (ReflectiveOperationException | RuntimeException unknown) {
      return false;
    }
  }

  // ---------------------------------------------------------------- vistas

  /** Guía; con {@code back} añade el botón para volver a esa protección. */
  public boolean guide(Player player, Protection back) {
    if (!supported(player)) return false;
    var dialog = base("Guía de protecciones");
    body(dialog, "{texto}Así funcionan las protecciones del servidor:");
    for (String line : messages.guideLines()) body(dialog, line);
    if (back != null)
      add(dialog, "{titulo}◀ {texto}Volver a " + back.name(), null, root(back) + "menu");
    if (player.hasPermission("viciontprotections.user.list"))
      add(dialog, "{titulo}۞ {texto}Mis protecciones", "{suave}Lista de tus protecciones", "/pr lista");
    for (String site : List.of("spigot", "modrinth")) {
      String url = messages.guideUrl(site);
      if (url.isEmpty()) continue;
      JsonObject link =
          button(
              "{acento}Guía en " + (site.equals("spigot") ? "Spigot" : "Modrinth"),
              "{suave}" + url,
              null);
      link.add("action", click("open_url", "url", url));
      dialog.getAsJsonArray("actions").add(link);
    }
    if (hasAdmin(player))
      add(dialog, "{admin}✦ Administración", "{suave}Herramientas para el staff", "/pr admin");
    dialog.add("exit_action", button("{texto}Cerrar", null, null));
    return show(player, dialog);
  }

  /** Menú principal de una protección; los botones dependen del rol del jugador. */
  public boolean menu(Player player, Protection p, String notice) {
    if (!supported(player)) return false;
    var dialog = base(p.name());
    notice(dialog, notice);
    UUID id = player.getUniqueId();
    String role =
        p.primaryOwner().equals(id)
            ? "Creador"
            : p.owners().contains(id)
                ? "Propietario"
                : p.members().contains(id) ? "Miembro" : "Administración";
    body(
        dialog,
        "{suave}Creador: {dato}"
            + ownerName(p.primaryOwner())
            + (role.equals("Creador") ? "" : " {suave}· Tu rol: {dato}" + role));
    body(
        dialog,
        "{suave}Propietarios: {dato}"
            + p.owners().size()
            + " {suave}· Miembros: {dato}"
            + p.members().size()
            + " {suave}· Tamaño: {dato}"
            + size(p));
    body(dialog, "{suave}" + place(p) + " {suave}· {dato}#" + p.shortId());
    String root = root(p);
    boolean owner = p.isOwner(id);
    if (allows(player, p, Action.MEMBERS)) {
      add(dialog, "{acento}＋ {texto}Añadir miembro", "{suave}Puede construir y usar objetos", root + "formulario miembro");
      add(dialog, "{error}✖ {texto}Quitar miembro", "{suave}Elige a quién quitar", root + "miembros");
    }
    if (allows(player, p, Action.OWNERS)) {
      add(dialog, "{acento}＋ {texto}Añadir propietario", "{suave}Puede gestionar miembros", root + "formulario propietario");
      add(dialog, "{error}✖ {texto}Quitar propietario", "{suave}Elige a quién quitar", root + "propietarios");
    }
    if (allows(player, p, Action.RENAME))
      add(dialog, "{titulo}✎ {texto}Cambiar nombre", "{suave}Hasta 48 caracteres", root + "formulario nombre");
    if (allows(player, p, Action.BORDERS)) {
      boolean visible = boundaries.isViewing(player, p);
      add(
          dialog,
          visible ? "{dato}◈ {texto}Ocultar límites" : "{titulo}◈ {texto}Mostrar límites",
          "{suave}Partículas solo visibles para ti",
          root + "limites " + FROM_DIALOG);
    }
    // Los propietarios añadidos solo gestionan miembros (y sus límites); el resto ve la ficha.
    if (allows(player, p, Action.INFO) && (!owner || p.primaryOwner().equals(id)))
      add(dialog, "{titulo}ℹ {texto}Información", "{suave}Propietarios, miembros y medidas", root + "info");
    if (allows(player, p, Action.DELETE))
      add(dialog, "{error}✘ {texto}Eliminar protección", "{suave}Pide confirmación", root + "eliminar");
    if (allows(player, p, Action.FLAGS))
      add(dialog, "{worldguard}⚑ {texto}Banderas de WorldGuard", "{suave}Solo administración", root + "formulario bandera");
    if (allows(player, p, Action.TRANSFER))
      add(dialog, "{admin}⇄ {texto}Transferir creador", "{suave}Solo administración", root + "formulario transferir");
    if (hasAdmin(player))
      add(dialog, "{admin}✦ Administración", "{suave}Herramientas para el staff", "/pr admin");
    // La guía siempre queda debajo de todo, como botón inferior del diálogo.
    dialog.add(
        "exit_action",
        button("{titulo}۞ {texto}Guía de protecciones", null, root + "guia"));
    return show(player, dialog);
  }

  /** Lista de miembros o propietarios con un botón por jugador para quitarlo. */
  public boolean roles(Player player, Protection p, boolean owners, String notice) {
    if (!supported(player)) return false;
    Set<UUID> ids = owners ? p.owners() : p.members();
    var dialog = base((owners ? "Propietarios" : "Miembros") + " · " + p.name());
    notice(dialog, notice);
    body(
        dialog,
        ids.isEmpty()
            ? "{suave}Todavía no hay " + (owners ? "propietarios añadidos." : "miembros.")
            : "{texto}Pulsa sobre un jugador para quitarle el acceso.");
    String root = root(p);
    String kind = owners ? "propietario" : "miembro";
    sorted(ids).stream()
        .limit(40)
        .forEach(
            uuid ->
                add(
                    dialog,
                    "{error}✖ {dato}" + ownerName(uuid),
                    "{suave}Quitar a " + ownerName(uuid),
                    root + kind + " quitar " + uuid + " " + FROM_DIALOG));
    add(dialog, "{acento}＋ {texto}Añadir " + kind, null, root + "formulario " + kind);
    dialog.add("exit_action", button("{titulo}◀ {texto}Volver", null, root + "menu"));
    return show(player, dialog);
  }

  public boolean info(Player player, Protection p) {
    if (!supported(player)) return false;
    var dialog = base("Información · " + p.name());
    body(dialog, "{suave}Creador: {dato}" + ownerName(p.primaryOwner()));
    body(dialog, "{suave}Propietarios: {dato}" + names(p.owners()));
    body(dialog, "{suave}Miembros: {dato}" + names(p.members()));
    body(dialog, "{suave}Tamaño: {dato}" + size(p));
    body(dialog, "{suave}" + place(p));
    if (p.anchor() != null)
      body(dialog, "{suave}Bloque protector: {dato}" + pretty(p.anchor().block().material()));
    if (!p.flags().isEmpty()) {
      List<String> flags = new ArrayList<>();
      p.flags().forEach((k, v) -> flags.add(k + "=" + (v.equals("ALLOW") ? "permitir" : "denegar")));
      flags.sort(String::compareTo);
      body(dialog, "{suave}Banderas: {dato}" + String.join(", ", flags));
    }
    body(dialog, "{suave}ID: {dato}" + p.id());
    add(dialog, "{titulo}◀ {texto}Volver al menú", null, root(p) + "menu");
    if (player.hasPermission("viciontprotections.user.list"))
      add(dialog, "{titulo}۞ {texto}Mis protecciones", null, "/pr lista");
    dialog.add("exit_action", button("{titulo}۞ {texto}Guía de protecciones", null, root(p) + "guia"));
    return show(player, dialog);
  }

  public boolean list(Player player, List<Protection> list, int page, int pages, boolean all) {
    if (!supported(player)) return false;
    var dialog =
        base((all ? "Todas las protecciones" : "Mis protecciones") + (pages > 1 ? " · " + page + "/" + pages : ""));
    if (list.isEmpty()) {
      body(
          dialog,
          all
              ? "{suave}No hay protecciones en el servidor."
              : "{suave}Aún no tienes protecciones. Consigue un bloque protector y colócalo.");
      add(dialog, "{titulo}۞ {texto}Ver la guía", null, "/pr guia");
    } else {
      body(
          dialog,
          "{texto}"
              + (all ? "Hay {dato}" : "Tienes acceso a {dato}")
              + list.size()
              + (list.size() == 1 ? " {texto}protección." : " {texto}protecciones.")
              + " {suave}Pulsa una para abrir su menú.");
      String base = all ? "/pr lista todas " : "/pr lista ";
      for (Protection p : list.subList((page - 1) * PAGE, Math.min(page * PAGE, list.size()))) {
        String tooltip =
            "{texto}"
                + p.name()
                + "\n{suave}Rol: {dato}"
                + role(p, player.getUniqueId())
                + "\n{suave}Creador: {dato}"
                + ownerName(p.primaryOwner())
                + "\n{suave}Tamaño: {dato}"
                + size(p)
                + "\n{suave}"
                + place(p);
        add(dialog, "{dato}" + p.name(), tooltip, "/pr @" + p.id() + " menu");
      }
      if (page > 1) add(dialog, "{titulo}◀ {texto}Anterior", null, base + (page - 1));
      if (page < pages) add(dialog, "{texto}Siguiente {titulo}▶", null, base + (page + 1));
    }
    dialog.add("exit_action", button("{texto}Cerrar", null, null));
    return show(player, dialog);
  }

  public boolean admin(Player player, String notice) {
    if (!supported(player)) return false;
    var dialog = base("Administración");
    notice(dialog, notice);
    body(dialog, "{texto}Herramientas para el staff. Todo queda limitado a tus permisos.");
    if (player.hasPermission("viciontprotections.admin.give"))
      add(dialog, "{admin}✦ {texto}Entregar bloque protector", "{suave}Cualquier bloque y tamaño", "/pr formulario dar");
    if (player.hasPermission("viciontprotections.admin.selection"))
      add(dialog, "{worldedit}✂ {texto}Proteger selección", "{suave}Usa tu selección de WorldEdit", "/pr formulario seleccion");
    if (player.hasPermission("viciontprotections.admin.list"))
      add(dialog, "{admin}☰ {texto}Todas las protecciones", null, "/pr lista todas");
    if (player.hasPermission("viciontprotections.admin.reload"))
      add(dialog, "{admin}⟳ {texto}Recargar ajustes", "{suave}config.yml y messages.yml", "/pr recargar " + FROM_DIALOG);
    dialog.add("exit_action", button("{titulo}۞ {texto}Guía de protecciones", null, "/pr guia"));
    return show(player, dialog);
  }

  /** Formularios con campos de texto u opciones. */
  public boolean form(Player player, Protection p, String operation, String notice) {
    if (!supported(player)) return false;
    String root = p == null ? "/pr " : root(p);
    String back = p == null ? "/pr admin" : root + "menu";
    JsonObject dialog;
    String template;
    String confirm;
    switch (operation) {
      case "miembro", "propietario" -> {
        boolean owners = operation.equals("propietario");
        dialog = base((owners ? "Añadir propietario" : "Añadir miembro") + " · " + p.name());
        notice(dialog, notice);
        body(
            dialog,
            owners
                ? "{texto}Los propietarios construyen y gestionan miembros. Solo el creador gestiona propietarios."
                : "{texto}Los miembros pueden construir, abrir cofres y usar objetos.");
        body(dialog, "{suave}Escribe el nombre de un jugador que haya entrado al servidor, o su UUID.");
        text(dialog, "jugador", "Jugador", "", 36);
        template = root + operation + " añadir $(jugador) " + FROM_DIALOG;
        confirm = "{acento}＋ {texto}Añadir";
        addDynamic(dialog, confirm, template);
        // Accesos rápidos: jugadores conectados (y miembros, para ascenderlos a propietario).
        Set<UUID> quick = new LinkedHashSet<>();
        if (owners) quick.addAll(p.members());
        for (Player online : Bukkit.getOnlinePlayers())
          if (!p.canAccess(online.getUniqueId())) quick.add(online.getUniqueId());
        quick.stream()
            .limit(12)
            .forEach(
                uuid ->
                    add(
                        dialog,
                        "{acento}＋ {dato}" + ownerName(uuid),
                        null,
                        root + operation + " añadir " + uuid + " " + FROM_DIALOG));
        dialog.add("exit_action", button("{titulo}◀ {texto}Volver", null, back));
        return show(player, dialog);
      }
      case "nombre", "nombrar" -> {
        boolean fresh = operation.equals("nombrar");
        dialog = base(fresh ? "Nombra tu protección" : "Cambiar nombre");
        notice(dialog, notice);
        body(
            dialog,
            fresh
                ? "{texto}¡Tu protección está lista! Ponle un nombre para reconocerla."
                : "{texto}Nombre actual: {dato}" + p.name());
        body(dialog, "{suave}Hasta 48 letras, números, espacios, puntos, guiones o #.");
        text(dialog, "nombre", "Nombre", fresh ? "" : p.name(), 48);
        addDynamic(dialog, "{acento}✔ {texto}Guardar nombre", root + "nombre $(nombre) " + FROM_DIALOG);
        dialog.add(
            "exit_action",
            button(fresh ? "{suave}Más tarde" : "{titulo}◀ {texto}Volver", null, fresh ? null : back));
        return show(player, dialog);
      }
      case "transferir" -> {
        dialog = base("Transferir creador · " + p.name());
        notice(dialog, notice);
        body(dialog, "{texto}El nuevo creador tendrá el control total; el actual pasa a propietario.");
        text(dialog, "jugador", "Nuevo creador", "", 36);
        addDynamic(dialog, "{admin}⇄ {texto}Transferir", root + "transferir $(jugador) " + FROM_DIALOG);
        Set<UUID> quick = new LinkedHashSet<>(p.owners());
        quick.addAll(p.members());
        sorted(quick).stream()
            .limit(10)
            .forEach(
                uuid ->
                    add(dialog, "{admin}⇄ {dato}" + ownerName(uuid), null, root + "transferir " + uuid + " " + FROM_DIALOG));
        dialog.add("exit_action", button("{titulo}◀ {texto}Volver", null, back));
        return show(player, dialog);
      }
      case "bandera" -> {
        dialog = base("Banderas de WorldGuard · " + p.name());
        notice(dialog, notice);
        if (p.flags().isEmpty()) body(dialog, "{suave}Sin cambios: se aplican los valores de ViciontProtections.");
        else
          p.flags().entrySet().stream()
              .sorted(Map.Entry.comparingByKey())
              .forEach(
                  e ->
                      body(
                          dialog,
                          "{suave}"
                              + e.getKey()
                              + ": {dato}"
                              + (e.getValue().equals("ALLOW") ? "permitir" : "denegar")));
        body(dialog, "{suave}Otras banderas: {worldguard}/pr bandera <bandera> <valor>");
        List<String> flags =
            COMMON_FLAGS.stream().filter(manager.guard().editableFlags()::contains).toList();
        options(dialog, "bandera", "Bandera", flags.stream().map(f -> new String[] {f, f}).toList());
        options(
            dialog,
            "valor",
            "Valor",
            List.of(
                new String[] {"permitir", "Permitir"},
                new String[] {"denegar", "Denegar"},
                new String[] {"restablecer", "Restablecer"}));
        addDynamic(dialog, "{worldguard}⚑ {texto}Aplicar", root + "bandera $(bandera) $(valor) " + FROM_DIALOG);
        dialog.add("exit_action", button("{titulo}◀ {texto}Volver", null, back));
        return show(player, dialog);
      }
      case "dar" -> {
        dialog = base("Entregar bloque protector");
        notice(dialog, notice);
        body(
            dialog,
            "{texto}Cualquier bloque colocable. Las medidas son exactas y el bloque queda en el centro.");
        body(dialog, "{suave}Altura: un número o {dato}todo {suave}para cubrir del fondo al techo.");
        text(dialog, "jugador", "Jugador (conectado)", player.getName(), 16);
        text(dialog, "bloque", "Bloque", "DIAMOND_BLOCK", 64);
        text(dialog, "ancho", "Ancho (X)", "32", 7);
        text(dialog, "profundidad", "Profundidad (Z)", "32", 7);
        text(dialog, "altura", "Altura", "todo", 7);
        text(dialog, "cantidad", "Cantidad", "1", 2);
        addDynamic(
            dialog,
            "{admin}✦ {texto}Entregar",
            "/pr dar $(jugador) $(bloque) $(ancho) $(profundidad) $(altura) $(cantidad) " + FROM_DIALOG);
        dialog.add("exit_action", button("{titulo}◀ {texto}Volver", null, back));
        return show(player, dialog);
      }
      case "seleccion" -> {
        dialog = base("Proteger selección de WorldEdit");
        notice(dialog, notice);
        body(dialog, "{texto}Marca un cuboide con {worldedit}//pos1 {texto}y {worldedit}//pos2{texto}, después elige creador y nombre.");
        text(dialog, "jugador", "Creador", player.getName(), 36);
        text(dialog, "nombre", "Nombre", "", 48);
        addDynamic(dialog, "{worldedit}✂ {texto}Proteger", "/pr seleccion $(jugador) $(nombre) " + FROM_DIALOG);
        dialog.add("exit_action", button("{titulo}◀ {texto}Volver", null, back));
        return show(player, dialog);
      }
      default -> throw new IllegalArgumentException("Formulario desconocido.");
    }
  }

  public boolean confirmDelete(Player player, Protection p) {
    if (!supported(player)) return false;
    var dialog = new JsonObject();
    dialog.addProperty("type", "minecraft:confirmation");
    dialog.add("title", text("{titulo}۞ {texto}Eliminar protección"));
    dialog.addProperty("can_close_with_escape", true);
    dialog.add("body", new JsonArray());
    body(dialog, "{texto}¿Eliminar {dato}" + p.name() + "{texto}?");
    body(dialog, "{error}La zona dejará de estar protegida. El bloque protector no se devuelve.");
    body(dialog, "{suave}Si quieres recuperar el bloque, rómpelo en lugar de eliminar la protección.");
    dialog.add("yes", button("{error}✘ Sí, eliminar", null, root(p) + "eliminar confirmar"));
    dialog.add("no", button("{texto}Cancelar", null, root(p) + "menu"));
    return show(player, dialog);
  }

  // ---------------------------------------------------------------- JSON

  private static JsonElement text(String value) {
    return JsonParser.parseString(
        ComponentSerializer.toString(TextComponent.fromLegacyText(Messages.color(value))));
  }

  private static JsonObject base(String title) {
    var dialog = new JsonObject();
    dialog.addProperty("type", "minecraft:multi_action");
    dialog.add("title", text("{titulo}۞ <gradient:#D8B6FF:#F5B3DA>" + title + "</gradient>"));
    dialog.addProperty("columns", 2);
    dialog.addProperty("can_close_with_escape", true);
    dialog.add("body", new JsonArray());
    dialog.add("actions", new JsonArray());
    return dialog;
  }

  private static void body(JsonObject dialog, String line) {
    var item = new JsonObject();
    item.addProperty("type", "minecraft:plain_message");
    item.add("contents", text(line));
    item.addProperty("width", 320);
    dialog.getAsJsonArray("body").add(item);
  }

  private static void notice(JsonObject dialog, String notice) {
    if (notice != null && !notice.isBlank()) body(dialog, notice);
  }

  private static JsonObject click(String type, String key, String value) {
    var action = new JsonObject();
    action.addProperty("type", "minecraft:" + type);
    action.addProperty(key, value);
    return action;
  }

  private static JsonObject button(String label, String tooltip, String command) {
    var button = new JsonObject();
    button.add("label", text(label));
    if (tooltip != null) button.add("tooltip", text(tooltip));
    button.addProperty("width", 150);
    if (command != null) button.add("action", click("run_command", "command", command));
    return button;
  }

  private static void add(JsonObject dialog, String label, String tooltip, String command) {
    dialog.getAsJsonArray("actions").add(button(label, tooltip, command));
  }

  private static void addDynamic(JsonObject dialog, String label, String template) {
    var button = button(label, null, null);
    button.add("action", click("dynamic/run_command", "template", template));
    dialog.getAsJsonArray("actions").add(button);
  }

  private static void text(JsonObject dialog, String key, String label, String initial, int max) {
    var input = new JsonObject();
    input.addProperty("type", "minecraft:text");
    input.addProperty("key", key);
    input.add("label", text("{texto}" + label));
    input.addProperty("width", 260);
    input.addProperty("initial", initial);
    input.addProperty("max_length", max);
    inputs(dialog).add(input);
  }

  private static void options(JsonObject dialog, String key, String label, List<String[]> values) {
    var input = new JsonObject();
    input.addProperty("type", "minecraft:single_option");
    input.addProperty("key", key);
    input.add("label", text("{texto}" + label));
    input.addProperty("width", 260);
    var options = new JsonArray();
    boolean first = true;
    for (String[] value : values) {
      var option = new JsonObject();
      option.addProperty("id", value[0]);
      option.add("display", text("{dato}" + value[1]));
      if (first) option.addProperty("initial", true);
      first = false;
      options.add(option);
    }
    input.add("options", options);
    inputs(dialog).add(input);
  }

  private static JsonArray inputs(JsonObject dialog) {
    if (!dialog.has("inputs")) dialog.add("inputs", new JsonArray());
    return dialog.getAsJsonArray("inputs");
  }

  protected boolean show(Player player, JsonObject dialog) {
    if (dialog.has("actions") && dialog.getAsJsonArray("actions").isEmpty())
      add(dialog, "{texto}Cerrar", null, null);
    return transport.show(player, dialog);
  }

  // ---------------------------------------------------------------- utilidades

  private static boolean allows(Player player, Protection p, Action action) {
    return AccessPolicy.allows(player, p, action);
  }

  private static boolean hasAdmin(Player player) {
    for (String node : List.of("give", "selection", "list", "reload"))
      if (player.hasPermission("viciontprotections.admin." + node)) return true;
    return false;
  }

  private static String root(Protection p) {
    return "/pr @" + p.id() + " ";
  }

  public static String role(Protection p, UUID id) {
    return p.primaryOwner().equals(id)
        ? "Creador"
        : p.owners().contains(id) ? "Propietario" : p.members().contains(id) ? "Miembro" : "Ninguno";
  }

  public static String size(Protection p) {
    var b = p.bounds();
    World world = Bukkit.getWorld(p.worldName());
    boolean full =
        world == null
            ? b.height() > 4064
            : b.minY() <= world.getMinHeight() && b.maxY() >= world.getMaxHeight() - 1;
    return b.width() + " × " + b.depth() + (full ? " · altura completa" : " × " + b.height());
  }

  public static String place(Protection p) {
    var b = p.bounds();
    int x = p.anchor() != null ? p.anchor().x() : (b.minX() + b.maxX()) / 2;
    int z = p.anchor() != null ? p.anchor().z() : (b.minZ() + b.maxZ()) / 2;
    String y = p.anchor() != null ? p.anchor().y() + ", " : "";
    return "Mundo: {dato}" + p.worldName() + " {suave}· Centro: {dato}" + x + ", " + y + z;
  }

  public static String pretty(Material material) {
    String name = material.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    return Character.toUpperCase(name.charAt(0)) + name.substring(1);
  }

  public static List<UUID> sorted(Collection<UUID> ids) {
    return ids.stream()
        .sorted(Comparator.comparing(ProtectionDialogs::ownerName, String.CASE_INSENSITIVE_ORDER))
        .toList();
  }

  public static String names(Collection<UUID> ids) {
    return ids.isEmpty()
        ? "ninguno"
        : String.join(", ", sorted(ids).stream().map(ProtectionDialogs::ownerName).toList());
  }

  public static String ownerName(UUID id) {
    String name = Bukkit.getOfflinePlayer(id).getName();
    return name == null ? id.toString().substring(0, 8) : name;
  }
}
