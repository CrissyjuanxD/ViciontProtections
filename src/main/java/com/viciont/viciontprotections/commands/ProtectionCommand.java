package com.viciont.viciontprotections.commands;

import static com.viciont.viciontprotections.ui.ProtectionDialogs.FROM_DIALOG;
import static com.viciont.viciontprotections.ui.ProtectionDialogs.ownerName;

import com.viciont.viciontprotections.api.*;
import com.viciont.viciontprotections.integration.WorldEditBridge;
import com.viciont.viciontprotections.managers.ProtectionManager;
import com.viciont.viciontprotections.models.Protection;
import com.viciont.viciontprotections.security.AccessPolicy;
import com.viciont.viciontprotections.security.AccessPolicy.Action;
import com.viciont.viciontprotections.ui.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import java.util.stream.Stream;
import org.bukkit.*;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Bed;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class ProtectionCommand implements CommandExecutor, TabCompleter {
  private static final Set<String> ADD = Set.of("añadir", "anadir", "add", "agregar");
  private static final Set<String> REMOVE = Set.of("quitar", "del", "remove", "eliminar");

  private final JavaPlugin plugin;
  private final ProtectionManager manager;
  private final Messages messages;
  private final ProtectionDialogs dialogs;
  private final ChatViews chat;
  private final Boundaries boundaries;

  public ProtectionCommand(
      JavaPlugin plugin,
      ProtectionManager manager,
      Messages messages,
      ProtectionDialogs dialogs,
      ChatViews chat,
      Boundaries boundaries) {
    this.plugin = plugin;
    this.manager = manager;
    this.messages = messages;
    this.dialogs = dialogs;
    this.chat = chat;
    this.boundaries = boundaries;
  }

  /**
   * Respuesta de un comando. Si se lanzó desde un diálogo, al terminar se vuelve a abrir el diálogo
   * adecuado con el resultado dentro, para que el jugador no tenga que mirar el chat.
   */
  private final class Flow {
    final CommandSender sender;
    final Player player;
    Function<String, Boolean> onSuccess = notice -> false;
    Function<String, Boolean> onFailure = notice -> false;

    Flow(CommandSender sender, boolean dialog) {
      this.sender = sender;
      this.player = dialog && sender instanceof Player p ? p : null;
    }

    void views(Function<String, Boolean> success, Function<String, Boolean> failure) {
      onSuccess = success;
      onFailure = failure;
    }

    void succeed(Messages.Theme theme, String text) {
      messages.send(sender, theme, text);
      if (player != null && player.isOnline()) onSuccess.apply("{acento}✔ {texto}" + text);
    }

    void fail(Throwable error) {
      messages.error(sender, error);
      if (player != null && player.isOnline()) onFailure.apply("{error}✖ " + describe(error));
    }

    void await(CompletableFuture<?> future, Messages.Theme theme, Supplier<String> text, Runnable then) {
      future.whenComplete(
          (value, error) ->
              sync(
                  () -> {
                    if (error != null) fail(error);
                    else {
                      succeed(theme, text.get());
                      if (then != null) then.run();
                    }
                  }));
    }
  }

  @Override
  public boolean onCommand(
      CommandSender sender, Command command, String label, String[] arguments) {
    String[] args = clean(arguments);
    boolean dialog = args.length > 0 && args[args.length - 1].equals(FROM_DIALOG);
    if (dialog) args = Arrays.copyOf(args, args.length - 1);
    Flow flow = new Flow(sender, dialog);
    try {
      execute(flow, legacy(sender, command.getName(), args));
    } catch (RuntimeException failure) {
      flow.fail(failure);
    }
    return true;
  }

  private void execute(Flow flow, String[] args) {
    CommandSender sender = flow.sender;
    Protection selected = null;
    int offset = 0;
    if (args.length > 0 && args[0].startsWith("@")) {
      selected = manager.resolve(args[0].substring(1));
      offset = 1;
    }
    String action = args.length > offset ? args[offset].toLowerCase(Locale.ROOT) : "menu";
    String[] values = Arrays.copyOfRange(args, Math.min(offset + 1, args.length), args.length);
    switch (action) {
      case "guia", "guía", "guide" -> guide(sender, selected);
      case "ayuda", "help", "comandos" -> chat.help(sender);
      case "recargar", "reload" -> reload(flow);
      case "dar", "give" -> give(flow, values);
      case "seleccion", "selección", "selection" -> selection(flow, values);
      case "lista", "list" -> list(sender, values);
      case "admin" -> admin(sender);
      case "cerrar" -> {
        // Botón «Cerrar» de los diálogos en Spigot: el diálogo ya se cerró al pulsarlo.
      }
      default -> {
        if (action.equals("formulario")
            && values.length == 1
            && (values[0].equals("dar") || values[0].equals("seleccion"))) {
          permission(
              sender,
              values[0].equals("dar")
                  ? "viciontprotections.admin.give"
                  : "viciontprotections.admin.selection");
          if (!dialogs.form(player(sender), null, values[0], null)) chat.help(sender);
          return;
        }
        Protection protection = selected != null ? selected : current(sender);
        if (action.equals("menu") && !(sender instanceof Player)) chat.help(sender);
        else if (action.equals("menu")) menu(sender, protection, null);
        else if (protection == null)
          throw new ProtectionException(
              "No estás en ninguna protección. Entra en una o usa /pr lista.");
        else protectionAction(flow, protection, action, values);
      }
    }
  }

  private void protectionAction(Flow flow, Protection protection, String action, String[] values) {
    CommandSender sender = flow.sender;
    UUID id = protection.id();
    Messages.Theme theme = theme(sender, protection);
    switch (action) {
      case "info" -> {
        allowed(sender, protection, Action.INFO);
        if (!(sender instanceof Player p && dialogs.info(p, protection))) chat.info(sender, protection);
      }
      case "miembros", "members", "propietarios", "owners" -> {
        boolean owners = action.startsWith("prop") || action.equals("owners");
        allowed(sender, protection, Action.INFO);
        boolean manage =
            AccessPolicy.allows(sender, protection, owners ? Action.OWNERS : Action.MEMBERS);
        if (!(manage && sender instanceof Player p && dialogs.roles(p, protection, owners, null)))
          chat.roles(sender, protection, owners);
      }
      case "nombre", "name", "renombrar" -> {
        allowed(sender, protection, Action.RENAME);
        flow.views(
            notice -> menu(flow.player, id, notice),
            notice -> dialogs.form(flow.player, fresh(id), "nombre", notice));
        require(values.length > 0, "Uso: /pr nombre <nuevo nombre>");
        String name = ProtectionManager.validName(String.join(" ", values));
        flow.await(
            manager.renameProtection(id, name),
            theme,
            () -> "Nombre actualizado: {dato}" + name + "{texto}.",
            null);
      }
      case "miembro", "member", "propietario", "owner" -> {
        boolean owner = action.startsWith("prop") || action.equals("owner");
        String kind = owner ? "propietario" : "miembro";
        allowed(sender, protection, owner ? Action.OWNERS : Action.MEMBERS);
        boolean add = values.length > 0 && ADD.contains(values[0].toLowerCase(Locale.ROOT));
        flow.views(
            notice -> menu(flow.player, id, notice),
            notice ->
                add
                    ? dialogs.form(flow.player, fresh(id), kind, notice)
                    : dialogs.roles(flow.player, fresh(id), owner, notice));
        require(
            values.length == 2
                && (add || REMOVE.contains(values[0].toLowerCase(Locale.ROOT))),
            "Uso: /pr " + kind + " <añadir|quitar> <jugador>");
        UUID target = target(values[1]);
        String who = ownerName(target);
        CompletableFuture<Protection> future =
            owner
                ? add ? manager.addOwner(id, target) : manager.removeOwner(id, target)
                : add ? manager.addMember(id, target) : manager.removeMember(id, target);
        flow.await(
            future,
            theme,
            () ->
                "{dato}"
                    + who
                    + (add ? " {texto}ahora es " : " {texto}ya no es ")
                    + kind
                    + " de {dato}"
                    + protection.name()
                    + "{texto}.",
            () -> {
              Player online = Bukkit.getPlayer(target);
              if (online != null && online != sender)
                messages.send(
                    online,
                    "{dato}"
                        + sender.getName()
                        + (add ? " {texto}te añadió como " : " {texto}te quitó como ")
                        + kind
                        + " de {dato}"
                        + protection.name()
                        + "{texto}.");
            });
      }
      case "limites", "límites", "borders", "bordes" -> {
        allowed(sender, protection, Action.BORDERS);
        Player player = player(sender);
        boolean visible = boundaries.toggle(player, protection);
        flow.views(notice -> menu(player, id, notice), notice -> false);
        flow.succeed(
            Messages.Theme.PUBLIC,
            visible
                ? "Límites de {dato}" + protection.name() + " {texto}visibles. Solo tú ves las partículas."
                : "Límites de {dato}" + protection.name() + " {texto}ocultos.");
      }
      case "eliminar", "delete", "borrar" -> {
        allowed(sender, protection, Action.DELETE);
        if (values.length == 1
            && (values[0].equalsIgnoreCase("confirmar") || values[0].equalsIgnoreCase("confirm")))
          flow.await(
              manager.deleteProtection(id),
              theme,
              () -> "Eliminaste la protección {dato}" + protection.name() + "{texto}.",
              null);
        else if (!(sender instanceof Player p && dialogs.confirmDelete(p, protection)))
          chat.confirmDelete(sender, protection);
      }
      case "transferir", "transfer" -> {
        allowed(sender, protection, Action.TRANSFER);
        flow.views(
            notice -> menu(flow.player, id, notice),
            notice -> dialogs.form(flow.player, fresh(id), "transferir", notice));
        require(values.length == 1, "Uso: /pr transferir <jugador>");
        UUID target = target(values[0]);
        flow.await(
            manager.transferOwnership(id, target),
            Messages.Theme.ADMIN,
            () ->
                "{dato}"
                    + ownerName(target)
                    + " {texto}es ahora el creador de {dato}"
                    + protection.name()
                    + "{texto}.",
            null);
      }
      case "bandera", "flag", "banderas", "flags" -> {
        allowed(sender, protection, Action.FLAGS);
        flow.views(
            notice -> dialogs.form(flow.player, fresh(id), "bandera", notice),
            notice -> dialogs.form(flow.player, fresh(id), "bandera", notice));
        if (values.length == 0) {
          messages.send(
              sender,
              Messages.Theme.WORLDGUARD,
              "Banderas disponibles: {dato}" + String.join("{suave}, {dato}", manager.guard().editableFlags()));
          return;
        }
        require(values.length == 2, "Uso: /pr bandera <bandera> <permitir|denegar|restablecer>");
        String flag = values[0].toLowerCase(Locale.ROOT), value = values[1].toLowerCase(Locale.ROOT);
        flow.await(
            manager.setFlag(id, flag, value),
            Messages.Theme.WORLDGUARD,
            () ->
                "Bandera {dato}"
                    + flag
                    + " {texto}→ {dato}"
                    + (value.startsWith("perm") || value.equals("allow")
                        ? "permitir"
                        : value.startsWith("den") || value.equals("deny") ? "denegar" : "valor por defecto")
                    + " {texto}en {dato}"
                    + protection.name()
                    + "{texto}.",
            null);
      }
      case "formulario" -> {
        require(values.length == 1, "Formulario no válido.");
        Action needed =
            switch (values[0]) {
              case "miembro" -> Action.MEMBERS;
              case "propietario" -> Action.OWNERS;
              case "nombre" -> Action.RENAME;
              case "bandera" -> Action.FLAGS;
              case "transferir" -> Action.TRANSFER;
              default -> throw new ProtectionException("Formulario no válido.");
            };
        allowed(sender, protection, needed);
        if (!dialogs.form(player(sender), protection, values[0], null)) chat.menu(player(sender), protection);
      }
      default -> throw new ProtectionException("Comando desconocido. Consulta /pr ayuda.");
    }
  }

  // ---------------------------------------------------------------- vistas generales

  private void guide(CommandSender sender, Protection selected) {
    if (sender instanceof Player player) {
      Protection back = selected != null ? selected : current(sender);
      if (back != null
          && !back.canAccess(player.getUniqueId())
          && !player.hasPermission("viciontprotections.admin.manage")) back = null;
      if (dialogs.guide(player, back)) return;
    }
    messages.guideChat(sender);
    messages.components(
        sender,
        false,
        Messages.button("{acento}[Ver comandos] ", "{texto}/pr ayuda", "/pr ayuda", true),
        Messages.button("{titulo}[Mis protecciones]", "{texto}/pr lista", "/pr lista", true));
  }

  /** Menú de la protección, o la guía si no estás en ninguna a la que tengas acceso. */
  private boolean menu(Player player, UUID id, String notice) {
    if (player == null || !player.isOnline()) return false;
    Protection protection = manager.getProtection(id).orElse(null);
    menu(player, protection, notice);
    return true;
  }

  private void menu(CommandSender sender, Protection protection, String notice) {
    Player player = player(sender);
    if (protection != null
        && !protection.canAccess(player.getUniqueId())
        && !player.hasPermission("viciontprotections.admin.manage")) protection = null;
    if (protection != null) {
      if (!dialogs.menu(player, protection, notice)) chat.menu(player, protection);
    } else if (!dialogs.guide(player, null)) guide(player, null);
  }

  private void list(CommandSender sender, String[] values) {
    permission(sender, "viciontprotections.user.list");
    boolean all = !(sender instanceof Player);
    if (values.length > 0 && Set.of("todas", "todos", "all").contains(values[0].toLowerCase(Locale.ROOT))) {
      permission(sender, "viciontprotections.admin.list");
      all = true;
      values = Arrays.copyOfRange(values, 1, values.length);
    }
    int page = values.length > 0 ? number(values[0]) : 1;
    List<Protection> list =
        all ? manager.getProtections() : manager.getProtections(player(sender).getUniqueId());
    boolean dialog = sender instanceof Player p && dialogs.supported(p);
    int size = dialog ? 10 : ChatViews.PAGE;
    int pages = Math.max(1, (list.size() + size - 1) / size);
    page = Math.max(1, Math.min(page, pages));
    if (!(dialog && dialogs.list((Player) sender, list, page, pages, all)))
      chat.list(
          sender,
          list,
          page,
          Math.max(1, (list.size() + ChatViews.PAGE - 1) / ChatViews.PAGE),
          all,
          sender instanceof Player p ? p.getUniqueId() : null);
  }

  private void admin(CommandSender sender) {
    boolean any = false;
    for (String node : List.of("give", "selection", "list", "reload"))
      any |= sender.hasPermission("viciontprotections.admin." + node);
    if (!any) throw new ProtectionException("No tienes permisos de administración.");
    if (!(sender instanceof Player p && dialogs.admin(p, null))) chat.help(sender);
  }

  private void reload(Flow flow) {
    permission(flow.sender, "viciontprotections.admin.reload");
    flow.views(notice -> dialogs.admin(flow.player, notice), notice -> dialogs.admin(flow.player, notice));
    plugin.reloadConfig();
    messages.reload();
    manager.guard().setDenyMessage(messages.text("avisos.denegado"));
    manager.resync();
    flow.succeed(
        Messages.Theme.ADMIN,
        "config.yml y messages.yml recargados. Los cambios de base de datos se aplican al reiniciar.");
  }

  // ---------------------------------------------------------------- administración

  private void give(Flow flow, String[] args) {
    CommandSender sender = flow.sender;
    permission(sender, "viciontprotections.admin.give");
    flow.views(
        notice -> dialogs.form(flow.player, null, "dar", notice),
        notice -> dialogs.form(flow.player, null, "dar", notice));
    require(
        args.length >= 3 && args.length <= 6,
        "Uso: /pr dar <jugador> <bloque> <ancho> [profundidad] [altura|todo] [cantidad]");
    Player target = Bukkit.getPlayerExact(args[0]);
    if (target == null) throw new ProtectionException("El jugador " + args[0] + " debe estar conectado.");
    Material material = Material.matchMaterial(args[1]);
    if (material == null) throw new ProtectionException("Bloque desconocido: " + args[1] + ".");
    if (!material.isBlock() || !material.isItem() || material.isAir())
      throw new ProtectionException("Usa un bloque colocable, por ejemplo DIAMOND_BLOCK.");
    var data = material.createBlockData();
    if (data instanceof Bisected || data instanceof Bed)
      throw new ProtectionException("Usa un bloque de una sola pieza (no puertas, camas ni plantas altas).");
    int width = number(args[2]),
        depth = args.length >= 4 ? number(args[3]) : width,
        height =
            args.length >= 5 && !Set.of("todo", "toda", "all", "0").contains(args[4].toLowerCase(Locale.ROOT))
                ? number(args[4])
                : 0,
        amount = args.length >= 6 ? number(args[5]) : 1;
    int maximum = plugin.getConfig().getInt("protections.max-block-size", 4096);
    require(width >= 1 && depth >= 1 && height >= 0, "Las medidas deben ser positivas.");
    require(width <= maximum && depth <= maximum, "El tamaño máximo configurado es " + maximum + " bloques.");
    require(amount >= 1 && amount <= 64, "La cantidad debe estar entre 1 y 64.");
    ProtectionBlock spec = new ProtectionBlock(material, width, depth, height);
    for (int left = amount; left > 0; left -= material.getMaxStackSize())
      giveItem(target, manager.createProtectionBlock(spec, Math.min(left, material.getMaxStackSize())));
    flow.succeed(
        Messages.Theme.ADMIN,
        "Entregado a {dato}"
            + target.getName()
            + "{texto}: {dato}"
            + amount
            + " × "
            + ProtectionDialogs.pretty(material)
            + " {texto}("
            + spec.dimensions()
            + ").");
    if (target != sender)
      messages.send(
          target,
          "Has recibido {dato}"
              + amount
              + (amount == 1 ? " bloque protector" : " bloques protectores")
              + " {texto}de {dato}"
              + spec.dimensions()
              + "{texto}. Colócalo para proteger tu zona.");
  }

  public static void giveItem(Player player, ItemStack item) {
    player
        .getInventory()
        .addItem(item)
        .values()
        .forEach(
            left -> {
              var drop = player.getWorld().dropItemNaturally(player.getLocation(), left);
              drop.setOwner(player.getUniqueId());
            });
  }

  private void selection(Flow flow, String[] args) {
    permission(flow.sender, "viciontprotections.admin.selection");
    Player player = player(flow.sender);
    flow.views(
        notice -> dialogs.admin(player, notice),
        notice -> dialogs.form(player, null, "seleccion", notice));
    require(args.length >= 1, "Uso: /pr seleccion <creador> [nombre]");
    UUID owner = target(args[0]);
    String name = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : null;
    var request =
        CreateProtectionRequest.region(
            owner, player.getWorld().getName(), new WorldEditBridge().selection(player), name);
    manager.guard().validate(request, player);
    CompletableFuture<Protection> future = manager.createProtection(request);
    flow.await(
        future,
        Messages.Theme.WORLDEDIT,
        () -> {
          Protection p = future.join();
          return "Selección protegida como {dato}"
              + p.name()
              + " {texto}(#"
              + p.shortId()
              + ") · creador {dato}"
              + ownerName(owner)
              + " {texto}· "
              + ProtectionDialogs.size(p)
              + ".";
        },
        null);
  }

  // ---------------------------------------------------------------- utilidades

  private Protection current(CommandSender sender) {
    return sender instanceof Player p ? manager.getProtectionAt(p.getLocation()).orElse(null) : null;
  }

  private Protection fresh(UUID id) {
    return manager.getProtection(id).orElseThrow(() -> new ProtectionException("La protección ya no existe."));
  }

  /** Tema administrativo cuando se actúa sobre una protección ajena con permisos de staff. */
  private static Messages.Theme theme(CommandSender sender, Protection p) {
    if (sender instanceof Player player && p.isOwner(player.getUniqueId())) return Messages.Theme.PUBLIC;
    return Messages.Theme.ADMIN;
  }

  private void sync(Runnable task) {
    if (Bukkit.isPrimaryThread()) task.run();
    else if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, task);
  }

  private static String describe(Throwable error) {
    while (error instanceof CompletionException && error.getCause() != null) error = error.getCause();
    return error instanceof ProtectionException || error instanceof IllegalArgumentException
        ? error.getMessage()
        : "No se pudo completar la operación. Consulta la consola.";
  }

  private void allowed(CommandSender sender, Protection p, Action action) {
    if (!AccessPolicy.allows(sender, p, action))
      throw new ProtectionException(
          sender.hasPermission(AccessPolicy.permission(action))
              ? "Tu rol en " + p.name() + " no permite esa acción."
              : "No tienes permiso para esa acción.");
  }

  private static void permission(CommandSender sender, String permission) {
    if (!sender.hasPermission(permission))
      throw new ProtectionException("No tienes permiso para esa acción.");
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
      double parsed = Double.parseDouble(value.replace(',', '.'));
      if (parsed != Math.rint(parsed) || Math.abs(parsed) > Integer.MAX_VALUE) throw new NumberFormatException();
      return (int) parsed;
    } catch (NumberFormatException invalid) {
      throw new ProtectionException("Número no válido: " + value + ".");
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
    throw new ProtectionException(
        "No conozco a " + value + ". Debe haber entrado antes al servidor, o usa su UUID.");
  }

  private static String[] clean(String[] args) {
    return Arrays.stream(args).filter(s -> !s.isBlank()).toArray(String[]::new);
  }

  private String[] legacy(CommandSender sender, String command, String[] args) {
    String action =
        switch (command) {
          case "addnamepr", "newnamepr" -> "nombre";
          case "prlist" -> "lista";
          case "ownerlist" -> "propietarios";
          case "memberlist" -> "miembros";
          case "vpreload" -> "recargar";
          default -> null;
        };
    if (action != null)
      return Stream.concat(Stream.of(action), Arrays.stream(args)).toArray(String[]::new);
    if (Set.of("addmember", "delmember", "addowner", "delowner").contains(command))
      return Stream.concat(
              Stream.of(
                  command.endsWith("owner") ? "propietario" : "miembro",
                  command.startsWith("add") ? "añadir" : "quitar"),
              Arrays.stream(args))
          .toArray(String[]::new);
    if (command.equals("givepr")) {
      if (args.length == 1 && Set.of("small", "medium", "large").contains(args[0]))
        return new String[] {
          "dar",
          player(sender).getName(),
          "REDSTONE_BLOCK",
          Integer.toString(
              plugin
                  .getConfig()
                  .getInt(
                      "protection_types." + args[0] + ".size",
                      args[0].equals("small") ? 32 : args[0].equals("medium") ? 64 : 128))
        };
      return Stream.concat(Stream.of("dar"), Arrays.stream(args)).toArray(String[]::new);
    }
    if (Set.of("modnamepr", "modmember", "modowner", "removepr").contains(command)) {
      permission(sender, "viciontprotections.admin.manage");
      require(args.length > 0, "Indica el nombre o el #ID de la protección.");
      Protection p = manager.resolve(args[0]);
      String mode =
          switch (command) {
            case "modnamepr" -> "nombre";
            case "modmember" -> "miembro";
            case "modowner" -> "propietario";
            default -> "eliminar";
          };
      return Stream.concat(Stream.of("@" + p.id(), mode), Arrays.stream(args).skip(1))
          .toArray(String[]::new);
    }
    return args;
  }

  // ---------------------------------------------------------------- autocompletado

  @Override
  public List<String> onTabComplete(
      CommandSender sender, Command command, String label, String[] arguments) {
    String[] args = arguments;
    String name = command.getName();
    if (Set.of("modnamepr", "modmember", "modowner", "removepr").contains(name)) {
      if (!sender.hasPermission("viciontprotections.admin.manage")) return List.of();
      if (args.length <= 1)
        return complete(manager.getProtections().stream().map(p -> "#" + p.shortId()).toList(), args);
      String action =
          switch (name) {
            case "modnamepr" -> "nombre";
            case "modmember" -> "miembro";
            case "modowner" -> "propietario";
            default -> "eliminar";
          };
      args = Stream.concat(Stream.of(action), Arrays.stream(args).skip(1)).toArray(String[]::new);
    } else if (Set.of("addmember", "delmember", "addowner", "delowner").contains(name)) {
      args =
          Stream.concat(
                  Stream.of(
                      name.endsWith("owner") ? "propietario" : "miembro",
                      name.startsWith("add") ? "añadir" : "quitar"),
                  Arrays.stream(args))
              .toArray(String[]::new);
    } else {
      String action =
          switch (name) {
            case "givepr" -> "dar";
            case "prlist" -> "lista";
            case "addnamepr", "newnamepr" -> "nombre";
            case "ownerlist", "memberlist", "vpreload" -> "";
            default -> null;
          };
      if (action != null && action.isEmpty()) return List.of();
      if (action != null)
        args = Stream.concat(Stream.of(action), Arrays.stream(args)).toArray(String[]::new);
    }
    if (args.length > 0 && args[0].startsWith("@")) {
      if (args.length == 1) {
        List<Protection> available =
            sender.hasPermission("viciontprotections.admin.manage")
                ? manager.getProtections()
                : sender instanceof Player p ? manager.getProtections(p.getUniqueId()) : List.of();
        return complete(available.stream().map(p -> "@" + p.shortId()).toList(), args);
      }
      args = Arrays.copyOfRange(args, 1, args.length);
    }
    List<String> options = new ArrayList<>();
    if (args.length <= 1) {
      options.addAll(List.of("guia", "ayuda"));
      Map<String, String> user = new LinkedHashMap<>();
      user.put("lista", "list");
      user.put("info", "info");
      user.put("nombre", "name");
      user.put("miembro", "members");
      user.put("miembros", "members");
      user.put("propietario", "owners");
      user.put("propietarios", "owners");
      user.put("limites", "borders");
      user.put("eliminar", "delete");
      user.forEach(
          (action, node) -> {
            if (sender.hasPermission("viciontprotections.user." + node)) options.add(action);
          });
      Map<String, String> admin = new LinkedHashMap<>();
      admin.put("dar", "give");
      admin.put("seleccion", "selection");
      admin.put("bandera", "flags");
      admin.put("transferir", "transfer");
      admin.put("recargar", "reload");
      admin.forEach(
          (action, node) -> {
            if (sender.hasPermission("viciontprotections.admin." + node)) options.add(action);
          });
      for (String node : List.of("give", "selection", "list", "reload"))
        if (sender.hasPermission("viciontprotections.admin." + node)) {
          options.add("admin");
          break;
        }
    } else {
      String first = args[0].toLowerCase(Locale.ROOT);
      if ((first.equals("dar") || first.equals("give"))
          && sender.hasPermission("viciontprotections.admin.give")) {
        if (args.length == 2) {
          Bukkit.getOnlinePlayers().forEach(p -> options.add(p.getName()));
          if (name.equals("givepr")) options.addAll(List.of("small", "medium", "large"));
        } else if (args.length == 3)
          Arrays.stream(Material.values())
              .filter(m -> !m.isLegacy() && m.isBlock() && m.isItem() && !m.isAir())
              .forEach(m -> options.add(m.name()));
        else if (args.length <= 5) {
          options.addAll(List.of("16", "32", "48", "64", "128"));
          if (args.length == 5) options.add("todo");
        } else if (args.length == 6) options.addAll(List.of("1", "2", "4", "16", "64"));
      } else if ((first.equals("lista") || first.equals("list")) && args.length == 2) {
        options.add("1");
        if (sender.hasPermission("viciontprotections.admin.list")) options.add("todas");
      } else if (Set.of("miembro", "member", "propietario", "owner").contains(first)) {
        boolean owners = first.startsWith("prop") || first.equals("owner");
        if (sender.hasPermission("viciontprotections.user." + (owners ? "owners" : "members"))
            || sender.hasPermission("viciontprotections.admin.manage")) {
          if (args.length == 2) options.addAll(List.of("añadir", "quitar"));
          else if (args.length == 3) {
            Protection here = current(sender);
            if (here != null && REMOVE.contains(args[1].toLowerCase(Locale.ROOT)))
              (owners ? here.owners() : here.members()).forEach(id -> options.add(ownerName(id)));
            else Bukkit.getOnlinePlayers().forEach(p -> options.add(p.getName()));
          }
        }
      } else if ((first.equals("bandera") || first.equals("flag"))
          && sender.hasPermission("viciontprotections.admin.flags")) {
        if (args.length == 2) options.addAll(manager.guard().editableFlags());
        else if (args.length == 3) options.addAll(List.of("permitir", "denegar", "restablecer"));
      } else if (first.equals("eliminar") && args.length == 2) options.add("confirmar");
      else if ((first.equals("seleccion") && sender.hasPermission("viciontprotections.admin.selection")
              || first.equals("transferir") && sender.hasPermission("viciontprotections.admin.transfer"))
          && args.length == 2) Bukkit.getOnlinePlayers().forEach(p -> options.add(p.getName()));
    }
    return complete(options, args);
  }

  private static List<String> complete(Collection<String> options, String[] args) {
    String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
    return options.stream()
        .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(last))
        .distinct()
        .sorted()
        .toList();
  }
}
