package com.viciont.viciontprotections.commands;

import static com.viciont.viciontprotections.ui.ClanViews.FROM_DIALOG;

import com.viciont.viciontprotections.api.ProtectionException;
import com.viciont.viciontprotections.api.clan.*;
import com.viciont.viciontprotections.api.event.clan.ClanDisbandEvent;
import com.viciont.viciontprotections.clans.*;
import com.viciont.viciontprotections.models.ClanRequest;
import com.viciont.viciontprotections.models.Protection;
import com.viciont.viciontprotections.ui.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** /clan (alias /clanes) y /cc. Todo lo que hacen los menús pasa por aquí. */
public final class ClanCommand implements CommandExecutor, TabCompleter {
  private static final String USE = "viciontprotections.clan.use";

  private final JavaPlugin plugin;
  private final ClanManager manager;
  private final ClanFounding founding;
  private final ClanViews views;
  private final Boundaries boundaries;
  private final ClanMessages messages;

  public ClanCommand(
      JavaPlugin plugin, ClanManager manager, ClanFounding founding, ClanViews views, Boundaries boundaries) {
    this.plugin = plugin;
    this.manager = manager;
    this.founding = founding;
    this.views = views;
    this.boundaries = boundaries;
    this.messages = manager.messages();
  }

  /** Respuesta de un comando; lanzado desde un menú, el menú vuelve a abrirse con el resultado. */
  private final class Flow {
    final CommandSender sender;
    final Player player;
    Function<String, Boolean> onSuccess = notice -> false;
    Function<String, Boolean> onFailure = notice -> false;

    Flow(CommandSender sender, boolean dialog) {
      this.sender = sender;
      this.player = dialog && sender instanceof Player p ? p : null;
    }

    void views(Consumer<String> success, Consumer<String> failure) {
      onSuccess = notice -> {
        success.accept(notice);
        return true;
      };
      onFailure = notice -> {
        failure.accept(notice);
        return true;
      };
    }

    void succeed(ClanMessages.Theme theme, String text) {
      messages.send(sender, theme, text);
      if (player != null && player.isOnline()) onSuccess.apply("{acento}✔ {texto}" + text);
    }

    void fail(Throwable error) {
      String text = messages.describe(error, sender);
      messages.fail(sender, text);
      if (player != null && player.isOnline()) onFailure.apply("{error}✖ " + text);
    }

    <T> void await(CompletableFuture<T> future, ClanMessages.Theme theme, Function<T, String> text) {
      future.whenComplete(
          (value, error) ->
              sync(
                  () -> {
                    if (error != null) fail(error);
                    else {
                      String message = text.apply(value);
                      if (message != null) succeed(theme, message);
                      else if (player != null && player.isOnline()) onSuccess.apply(null);
                    }
                  }));
    }
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] arguments) {
    String[] args = Arrays.stream(arguments).filter(s -> !s.isBlank()).toArray(String[]::new);
    boolean dialog = args.length > 0 && args[args.length - 1].equals(FROM_DIALOG);
    if (dialog) args = Arrays.copyOf(args, args.length - 1);
    Flow flow = new Flow(sender, dialog);
    try {
      // /prclan sirve aunque los clanes estén desactivados: entonces solo muestra las protecciones
      if (command.getName().equals("prclan")) {
        if (args.length > 0 && args[0].equalsIgnoreCase("inicio")) views.home(sender, null);
        else views.hub(sender);
        return true;
      }
      if (!manager.settings().enabled() && !sender.hasPermission("viciontprotections.admin.clan.manage"))
        throw new ProtectionException("Los clanes están desactivados en este servidor.");
      if (command.getName().equals("cc")) clanChat(flow, args);
      else execute(flow, args);
    } catch (RuntimeException failure) {
      flow.fail(failure);
    }
    return true;
  }

  private void execute(Flow flow, String[] args) {
    CommandSender sender = flow.sender;
    String action = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "menu";
    String[] values = Arrays.copyOfRange(args, Math.min(1, args.length), args.length);
    switch (action) {
      case "guia", "guía", "guide" -> views.guide(sender, values.length > 0 ? values[0] : null);
      case "ayuda", "help", "comandos" -> views.guide(sender, "comandos");
      case "cerrar" -> {
        // Botón «Cerrar» de los diálogos en Spigot: el diálogo ya se cerró al pulsarlo.
      }
      case "admin" -> admin(flow, values);
      case "espiar", "spy" -> {
        Player player = player(sender);
        permission(sender, "viciontprotections.admin.clan.spy");
        boolean on = manager.chat().toggleSpy(player);
        flow.views(n -> views.admin(player, null, n), n -> views.admin(player, null, n));
        flow.succeed(ClanMessages.Theme.ADMIN, on ? "Ahora lees todos los chats de clan." : "Dejas de leer los chats de clan.");
      }
      default -> {
        permission(sender, USE);
        user(flow, action, values);
      }
    }
  }

  private void user(Flow flow, String action, String[] values) {
    CommandSender sender = flow.sender;
    switch (action) {
      case "menu" -> {
        if (sender instanceof Player player) views.open(player, null);
        else views.guide(sender, "comandos");
      }
      case "lista", "list" -> views.list(sender, values.length > 0 ? number(values[0]) : 1);
      case "mios", "míos", "mine" -> views.mine(player(sender), null);
      case "info" -> {
        Clan clan = values.length == 0 ? myClan(player(sender)) : manager.resolve(String.join(" ", values));
        views.info(sender, clan);
      }
      case "fundar", "crear" -> found(flow, values);
      case "aceptar", "accept" -> {
        Player player = player(sender);
        Clan clan = clanArgument(values, "/clan aceptar <clan>");
        flow.views(n -> views.open(player, n), n -> views.noClan(player, n));
        flow.await(
            manager.acceptInvite(player, clan),
            ClanMessages.Theme.PUBLIC,
            c -> "Ahora eres " + manager.getRankName(c.rank(player.getUniqueId()).orElse(ClanRank.GUEST)) + " de " + ClanViews.paint(c) + "{texto}. Abre su menú con {dato}/clan{texto}.");
      }
      case "rechazar", "reject" -> {
        Player player = player(sender);
        Clan clan = clanArgument(values, "/clan rechazar <clan>");
        flow.views(n -> views.noClan(player, n), n -> views.noClan(player, n));
        flow.await(manager.rejectInvite(player, clan), ClanMessages.Theme.PUBLIC, v -> "Rechazaste la invitación de " + ClanViews.paint(clan) + "{texto}.");
      }
      case "invitacion", "invitación" -> {
        Player player = player(sender);
        Clan clan = clanArgument(values, "/clan invitacion <clan>");
        ClanRequest invite =
            manager.invitations(player.getUniqueId()).stream()
                .filter(r -> r.clan().equals(clan.id()))
                .findFirst()
                .orElseThrow(() -> new ProtectionException("Esa invitación ya no está disponible."));
        views.invitation(player, clan, invite);
      }
      case "activo", "active" -> {
        Player player = player(sender);
        Clan clan = clanArgument(values, "/clan activo <clan>");
        flow.views(n -> views.open(player, n), n -> views.open(player, n));
        flow.await(manager.setActive(player.getUniqueId(), clan), ClanMessages.Theme.PUBLIC, v -> "Ahora muestras el clan " + ClanViews.paint(clan) + "{texto}.");
      }
      case "chat" -> {
        Player player = player(sender);
        Clan clan = myClan(player);
        if (values.length == 0) views.chat(player, clan, null);
        else {
          manager.chat().send(player, clan, String.join(" ", values));
          if (flow.player != null) views.chat(player, clan, null);
        }
      }
      case "modo" -> {
        Player player = player(sender);
        Clan clan = myClan(player);
        flow.views(n -> views.chat(player, clan, n), n -> views.chat(player, clan, n));
        toggleMode(flow, player);
      }
      case "salir", "leave" -> {
        Player player = player(sender);
        Clan clan = myClan(player);
        if (!confirmed(values, 0)) {
          views.confirmLeave(player, clan);
          return;
        }
        flow.views(n -> views.open(player, n), n -> views.open(player, n));
        flow.await(manager.leave(player, clan), ClanMessages.Theme.PUBLIC, c -> "Has salido del clan " + ClanViews.paint(clan) + "{texto}.");
      }
      case "limites", "límites", "borders" -> borders(flow);
      case "territorio", "territory" -> territory(flow, values);
      case "territorios" -> {
        Player player = player(sender);
        views.territories(player, myClan(player), null);
      }
      case "solicitudes", "requests" -> {
        Player player = player(sender);
        Clan clan = myClan(player);
        manager.requirePermission(clan, player, "territorios");
        views.requests(player, clan, null);
      }
      case "miembros", "members" -> {
        Player player = player(sender);
        views.members(player, myClan(player), null);
      }
      case "miembro", "member" -> {
        Player player = player(sender);
        require(values.length == 1, "Uso: /clan miembro <jugador>");
        views.member(player, myClan(player), target(values[0]), null);
      }
      case "invitar", "invite" -> invite(flow, values);
      case "expulsar", "kick" -> {
        Player player = player(sender);
        Clan clan = myClan(player);
        require(values.length == 1, "Uso: /clan expulsar <jugador>");
        UUID target = target(values[0]);
        flow.views(n -> views.members(player, fresh(clan), n), n -> views.member(player, fresh(clan), target, n));
        flow.await(manager.kick(player, clan, target), ClanMessages.Theme.PUBLIC, c -> "{dato}" + ClanManager.name(target) + " {texto}ya no está en el clan.");
      }
      case "rango", "rank", "ascender", "degradar" -> rank(flow, action, values);
      case "transferir", "transfer" -> {
        Player player = player(sender);
        Clan clan = myClan(player);
        require(values.length >= 1, "Uso: /clan transferir <jugador>");
        UUID target = target(values[0]);
        if (!confirmed(values, 1)) {
          if (!clan.leader().equals(player.getUniqueId()))
            throw new ProtectionException("Solo el líder puede transferir el liderazgo.");
          views.confirmTransfer(player, clan, target);
          return;
        }
        flow.views(n -> views.open(player, n), n -> views.open(player, n));
        flow.await(manager.transfer(player, clan, target), ClanMessages.Theme.PUBLIC, c -> "{dato}" + ClanManager.name(target) + " {texto}es el nuevo líder de " + ClanViews.paint(c) + "{texto}.");
      }
      case "nombre", "name", "renombrar" -> {
        Player player = player(sender);
        Clan clan = myClan(player);
        require(values.length > 0, "Uso: /clan nombre <nombre>");
        flow.views(n -> views.settings(player, fresh(clan), n), n -> views.form(player, fresh(clan), "nombre", n));
        flow.await(manager.rename(player, clan, String.join(" ", values)), ClanMessages.Theme.PUBLIC, c -> "El clan ahora se llama " + ClanViews.paint(c) + "{texto}.");
      }
      case "color" -> {
        Player player = player(sender);
        Clan clan = myClan(player);
        require(values.length == 1, "Uso: /clan color <" + String.join("|", manager.settings().colors().keySet()) + ">");
        flow.views(n -> views.settings(player, fresh(clan), n), n -> views.form(player, fresh(clan), "color", n));
        flow.await(
            manager.recolor(player, clan, values[0]),
            ClanMessages.Theme.PUBLIC,
            c -> "Nuevo color del clan: " + ClanViews.paint(manager.settings().colorName(c.color()), c.color()) + "{texto}.");
      }
      case "bandera", "flag" -> {
        Player player = player(sender);
        Clan clan = myClan(player);
        flow.views(n -> views.settings(player, fresh(clan), n), n -> views.form(player, fresh(clan), "bandera", n));
        if (values.length == 0) {
          views.form(player, clan, "bandera", null);
          return;
        }
        require(values.length == 2, "Uso: /clan bandera <bandera> <permitir|denegar|restablecer>");
        String flag = values[0].toLowerCase(Locale.ROOT), value = values[1].toLowerCase(Locale.ROOT);
        flow.await(manager.setFlag(player, clan, flag, value), ClanMessages.Theme.PUBLIC, c -> "Bandera {dato}" + flag + " {texto}→ {dato}" + value + " {texto}en todos los territorios.");
      }
      case "permisos", "permissions" -> permissions(flow, values);
      case "ajustes", "settings" -> {
        Player player = player(sender);
        views.settings(player, myClan(player), null);
      }
      case "guerra", "war" -> war(flow, values);
      case "bloque", "block" -> {
        Player player = player(sender);
        Clan clan = myClan(player);
        String sub = values.length > 0 ? values[0].toLowerCase(Locale.ROOT) : "";
        var draft = founding.draft(player.getUniqueId()).filter(d -> clan.id().equals(d.clan()));
        switch (sub) {
          case "confirmar", "confirm", "mantener", "keep" -> {
            boolean keep = sub.equals("mantener") || sub.equals("keep");
            flow.views(
                n -> views.open(player, n),
                n -> {
                  var pending = founding.draft(player.getUniqueId()).filter(d -> clan.id().equals(d.clan()));
                  if (pending.isPresent()) views.confirmReplace(player, clan, pending.get(), n);
                  else views.open(player, n);
                });
            flow.await(
                founding.replace(player, keep),
                ClanMessages.Theme.PUBLIC,
                c -> "Bloque de clan cambiado" + (keep ? " en el mismo sitio" : "") + ": el territorio principal ahora mide {dato}"
                    + draft.map(d -> d.spec().dimensions()).orElse("lo del bloque nuevo")
                    + "{texto}. El bloque anterior está en tu inventario.");
          }
          case "cancelar", "cancel" -> {
            if (draft.isEmpty()) throw new ProtectionException("No tienes ningún cambio de bloque pendiente.");
            founding.cancel(player.getUniqueId(), true);
          }
          default -> {
            if (!clan.leader().equals(player.getUniqueId()))
              throw new ProtectionException("Solo el líder puede cambiar el bloque de clan.");
            if (draft.isPresent()) views.confirmReplace(player, clan, draft.get());
            else views.blockInfo(player, clan);
          }
        }
      }
      case "disolver", "disband" -> {
        Player player = player(sender);
        Clan clan = myClan(player);
        if (!clan.leader().equals(player.getUniqueId()))
          throw new ProtectionException("Solo el líder puede disolver el clan.");
        if (!confirmed(values, 0)) {
          views.confirmDissolve(player, clan);
          return;
        }
        flow.views(n -> views.open(player, n), n -> views.open(player, n));
        flow.await(
            manager.dissolve(clan.id(), ClanDisbandEvent.Cause.LEADER, "decisión de su líder", player),
            ClanMessages.Theme.PUBLIC,
            v -> "Has disuelto el clan " + ClanViews.paint(clan) + "{texto}.");
      }
      case "formulario", "form" -> {
        Player player = player(sender);
        require(values.length == 1, "Formulario no válido.");
        Clan clan = myClan(player);
        if (!views.form(player, clan, values[0].toLowerCase(Locale.ROOT), null))
          throw new ProtectionException("Formulario no válido.");
      }
      default -> throw new ProtectionException("Comando desconocido. Consulta /clan guia comandos.");
    }
  }

  // ------------------------------------------------------------------ fundar y chat

  private void found(Flow flow, String[] values) {
    Player player = player(flow.sender);
    permission(player, "viciontprotections.clan.create");
    boolean choice = !manager.backupsOf(player.getUniqueId()).isEmpty();
    if (values.length == 0) {
      if (choice && founding.draft(player.getUniqueId()).isPresent()) views.foundChoice(player, null);
      else views.found(player, null);
      return;
    }
    if (values[0].equalsIgnoreCase("nuevo")) {
      views.found(player, null);
      return;
    }
    if (values[0].equalsIgnoreCase("recuperar")) {
      require(values.length >= 2, "Uso: /clan fundar recuperar <clan>");
      String wanted = String.join(" ", Arrays.copyOfRange(values, 1, values.length));
      var copy =
          manager.backupsOf(player.getUniqueId()).stream()
              .filter(b -> ("@" + b.clan().id()).equalsIgnoreCase(wanted) || b.clan().name().equalsIgnoreCase(wanted))
              .findFirst()
              .orElseThrow(() -> new ProtectionException("No tienes ninguna copia de ese clan."));
      flow.views(n -> views.open(player, n), n -> views.foundChoice(player, n));
      flow.await(
          founding.restore(player, copy.clan().id()),
          ClanMessages.Theme.PUBLIC,
          c -> "Has recuperado el clan " + ClanViews.paint(c) + "{texto}. Su territorio principal es el del bloque que acabas de colocar.");
      return;
    }
    if (values[0].equalsIgnoreCase("cancelar")) {
      if (founding.draft(player.getUniqueId()).isEmpty()) throw new ProtectionException("No tienes ningún bloque de clan pendiente.");
      founding.cancel(player.getUniqueId(), true);
      return;
    }
    flow.views(n -> views.open(player, n), n -> views.found(player, n));
    require(values.length >= 2, "Uso: /clan fundar <color> <nombre>. Colores: " + String.join(", ", manager.settings().colors().keySet()) + ".");
    String name = String.join(" ", Arrays.copyOfRange(values, 1, values.length));
    flow.await(
        founding.found(player, values[0], name),
        ClanMessages.Theme.PUBLIC,
        c -> "¡Clan " + ClanViews.paint(c) + " {texto}fundado! Eres su líder. Invita a tu gente con {dato}/clan invitar <jugador>{texto}.");
  }

  private void clanChat(Flow flow, String[] args) {
    permission(flow.sender, USE);
    Player player = player(flow.sender);
    Clan clan = myClan(player);
    if (args.length == 0) toggleMode(flow, player);
    else manager.chat().send(player, clan, String.join(" ", args));
  }

  private void toggleMode(Flow flow, Player player) {
    boolean on = manager.chat().toggleMode(player);
    flow.succeed(
        ClanMessages.Theme.PUBLIC,
        on
            ? "Modo clan {acento}activado{texto}: lo que escribas irá al chat del clan. Escribe {dato}/cc {texto}para volver al público."
            : "Modo clan {suave}desactivado{texto}: vuelves al chat público.");
  }

  // ------------------------------------------------------------------ territorios

  private void borders(Flow flow) {
    Player player = player(flow.sender);
    Clan clan = myClan(player);
    manager.requirePermission(clan, player, "limites");
    Protection here = manager.protections().getProtectionAt(player.getLocation()).orElse(null);
    Protection target =
        here != null && clan.territories().containsKey(here.id())
            ? here
            : clan.home() == null ? null : manager.protections().getProtection(clan.home()).orElse(null);
    if (target == null) throw new ProtectionException("El clan no tiene territorio principal.");
    boolean visible = boundaries.toggle(player, target);
    flow.views(n -> views.open(player, n), n -> views.open(player, n));
    flow.succeed(
        ClanMessages.Theme.PUBLIC,
        visible ? "Límites de {dato}" + target.name() + " {texto}visibles. Solo tú ves las partículas." : "Límites de {dato}" + target.name() + " {texto}ocultos.");
  }

  private void territory(Flow flow, String[] values) {
    Player player = player(flow.sender);
    Clan clan = myClan(player);
    String sub = values.length > 0 ? values[0].toLowerCase(Locale.ROOT) : "";
    switch (sub) {
      case "aportar", "contribute" -> {
        manager.requirePermission(clan, player, "aportar");
        Protection protection =
            manager.protections().getProtectionAt(player.getLocation())
                .orElseThrow(() -> new ProtectionException("Ponte dentro de la protección que quieres aportar."));
        if (!protection.primaryOwner().equals(player.getUniqueId()))
          throw new ProtectionException("Solo puedes aportar protecciones que creaste tú.");
        if (manager.isTerritory(protection.id())) throw new ProtectionException("Esa protección ya es territorio de un clan.");
        if (!confirmed(values, 1)) {
          views.contribute(player, clan, protection);
          return;
        }
        flow.views(n -> views.territories(player, fresh(clan), n), n -> views.open(player, n));
        flow.await(
            manager.contribute(player, clan, protection),
            ClanMessages.Theme.PUBLIC,
            c -> c != null
                ? "{dato}" + protection.name() + " {texto}es ahora territorio del clan."
                : "Solicitud enviada: el líder o un sublíder decidirá si {dato}" + protection.name() + " {texto}pasa al clan.");
      }
      case "aceptar", "rechazar" -> {
        require(values.length == 2, "Uso: /clan territorio " + sub + " <jugador>");
        ClanRequest request = request(clan, values[1]);
        flow.views(n -> views.requests(player, fresh(clan), n), n -> views.requests(player, fresh(clan), n));
        if (sub.equals("aceptar"))
          flow.await(manager.acceptTerritory(player, clan, request), ClanMessages.Theme.PUBLIC, c -> "Territorio aceptado.");
        else
          flow.await(manager.rejectTerritory(player, clan, request), ClanMessages.Theme.PUBLIC, c -> "Solicitud rechazada.");
      }
      case "quitar", "devolver", "remove" -> {
        Protection protection;
        if (values.length >= 2 && values[1].startsWith("@"))
          protection =
              manager.protections().getProtection(uuid(values[1].substring(1)))
                  .orElseThrow(() -> new ProtectionException("Esa protección ya no existe."));
        else
          protection =
              manager.protections().getProtectionAt(player.getLocation())
                  .orElseThrow(() -> new ProtectionException("Ponte dentro del territorio que quieres devolver."));
        flow.views(n -> views.territories(player, fresh(clan), n), n -> views.territories(player, fresh(clan), n));
        flow.await(
            manager.removeTerritory(player, clan, protection.id()),
            ClanMessages.Theme.PUBLIC,
            c -> "{dato}" + protection.name() + " {texto}vuelve a {dato}" + ClanManager.name(territoryOwner(clan, protection)) + "{texto}.");
      }
      default -> views.territories(player, clan, null);
    }
  }

  private static UUID territoryOwner(Clan clan, Protection protection) {
    ClanTerritory territory = clan.territories().get(protection.id());
    return territory == null ? protection.primaryOwner() : territory.contributor();
  }

  private ClanRequest request(Clan clan, String value) {
    List<ClanRequest> list = manager.territoryRequests(clan.id());
    for (ClanRequest request : list) if (request.id().toString().equalsIgnoreCase(value)) return request;
    for (ClanRequest request : list) if (ClanManager.name(request.player()).equalsIgnoreCase(value)) return request;
    throw new ProtectionException("No hay ninguna solicitud de territorio de " + value + ".");
  }

  // ------------------------------------------------------------------ miembros y rangos

  private void invite(Flow flow, String[] values) {
    Player player = player(flow.sender);
    Clan clan = myClan(player);
    if (values.length == 0) {
      views.invite(player, clan, null);
      return;
    }
    UUID target = target(values[0]);
    ClanRank own = clan.rank(player.getUniqueId()).orElse(ClanRank.GUEST);
    ClanRank rank =
        values.length > 1
            ? rank(values[1])
            : own.isAbove(ClanRank.APPRENTICE) ? ClanRank.APPRENTICE : ClanRank.GUEST;
    flow.views(n -> views.invite(player, fresh(clan), n), n -> views.invite(player, fresh(clan), n));
    flow.await(
        manager.invite(player, clan, target, rank),
        ClanMessages.Theme.PUBLIC,
        request -> {
          Player online = Bukkit.getPlayer(target);
          if (online != null) views.invitationNotice(online, clan, request);
          return "Invitación enviada a {dato}" + ClanManager.name(target) + " {texto}como {dato}" + manager.getRankName(rank)
              + "{texto}. Caduca en {dato}" + manager.settings().inviteMillis() / 60_000 + " minutos{texto}.";
        });
  }

  private void rank(Flow flow, String action, String[] values) {
    Player player = player(flow.sender);
    Clan clan = myClan(player);
    require(values.length >= 1, "Uso: /clan rango <jugador> <rango>");
    UUID target = target(values[0]);
    ClanRank current = clan.rank(target).orElseThrow(() -> new ProtectionException("Ese jugador no está en el clan."));
    ClanRank rank =
        switch (action) {
          case "ascender" -> current.next().orElseThrow(() -> new ProtectionException("Ya tiene el rango más alto."));
          case "degradar" -> current.previous().orElseThrow(() -> new ProtectionException("Ya tiene el rango más bajo."));
          default -> {
            require(values.length == 2, "Uso: /clan rango <jugador> <rango>");
            yield rank(values[1]);
          }
        };
    flow.views(n -> views.member(player, fresh(clan), target, n), n -> views.member(player, fresh(clan), target, n));
    flow.await(
        manager.setRank(player, clan, target, rank),
        ClanMessages.Theme.PUBLIC,
        c -> "{dato}" + ClanManager.name(target) + " {texto}ahora es {dato}" + manager.getRankName(rank) + "{texto}.");
  }

  private void permissions(Flow flow, String[] values) {
    Player player = player(flow.sender);
    Clan clan = myClan(player);
    if (!clan.leader().equals(player.getUniqueId()))
      throw new ProtectionException("Solo el líder ajusta los permisos de los rangos.");
    if (values.length == 0) {
      views.permissions(player, clan, null, null);
      return;
    }
    ClanRank rank = rank(values[0]);
    if (values.length == 1) {
      views.permissions(player, clan, rank, null);
      return;
    }
    String key = values[1].toLowerCase(Locale.ROOT);
    flow.views(n -> views.permissions(player, fresh(clan), rank, n), n -> views.permissions(player, fresh(clan), rank, n));
    flow.await(
        manager.togglePermission(player, clan, rank, key),
        ClanMessages.Theme.PUBLIC,
        c -> {
          String name = manager.permission(key).map(ClanPermission::name).orElse(key);
          return "{dato}" + name + (manager.allows(c, rank, key) ? " {texto}activado" : " {texto}quitado") + " para {dato}" + manager.getRankName(rank) + "{texto}.";
        });
  }

  // ------------------------------------------------------------------ guerras

  private void war(Flow flow, String[] values) {
    Player player = player(flow.sender);
    Clan clan = myClan(player);
    String sub = values.length > 0 ? values[0].toLowerCase(Locale.ROOT) : "";
    ClanWars wars = manager.wars();
    switch (sub) {
      case "declarar", "declare" -> {
        flow.views(n -> views.war(player, fresh(clan), n), n -> views.form(player, fresh(clan), "declarar", n));
        var target = manager.resolvePrefix(values, 1);
        if (target == null) throw new ProtectionException("Uso: /clan guerra declarar <clan> <motivo>");
        String reason = String.join(" ", Arrays.copyOfRange(values, target.getValue(), values.length));
        Clan enemy = target.getKey();
        flow.await(wars.declare(player, clan, enemy, reason), ClanMessages.Theme.WAR, w -> "Has declarado la guerra a " + ClanViews.paint(enemy) + "{texto}.");
      }
      case "rendirse", "rendicion", "rendición", "surrender" -> {
        int tribute = 0, from = 1;
        if (values.length > 1 && values[1].matches("\\d{1,9}")) {
          tribute = Integer.parseInt(values[1]);
          from = 2;
        }
        String terms = String.join(" ", Arrays.copyOfRange(values, Math.min(from, values.length), values.length));
        flow.views(n -> views.war(player, fresh(clan), n), n -> views.form(player, fresh(clan), "rendirse", n));
        flow.await(wars.surrender(player, clan, tribute, terms), ClanMessages.Theme.WAR, w -> "Tu clan se ha rendido. La guerra ha terminado.");
      }
      case "paz", "peace" -> {
        String terms = String.join(" ", Arrays.copyOfRange(values, 1, values.length));
        flow.views(n -> views.war(player, fresh(clan), n), n -> views.form(player, fresh(clan), "paz", n));
        flow.await(
            wars.peace(player, clan, terms),
            ClanMessages.Theme.WAR,
            w -> w.active() ? "Propuesta de paz enviada." : "Paz firmada. La guerra ha terminado.");
      }
      default -> views.war(player, clan, null);
    }
  }

  // ------------------------------------------------------------------ administración

  private void admin(Flow flow, String[] values) {
    CommandSender sender = flow.sender;
    boolean any =
        sender.hasPermission("viciontprotections.admin.clan.manage")
            || sender.hasPermission("viciontprotections.admin.clan.spy")
            || sender.hasPermission("viciontprotections.admin.clan.war");
    if (!any) throw new ProtectionException("No tienes permisos de administración de clanes.");
    if (values.length == 0) {
      if (sender instanceof Player player) views.admin(player, null, null);
      else views.list(sender, 1);
      return;
    }
    if (values[0].equalsIgnoreCase("guerra") && values.length >= 3 && values[1].equalsIgnoreCase("terminar")) {
      permission(sender, "viciontprotections.admin.clan.war");
      Clan clan = manager.resolve(String.join(" ", Arrays.copyOfRange(values, 2, values.length)));
      if (sender instanceof Player player) flow.views(n -> views.admin(player, fresh(clan), n), n -> views.admin(player, fresh(clan), n));
      flow.await(manager.wars().adminEnd(clan), ClanMessages.Theme.ADMIN, w -> "Guerra de " + ClanViews.paint(clan) + " {texto}terminada sin vencedor.");
      return;
    }
    permission(sender, "viciontprotections.admin.clan.manage");
    var found = manager.resolvePrefix(values, 0);
    if (found == null) throw new ProtectionException("No existe ese clan. Usa /clan admin <clan> [acción].");
    Clan clan = found.getKey();
    String[] rest = Arrays.copyOfRange(values, found.getValue(), values.length);
    String action = rest.length > 0 ? rest[0].toLowerCase(Locale.ROOT) : "";
    if (sender instanceof Player player) flow.views(n -> views.admin(player, fresh(clan), n), n -> views.admin(player, fresh(clan), n));
    ClanMessages.Theme theme = ClanMessages.Theme.ADMIN;
    switch (action) {
      case "" -> {
        if (sender instanceof Player player) views.admin(player, clan, null);
        else views.info(sender, clan);
      }
      case "info" -> views.info(sender, clan);
      case "añadir", "anadir", "add" -> {
        require(rest.length >= 2, "Uso: /clan admin <clan> añadir <jugador> [rango]");
        UUID target = target(rest[1]);
        ClanRank rank = rest.length > 2 ? rank(rest[2]) : ClanRank.APPRENTICE;
        flow.await(manager.addMember(clan.id(), target, rank), theme, c -> "{dato}" + ClanManager.name(target) + " {texto}añadido a " + ClanViews.paint(c) + " {texto}como {dato}" + manager.getRankName(rank) + "{texto}.");
      }
      case "quitar", "remove" -> {
        require(rest.length == 2, "Uso: /clan admin <clan> quitar <jugador>");
        UUID target = target(rest[1]);
        flow.await(manager.removeMember(clan.id(), target), theme, c -> "{dato}" + ClanManager.name(target) + " {texto}ya no está en " + ClanViews.paint(c) + "{texto}.");
      }
      case "rango", "rank", "lider", "líder", "leader" -> {
        boolean leader = !action.startsWith("rang") && !action.equals("rank");
        require(rest.length == (leader ? 2 : 3), leader ? "Uso: /clan admin <clan> lider <jugador>" : "Uso: /clan admin <clan> rango <jugador> <rango>");
        UUID target = target(rest[1]);
        ClanRank rank = leader ? ClanRank.LEADER : rank(rest[2]);
        flow.await(manager.setRank(clan.id(), target, rank), theme, c -> "{dato}" + ClanManager.name(target) + " {texto}es ahora {dato}" + manager.getRankName(rank) + " {texto}de " + ClanViews.paint(c) + "{texto}.");
      }
      case "nombre", "name" -> {
        require(rest.length >= 2, "Uso: /clan admin <clan> nombre <nombre>");
        flow.await(manager.rename(null, clan, String.join(" ", Arrays.copyOfRange(rest, 1, rest.length))), theme, c -> "El clan ahora se llama " + ClanViews.paint(c) + "{texto}.");
      }
      case "color" -> {
        require(rest.length == 2, "Uso: /clan admin <clan> color <color>");
        flow.await(manager.recolor(null, clan, rest[1]), theme, c -> "Nuevo color para " + ClanViews.paint(c) + "{texto}.");
      }
      case "disolver", "disband" -> {
        if (!confirmed(rest, 1)) {
          if (sender instanceof Player player) views.confirmAdminDissolve(player, clan);
          else throw new ProtectionException("Confirma con /clan admin " + clan.name() + " disolver confirmar");
          return;
        }
        flow.await(
            manager.dissolve(clan.id(), ClanDisbandEvent.Cause.ADMIN, "decisión de la administración", null),
            theme,
            v -> "Clan " + ClanViews.paint(clan) + " {texto}disuelto.");
      }
      case "guerra" -> {
        permission(sender, "viciontprotections.admin.clan.war");
        flow.await(manager.wars().adminEnd(clan), theme, w -> "Guerra de " + ClanViews.paint(clan) + " {texto}terminada sin vencedor.");
      }
      default -> throw new ProtectionException("Acciones: añadir, quitar, rango, lider, nombre, color, disolver, guerra.");
    }
  }

  // ------------------------------------------------------------------ utilidades

  private Clan myClan(Player player) {
    return manager.getPlayerClan(player.getUniqueId())
        .orElseThrow(() -> new ProtectionException("No perteneces a ningún clan. Consulta /clan guia para fundar uno o unirte."));
  }

  private Clan fresh(Clan clan) {
    return manager.getClan(clan.id()).orElse(clan);
  }

  private Clan clanArgument(String[] values, String usage) {
    require(values.length > 0, "Uso: " + usage);
    return manager.resolve(String.join(" ", values));
  }

  private static boolean confirmed(String[] values, int index) {
    return values.length > index && (values[index].equalsIgnoreCase("confirmar") || values[index].equalsIgnoreCase("confirm"));
  }

  private static ClanRank rank(String value) {
    return ClanRank.parse(value)
        .orElseThrow(() -> new ProtectionException("Rango desconocido: " + value + ". Usa lider, sublider, oficial, aprendiz o invitado."));
  }

  private void sync(Runnable task) {
    if (Bukkit.isPrimaryThread()) task.run();
    else if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, task);
  }

  private static void permission(CommandSender sender, String permission) {
    if (!sender.hasPermission(permission)) throw new ProtectionException("No tienes permiso para esa acción.");
  }

  private static Player player(CommandSender sender) {
    if (sender instanceof Player p) return p;
    throw new ProtectionException("Este comando solo lo puede usar un jugador.");
  }

  private static void require(boolean condition, String message) {
    if (!condition) throw new ProtectionException(message);
  }

  private static int number(String value) {
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException invalid) {
      throw new ProtectionException("Número no válido: " + value + ".");
    }
  }

  private static UUID uuid(String value) {
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException invalid) {
      throw new ProtectionException("Identificador no válido.");
    }
  }

  private static UUID target(String value) {
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException ignored) {
    }
    Player online = Bukkit.getPlayerExact(value);
    if (online != null) return online.getUniqueId();
    for (OfflinePlayer player : Bukkit.getOfflinePlayers())
      if (value.equalsIgnoreCase(player.getName())) return player.getUniqueId();
    throw new ProtectionException("No conozco a " + value + ". Debe haber entrado antes al servidor.");
  }

  // ------------------------------------------------------------------ autocompletado

  @Override
  public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
    if (command.getName().equals("cc") || command.getName().equals("prclan") || !sender.hasPermission(USE)) return List.of();
    List<String> options = new ArrayList<>();
    Player player = sender instanceof Player p ? p : null;
    Clan clan = player == null ? null : manager.getPlayerClan(player.getUniqueId()).orElse(null);
    if (args.length <= 1) {
      options.addAll(List.of("guia", "lista", "info", "mios"));
      if (clan == null) {
        options.addAll(List.of("aceptar", "rechazar"));
        if (founding != null && player != null && founding.draft(player.getUniqueId()).isPresent()) options.add("fundar");
      } else {
        options.addAll(List.of("chat", "miembros", "territorios", "territorio", "guerra", "limites", "ajustes"));
        UUID id = player.getUniqueId();
        if (manager.allows(clan, id, "invitar")) options.add("invitar");
        if (manager.allows(clan, id, "expulsar")) options.add("expulsar");
        if (manager.allows(clan, id, "rangos")) options.add("rango");
        if (manager.allows(clan, id, "territorios")) options.add("solicitudes");
        if (manager.allows(clan, id, "ajustes")) options.addAll(List.of("nombre", "color"));
        if (manager.allows(clan, id, "banderas")) options.add("bandera");
        if (clan.leader().equals(id)) options.addAll(List.of("permisos", "transferir", "disolver", "bloque"));
        else options.add("salir");
        if (manager.getPlayerClans(id).size() > 1) options.add("activo");
      }
      if (sender.hasPermission("viciontprotections.admin.clan.manage") || sender.hasPermission("viciontprotections.admin.clan.war"))
        options.add("admin");
      if (sender.hasPermission("viciontprotections.admin.clan.spy")) options.add("espiar");
      return complete(options, args);
    }
    String first = args[0].toLowerCase(Locale.ROOT);
    switch (first) {
      case "aceptar", "rechazar", "invitacion" -> {
        if (player != null)
          for (ClanRequest invite : manager.invitations(player.getUniqueId()))
            manager.getClan(invite.clan()).ifPresent(c -> options.add(c.name()));
      }
      case "info" -> manager.getClans().forEach(c -> options.add(c.name()));
      case "activo" -> {
        if (player != null) manager.getPlayerClans(player.getUniqueId()).forEach(c -> options.add(c.name()));
      }
      case "invitar" -> {
        if (args.length == 2)
          for (Player online : Bukkit.getOnlinePlayers())
            if (manager.getPlayerClans(online.getUniqueId()).isEmpty()) options.add(online.getName());
        if (args.length == 3) for (ClanRank rank : ClanRank.values()) if (rank != ClanRank.LEADER) options.add(rank.key());
      }
      case "expulsar", "miembro", "transferir", "ascender", "degradar" -> {
        if (clan != null && args.length == 2) clan.members().keySet().forEach(id -> options.add(ClanManager.name(id)));
      }
      case "rango" -> {
        if (clan != null && args.length == 2) clan.members().keySet().forEach(id -> options.add(ClanManager.name(id)));
        if (args.length == 3) for (ClanRank rank : ClanRank.values()) if (rank != ClanRank.LEADER) options.add(rank.key());
      }
      case "color" -> options.addAll(manager.settings().colors().keySet());
      case "bandera" -> {
        if (args.length == 2) options.addAll(manager.settings().allowedFlags());
        if (args.length == 3) options.addAll(List.of("permitir", "denegar", "restablecer"));
      }
      case "permisos" -> {
        if (args.length == 2) for (ClanRank rank : ClanRank.values()) if (rank != ClanRank.LEADER) options.add(rank.key());
        if (args.length == 3) manager.getPermissions().forEach(p -> options.add(p.key()));
      }
      case "territorio" -> {
        if (args.length == 2) options.addAll(List.of("aportar", "aceptar", "rechazar", "quitar"));
        if (args.length == 3 && clan != null && (args[1].equalsIgnoreCase("aceptar") || args[1].equalsIgnoreCase("rechazar")))
          manager.territoryRequests(clan.id()).forEach(r -> options.add(ClanManager.name(r.player())));
      }
      case "guerra" -> {
        if (args.length == 2) options.addAll(List.of("declarar", "rendirse", "paz"));
        if (args.length == 3 && args[1].equalsIgnoreCase("declarar"))
          manager.getClans().stream().filter(c -> clan == null || !c.id().equals(clan.id())).forEach(c -> options.add(c.name()));
      }
      case "salir", "disolver" -> options.add("confirmar");
      case "bloque" -> options.addAll(List.of("mantener", "confirmar", "cancelar"));
      case "guia" -> {
        manager.settings().topics().forEach(t -> options.add(t.key()));
        manager.extensionTopics().forEach(t -> options.add(t.key()));
        options.add("comandos");
      }
      case "admin" -> {
        if (args.length == 2) {
          manager.getClans().forEach(c -> options.add(c.name()));
          options.add("guerra");
        } else if (args[1].equalsIgnoreCase("guerra")) {
          if (args.length == 3) options.add("terminar");
          else manager.getClans().forEach(c -> options.add(c.name()));
        } else if (args.length == 3)
          options.addAll(List.of("añadir", "quitar", "rango", "lider", "nombre", "color", "disolver", "guerra", "info"));
        else if (args.length == 4) Bukkit.getOnlinePlayers().forEach(p -> options.add(p.getName()));
        else if (args.length == 5) for (ClanRank rank : ClanRank.values()) if (rank != ClanRank.LEADER) options.add(rank.key());
      }
      default -> {}
    }
    return complete(options, args);
  }

  private static List<String> complete(Collection<String> options, String[] args) {
    String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
    return options.stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(last)).distinct().sorted().toList();
  }
}
