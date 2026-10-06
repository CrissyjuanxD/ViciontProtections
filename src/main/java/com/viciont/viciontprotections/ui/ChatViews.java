package com.viciont.viciontprotections.ui;

import static com.viciont.viciontprotections.ui.Messages.button;
import static com.viciont.viciontprotections.ui.Messages.component;

import com.viciont.viciontprotections.models.Protection;
import com.viciont.viciontprotections.security.AccessPolicy;
import com.viciont.viciontprotections.security.AccessPolicy.Action;
import java.util.*;
import net.md_5.bungee.api.chat.BaseComponent;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Versión de chat de los menús: botones pulsables con descripción al pasar el ratón. */
public final class ChatViews {
  public static final int PAGE = 8;
  private final Messages messages;
  private final Boundaries boundaries;

  public ChatViews(Messages messages, Boundaries boundaries) {
    this.messages = messages;
    this.boundaries = boundaries;
  }

  public void list(
      CommandSender sender, List<Protection> list, int page, int pages, boolean all, UUID viewer) {
    messages.header(
        sender,
        all ? Messages.Theme.ADMIN : Messages.Theme.PUBLIC,
        (all ? "Todas las protecciones" : "Mis protecciones")
            + " {suave}· {dato}"
            + list.size()
            + (pages > 1 ? " {suave}· página {dato}" + page + "{suave}/{dato}" + pages : ""));
    if (list.isEmpty()) {
      messages.line(
          sender,
          all
              ? "{suave}No hay protecciones en el servidor."
              : "{suave}Aún no tienes protecciones. Consigue un bloque protector y colócalo.");
      return;
    }
    int number = (page - 1) * PAGE;
    for (Protection p : list.subList((page - 1) * PAGE, Math.min(page * PAGE, list.size()))) {
      number++;
      String details =
          "{titulo}"
              + p.name()
              + "\n{suave}Creador: {dato}"
              + ProtectionDialogs.ownerName(p.primaryOwner())
              + (viewer == null ? "" : "\n{suave}Tu rol: {dato}" + ProtectionDialogs.role(p, viewer))
              + "\n{suave}Tamaño: {dato}"
              + ProtectionDialogs.size(p)
              + "\n{suave}"
              + ProtectionDialogs.place(p)
              + "\n{suave}ID: {dato}#"
              + p.shortId()
              + "\n\n{acento}Clic para ver la información";
      messages.components(
          sender,
          false,
          component("{suave}" + number + ". "),
          button("{dato}" + p.name(), details, "/pr @" + p.id() + " info", true),
          component(
              " {suave}· "
                  + p.worldName()
                  + " · "
                  + p.bounds().width()
                  + "×"
                  + p.bounds().depth()
                  + (all ? " · " + ProtectionDialogs.ownerName(p.primaryOwner()) : "")
                  + " "),
          button("{titulo}[Menú]", "{texto}Abrir el menú de " + p.name(), "/pr @" + p.id() + " menu", true));
    }
    if (pages > 1) {
      String base = all ? "/pr lista todas " : "/pr lista ";
      List<BaseComponent> nav = new ArrayList<>();
      nav.add(
          page > 1
              ? button("{acento}◀ Anterior", "{texto}Página " + (page - 1), base + (page - 1), true)
              : component("{suave}◀ Anterior"));
      nav.add(component("  {suave}" + page + "/" + pages + "  "));
      nav.add(
          page < pages
              ? button("{acento}Siguiente ▶", "{texto}Página " + (page + 1), base + (page + 1), true)
              : component("{suave}Siguiente ▶"));
      messages.components(sender, false, nav.toArray(BaseComponent[]::new));
    }
  }

  public void info(CommandSender sender, Protection p) {
    messages.header(sender, Messages.Theme.PUBLIC, p.name() + " {suave}· {dato}#" + p.shortId());
    messages.line(sender, "{suave}Creador: {dato}" + ProtectionDialogs.ownerName(p.primaryOwner()));
    messages.line(sender, "{suave}Propietarios: {dato}" + ProtectionDialogs.names(p.owners()));
    messages.line(sender, "{suave}Miembros: {dato}" + ProtectionDialogs.names(p.members()));
    messages.line(sender, "{suave}Tamaño: {dato}" + ProtectionDialogs.size(p));
    messages.line(sender, "{suave}" + ProtectionDialogs.place(p));
    if (p.anchor() != null)
      messages.line(
          sender,
          "{suave}Bloque protector: {dato}" + ProtectionDialogs.pretty(p.anchor().block().material()));
    if (!p.flags().isEmpty()) {
      List<String> flags = new ArrayList<>();
      p.flags().forEach((k, v) -> flags.add(k + "=" + (v.equals("ALLOW") ? "permitir" : "denegar")));
      flags.sort(String::compareTo);
      messages.line(sender, "{suave}Banderas: {dato}" + String.join(", ", flags));
    }
  }

  /** Menú de chat de una protección para clientes sin diálogos. */
  public void menu(Player player, Protection p) {
    UUID id = player.getUniqueId();
    String root = "/pr @" + p.id() + " ";
    messages.header(
        player,
        Messages.Theme.PUBLIC,
        p.name() + " {suave}· tu rol: {dato}" + role(p, id));
    messages.line(
        player,
        "{suave}Creador: {dato}"
            + ProtectionDialogs.ownerName(p.primaryOwner())
            + " {suave}· Propietarios: {dato}"
            + p.owners().size()
            + " {suave}· Miembros: {dato}"
            + p.members().size()
            + " {suave}· {dato}"
            + ProtectionDialogs.size(p));
    List<BaseComponent> row = new ArrayList<>();
    if (AccessPolicy.allows(player, p, Action.MEMBERS)) {
      row.add(button("{acento}[＋ Miembro] ", "{texto}Escribe el jugador tras el comando", root + "miembro añadir ", false));
      row.add(button("{error}[✖ Miembro] ", "{texto}Ver miembros para quitarlos", root + "miembros", true));
    }
    if (AccessPolicy.allows(player, p, Action.OWNERS)) {
      row.add(button("{acento}[＋ Propietario] ", "{texto}Escribe el jugador tras el comando", root + "propietario añadir ", false));
      row.add(button("{error}[✖ Propietario] ", "{texto}Ver propietarios para quitarlos", root + "propietarios", true));
    }
    if (!row.isEmpty()) messages.components(player, false, row.toArray(BaseComponent[]::new));
    row.clear();
    if (AccessPolicy.allows(player, p, Action.RENAME))
      row.add(button("{titulo}[✎ Nombre] ", "{texto}Escribe el nuevo nombre", root + "nombre ", false));
    if (AccessPolicy.allows(player, p, Action.BORDERS))
      row.add(
          button(
              boundaries.isViewing(player, p) ? "{dato}[◈ Ocultar límites] " : "{titulo}[◈ Límites] ",
              "{texto}Partículas solo visibles para ti",
              root + "limites",
              true));
    if (AccessPolicy.allows(player, p, Action.INFO))
      row.add(button("{titulo}[ℹ Info] ", "{texto}Propietarios, miembros y medidas", root + "info", true));
    if (AccessPolicy.allows(player, p, Action.DELETE))
      row.add(button("{error}[✘ Eliminar] ", "{texto}Pide confirmación", root + "eliminar", true));
    if (!row.isEmpty()) messages.components(player, false, row.toArray(BaseComponent[]::new));
    row.clear();
    if (AccessPolicy.allows(player, p, Action.FLAGS))
      row.add(button("{worldguard}[⚑ Banderas] ", "{texto}/pr bandera <bandera> <valor>", root + "bandera ", false));
    if (AccessPolicy.allows(player, p, Action.TRANSFER))
      row.add(button("{admin}[⇄ Transferir] ", "{texto}Escribe el nuevo creador", root + "transferir ", false));
    row.add(button("{titulo}[۞ Guía]", "{texto}Cómo funcionan las protecciones", "/pr guia", true));
    messages.components(player, false, row.toArray(BaseComponent[]::new));
  }

  /** Miembros o propietarios, con un botón para quitar a cada uno si puedes gestionarlos. */
  public void roles(CommandSender sender, Protection p, boolean owners) {
    Set<UUID> ids = owners ? p.owners() : p.members();
    messages.header(
        sender,
        Messages.Theme.PUBLIC,
        (owners ? "Propietarios de " : "Miembros de ") + p.name() + " {suave}· {dato}" + ids.size());
    if (owners)
      messages.line(sender, "{suave}Creador: {dato}" + ProtectionDialogs.ownerName(p.primaryOwner()));
    if (ids.isEmpty()) {
      messages.line(sender, "{suave}Ninguno todavía.");
      return;
    }
    boolean manage = AccessPolicy.allows(sender, p, owners ? Action.OWNERS : Action.MEMBERS);
    String kind = owners ? "propietario" : "miembro";
    for (UUID uuid : ProtectionDialogs.sorted(ids)) {
      String name = ProtectionDialogs.ownerName(uuid);
      if (manage)
        messages.components(
            sender,
            false,
            component("{suave}۞ {dato}" + name + " "),
            button(
                "{error}[✖ Quitar]",
                "{texto}Quitar a " + name,
                "/pr @" + p.id() + " " + kind + " quitar " + uuid,
                true));
      else messages.line(sender, "{dato}" + name);
    }
  }

  public void confirmDelete(CommandSender sender, Protection p) {
    messages.send(
        sender,
        "{texto}¿Eliminar {dato}"
            + p.name()
            + "{texto}? La zona dejará de estar protegida y el bloque no se devuelve.");
    messages.components(
        sender,
        false,
        button(
            "{error}[✘ Sí, eliminar]",
            "{texto}Eliminar definitivamente",
            "/pr @" + p.id() + " eliminar confirmar",
            true),
        component(" "),
        button("{texto}[Cancelar]", null, "/pr @" + p.id() + " info", true));
  }

  public void help(CommandSender sender) {
    messages.header(sender, Messages.Theme.PUBLIC, "Comandos de protecciones");
    entry(sender, "/pr", "Menú (desde 1.21.6) o esta ayuda", true);
    entry(sender, "/pr guia", "Cómo funcionan las protecciones", true);
    entry(sender, "/pr lista [página]", "Tus protecciones", true);
    entry(sender, "/pr info", "Datos de la protección donde estás", true);
    entry(sender, "/pr nombre <nombre>", "Cambia el nombre", false);
    entry(sender, "/pr miembro añadir|quitar <jugador>", "Gestiona miembros", false);
    entry(sender, "/pr propietario añadir|quitar <jugador>", "Gestiona propietarios", false);
    entry(sender, "/pr miembros · /pr propietarios", "Lista y quita jugadores", true);
    entry(sender, "/pr limites", "Muestra u oculta el borde", true);
    entry(sender, "/pr eliminar", "Elimina la protección (pide confirmación)", true);
    entry(sender, "/pr @<id> <acción>", "Actúa sobre una protección a distancia", false);
    boolean admin = false;
    for (String node : List.of("give", "selection", "flags", "transfer", "list", "reload", "manage"))
      admin |= sender.hasPermission("viciontprotections.admin." + node);
    if (!admin) return;
    messages.header(sender, Messages.Theme.ADMIN, "Administración");
    entry(sender, "/pr admin", "Panel de administración", true);
    entry(sender, "/pr dar <jugador> <bloque> <ancho> [prof.] [altura|todo] [cant.]", "Entrega bloques", false);
    entry(sender, "/pr lista todas [página]", "Todas las protecciones", true);
    entry(sender, "/pr transferir <jugador>", "Cambia el creador", false);
    entry(sender, "/pr recargar", "Recarga config.yml y messages.yml", true);
    messages.header(sender, Messages.Theme.WORLDGUARD, "Banderas");
    entry(sender, "/pr bandera <bandera> <permitir|denegar|restablecer>", "Ajusta WorldGuard", false);
    messages.header(sender, Messages.Theme.WORLDEDIT, "Selecciones");
    entry(sender, "/pr seleccion <creador> [nombre]", "Protege tu selección de WorldEdit", false);
  }

  private void entry(CommandSender sender, String usage, String description, boolean run) {
    String command = usage.split(" [<\\[]")[0].split(" · ")[0];
    messages.components(
        sender,
        false,
        component("{suave}۞ "),
        button(
            "{dato}" + usage,
            "{texto}" + description + "\n{acento}Clic para " + (run ? "ejecutarlo" : "escribirlo"),
            run ? command : command + " ",
            run),
        component(" {suave}— {texto}" + description));
  }

  private static String role(Protection p, UUID id) {
    String role = ProtectionDialogs.role(p, id);
    return role.equals("Ninguno") ? "Administración" : role;
  }
}
