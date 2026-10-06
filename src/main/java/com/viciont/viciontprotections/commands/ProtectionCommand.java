package com.viciont.viciontprotections.commands;

import com.viciont.viciontprotections.api.*;
import com.viciont.viciontprotections.integration.WorldEditBridge;
import com.viciont.viciontprotections.managers.ProtectionManager;
import com.viciont.viciontprotections.models.Protection;
import com.viciont.viciontprotections.security.AccessPolicy;
import com.viciont.viciontprotections.ui.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class ProtectionCommand implements CommandExecutor, TabCompleter {
  private final JavaPlugin plugin;
  private final ProtectionManager manager;
  private final Messages messages;
  private final ProtectionDialogs dialogs;
  private final Boundaries boundaries;

  public ProtectionCommand(
      JavaPlugin plugin,
      ProtectionManager manager,
      Messages messages,
      ProtectionDialogs dialogs,
      Boundaries boundaries) {
    this.plugin = plugin;
    this.manager = manager;
    this.messages = messages;
    this.dialogs = dialogs;
    this.boundaries = boundaries;
  }

  @Override
  public boolean onCommand(
      CommandSender sender, Command command, String label, String[] arguments) {
    try {
      execute(sender, legacy(sender, command.getName(), arguments));
    } catch (RuntimeException failure) {
      messages.error(sender, failure);
    }
    return true;
  }

  private void execute(CommandSender sender, String[] args) {
    Protection selected = null;
    int offset = 0;
    if (args.length > 0 && args[0].startsWith("@")) {
      selected = manager.resolve(args[0].substring(1));
      offset = 1;
    }
    String action = args.length > offset ? args[offset].toLowerCase(Locale.ROOT) : "menu";
    String[] values = Arrays.copyOfRange(args, Math.min(offset + 1, args.length), args.length);
    if (action.equals("guia") || action.equals("guía") || action.equals("guide")) {
      if (sender instanceof Player p) dialogs.guide(p);
      else messages.guideChat(sender);
      return;
    }
    if (action.equals("ayuda") || action.equals("help")) {
      messages.guideChat(sender);
      messages.send(
          sender, "&#D8B4FF/pr lista · info · nombre · miembro · propietario · limites · eliminar");
      if (sender.hasPermission("viciontprotections.admin.manage"))
        messages.send(
            sender, Messages.Theme.ADMIN, "/pr dar · seleccion · bandera · transferir · recargar");
      return;
    }
    if (action.equals("recargar") || action.equals("reload")) {
      permission(sender, "viciontprotections.admin.reload");
      plugin.reloadConfig();
      messages.reload();
      messages.send(
          sender,
          Messages.Theme.ADMIN,
          "&#E6CCFFAjustes y mensajes recargados. Los cambios de conexión SQL se aplican al"
              + " reiniciar.");
      return;
    }
    if (action.equals("dar") || action.equals("give")) {
      give(sender, values);
      return;
    }
    if (action.equals("seleccion") || action.equals("selección")) {
      selection(sender, values);
      return;
    }
    if (action.equals("lista") || action.equals("list")) {
      list(sender, values);
      return;
    }
    if (action.equals("formulario")
        && values.length == 1
        && (values[0].equals("dar") || values[0].equals("seleccion"))) {
      permission(
          sender,
          values[0].equals("dar")
              ? "viciontprotections.admin.give"
              : "viciontprotections.admin.selection");
      dialogs.form(player(sender), null, values[0]);
      return;
    }
    Protection protection =
        selected != null
            ? selected
            : sender instanceof Player p
                ? manager.getProtectionAt(p.getLocation()).orElse(null)
                : null;
    if (action.equals("menu")) {
      Player player = player(sender);
      if (protection != null
          && !protection.canAccess(player.getUniqueId())
          && !sender.hasPermission("viciontprotections.admin.manage")) protection = null;
      dialogs.open(player, protection);
      return;
    }
    if (protection == null)
      throw new ProtectionException("No estás en una protección. Consulta /pr lista o /pr guia.");
    switch (action) {
      case "info" -> {
        allowed(sender, protection, AccessPolicy.Action.INFO);
        info(sender, protection);
      }
      case "nombre", "name" -> {
        allowed(sender, protection, AccessPolicy.Action.RENAME);
        require(values.length > 0, "/pr nombre <nuevo nombre>");
        done(
            sender,
            manager.renameProtection(protection.id(), String.join(" ", values)),
            "Nombre actualizado.");
      }
      case "miembro", "member", "propietario", "owner" -> {
        boolean owner = action.equals("propietario") || action.equals("owner");
        allowed(
            sender, protection, owner ? AccessPolicy.Action.OWNERS : AccessPolicy.Action.MEMBERS);
        require(
            values.length == 2,
            "/pr " + (owner ? "propietario" : "miembro") + " <añadir|quitar> <jugador|UUID>");
        UUID target = target(values[1]);
        boolean add =
            Set.of("añadir", "anadir", "add").contains(values[0].toLowerCase(Locale.ROOT));
        require(
            add || Set.of("quitar", "del", "remove").contains(values[0].toLowerCase(Locale.ROOT)),
            "Usa añadir o quitar.");
        CompletableFuture<Protection> future =
            owner
                ? (add
                    ? manager.addOwner(protection.id(), target)
                    : manager.removeOwner(protection.id(), target))
                : (add
                    ? manager.addMember(protection.id(), target)
                    : manager.removeMember(protection.id(), target));
        done(
            sender,
            future,
            "Permisos actualizados para " + ProtectionDialogs.ownerName(target) + ".");
      }
      case "limites", "límites", "borders" -> {
        allowed(sender, protection, AccessPolicy.Action.BORDERS);
        boolean enabled = boundaries.toggle(player(sender), protection);
        messages.send(
            sender,
            enabled
                ? "&#ECC7FFLímites activados para ti."
                : "&#ECC7FFLímites desactivados para ti.");
      }
      case "eliminar", "delete", "confirmacion" -> {
        allowed(sender, protection, AccessPolicy.Action.DELETE);
        if (values.length == 1 && values[0].equals("confirmar")) {
          done(sender, manager.deleteProtection(protection.id()), "Protección eliminada.");
        } else if (sender instanceof Player p && dialogs.supported(p))
          dialogs.confirmDelete(p, protection);
        else
          messages.link(
              sender,
              "Confirmar eliminación de " + protection.name(),
              "/pr @" + protection.id() + " eliminar confirmar",
              true);
      }
      case "transferir" -> {
        allowed(sender, protection, AccessPolicy.Action.TRANSFER);
        require(values.length == 1, "/pr transferir <jugador|UUID>");
        done(
            sender,
            manager.transferOwnership(protection.id(), target(values[0])),
            "Creador actualizado.");
      }
      case "bandera", "flag" -> {
        allowed(sender, protection, AccessPolicy.Action.FLAGS);
        if (values.length == 0) {
          messages.send(
              sender,
              Messages.Theme.WORLDGUARD,
              "&#D7B3FFOpciones: " + String.join(", ", manager.guard().editableFlags()));
          return;
        }
        require(values.length == 2, "/pr bandera <bandera> <permitir|denegar|restablecer>");
        manager
            .setFlag(protection.id(), values[0], values[1])
            .whenComplete(
                (p, error) -> {
                  if (error != null) messages.error(sender, error);
                  else
                    messages.send(
                        sender,
                        Messages.Theme.WORLDGUARD,
                        "&#D7B3FFBandera actualizada: " + values[0] + ".");
                });
      }
      case "formulario" -> {
        require(values.length == 1, "Formulario no válido.");
        AccessPolicy.Action needed =
            switch (values[0]) {
              case "miembro", "quitar-miembro" -> AccessPolicy.Action.MEMBERS;
              case "propietario", "quitar-propietario" -> AccessPolicy.Action.OWNERS;
              case "nombre" -> AccessPolicy.Action.RENAME;
              case "bandera" -> AccessPolicy.Action.FLAGS;
              case "transferir" -> AccessPolicy.Action.TRANSFER;
              default -> throw new ProtectionException("Formulario no válido.");
            };
        allowed(sender, protection, needed);
        dialogs.form(player(sender), protection, values[0]);
      }
      default -> throw new ProtectionException("Comando desconocido. Consulta /pr ayuda.");
    }
  }

  private void give(CommandSender sender, String[] args) {
    permission(sender, "viciontprotections.admin.give");
    require(
        args.length >= 3 && args.length <= 6,
        "/pr dar <jugador> <bloque> <ancho> [profundidad] [altura|todo] [cantidad]");
    Player target = Bukkit.getPlayerExact(args[0]);
    if (target == null) throw new ProtectionException("El destinatario debe estar conectado.");
    Material material = Material.matchMaterial(args[1]);
    if (material == null) throw new ProtectionException("Bloque desconocido.");
    int width = number(args[2]),
        depth = args.length >= 4 ? number(args[3]) : width,
        height = args.length >= 5 && !args[4].equalsIgnoreCase("todo") ? number(args[4]) : 0,
        amount = args.length >= 6 ? number(args[5]) : 1;
    int maximum = plugin.getConfig().getInt("protections.max-block-size", 4096);
    require(
        width <= maximum && depth <= maximum,
        "El tamaño máximo configurado es " + maximum + " bloques.");
    ItemStack block =
        manager.createProtectionBlock(new ProtectionBlock(material, width, depth, height), amount);
    giveItem(target, block);
    messages.send(
        sender,
        Messages.Theme.ADMIN,
        "&#E8CFFFEntregado a "
            + target.getName()
            + ": "
            + width
            + " × "
            + depth
            + " ("
            + material.name()
            + ").");
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

  private void selection(CommandSender sender, String[] args) {
    permission(sender, "viciontprotections.admin.selection");
    Player player = player(sender);
    require(args.length >= 2, "/pr seleccion <creador> <nombre>");
    var request =
        CreateProtectionRequest.region(
            target(args[0]),
            player.getWorld().getName(),
            new WorldEditBridge().selection(player),
            String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
    manager.guard().validate(request, player);
    manager
        .createProtection(request)
        .whenComplete(
            (p, error) -> {
              if (error != null) messages.error(sender, error);
              else
                messages.send(
                    sender,
                    Messages.Theme.WORLDEDIT,
                    "&#F0C5E7Selección protegida: " + p.name() + " (#" + p.shortId() + ").");
            });
  }

  private void list(CommandSender sender, String[] args) {
    permission(sender, "viciontprotections.user.list");
    int page = args.length > 0 ? number(args[0]) : 1;
    require(page > 0, "La página debe ser mayor que cero.");
    List<Protection> list =
        sender.hasPermission("viciontprotections.admin.list")
            ? manager.getProtections()
            : manager.getProtections(player(sender).getUniqueId());
    int pages = Math.max(1, (list.size() + 7) / 8);
    require(page <= pages, "La última página es " + pages + ".");
    if (sender instanceof Player player && dialogs.supported(player)) {
      dialogs.list(player, list, page, pages);
      return;
    }
    messages.send(
        sender,
        "&#D5A7FFProtecciones &#F2C4E2· " + page + "/" + pages + " · " + list.size() + " en total");
    if (list.isEmpty()) messages.send(sender, "&#E6CCFFAún no tienes protecciones.");
    for (Protection p : list.subList((page - 1) * 8, Math.min(page * 8, list.size())))
      messages.link(
          sender,
          p.name()
              + " · "
              + p.worldName()
              + " · "
              + ProtectionDialogs.ownerName(p.primaryOwner())
              + " · #"
              + p.shortId(),
          "/pr @" + p.id() + " info",
          true);
    if (page > 1) messages.link(sender, "Anterior", "/pr lista " + (page - 1), true);
    if (page < pages) messages.link(sender, "Siguiente", "/pr lista " + (page + 1), true);
  }

  private void info(CommandSender sender, Protection p) {
    if (sender instanceof Player player && dialogs.supported(player)) {
      dialogs.info(player, p);
      return;
    }
    messages.send(sender, "&#D6A8FF" + p.name() + " &#F1C1E0· #" + p.shortId());
    messages.send(
        sender, "&#E4C7FFCreador: &#F1B9DA" + ProtectionDialogs.ownerName(p.primaryOwner()));
    messages.send(sender, "&#E4C7FFPropietarios: &#F1B9DA" + names(p.owners()));
    messages.send(sender, "&#E4C7FFMiembros: &#F1B9DA" + names(p.members()));
    messages.send(
        sender,
        "&#E4C7FFZona: "
            + p.worldName()
            + " · "
            + p.bounds().width()
            + " × "
            + p.bounds().height()
            + " × "
            + p.bounds().depth());
  }

  private String names(Set<UUID> ids) {
    return ids.isEmpty()
        ? "ninguno"
        : String.join(
            ", ",
            ids.stream()
                .map(ProtectionDialogs::ownerName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList());
  }

  private void done(CommandSender sender, CompletableFuture<?> future, String success) {
    future.whenComplete(
        (p, error) -> {
          if (error == null)
            messages.send(
                sender,
                sender.hasPermission("viciontprotections.admin.manage")
                    ? Messages.Theme.ADMIN
                    : Messages.Theme.PUBLIC,
                "&#E6CCFF" + success);
          else messages.error(sender, error);
        });
  }

  private void allowed(CommandSender sender, Protection p, AccessPolicy.Action action) {
    if (!AccessPolicy.allows(sender, p, action))
      throw new ProtectionException("No tienes permiso para esa acción en esta protección.");
  }

  private void permission(CommandSender sender, String permission) {
    if (!sender.hasPermission(permission))
      throw new ProtectionException("No tienes el permiso " + permission + ".");
  }

  private Player player(CommandSender sender) {
    if (sender instanceof Player p) return p;
    throw new ProtectionException("Este comando necesita un jugador.");
  }

  private void require(boolean condition, String message) {
    if (!condition) throw new ProtectionException(message);
  }

  private int number(String value) {
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException invalid) {
      throw new ProtectionException("Número no válido: " + value);
    }
  }

  private UUID target(String value) {
    try {
      return UUID.fromString(value);
    } catch (IllegalArgumentException ignored) {
    }
    Player online = Bukkit.getPlayerExact(value);
    if (online != null) return online.getUniqueId();
    for (OfflinePlayer player : Bukkit.getOfflinePlayers())
      if (value.equalsIgnoreCase(player.getName())) return player.getUniqueId();
    throw new ProtectionException(
        "Jugador desconocido. Debe haber entrado antes al servidor o debes usar su UUID.");
  }

  private String[] legacy(CommandSender sender, String command, String[] args) {
    String action =
        switch (command) {
          case "addnamepr", "newnamepr" -> "nombre";
          case "prlist" -> "lista";
          case "ownerlist", "memberlist" -> "info";
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
      require(args.length > 0, "Indica el nombre o #ID de la protección.");
      Protection p = manager.resolve(args[0]);
      permission(sender, "viciontprotections.admin.manage");
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

  @Override
  public List<String> onTabComplete(
      CommandSender sender, Command command, String label, String[] arguments) {
    String[] args = arguments;
    String name = command.getName();
    if (Set.of("modnamepr", "modmember", "modowner", "removepr").contains(name)) {
      if (!sender.hasPermission("viciontprotections.admin.manage")) return List.of();
      if (args.length <= 1)
        return complete(
            manager.getProtections().stream().map(p -> "#" + p.shortId()).toList(), args);
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
            case "ownerlist", "memberlist" -> "info";
            case "vpreload" -> "recargar";
            default -> null;
          };
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
      options.addAll(List.of("guia", "ayuda", "menu"));
      Map<String, String> nodes =
          Map.of(
              "lista",
              "list",
              "info",
              "info",
              "nombre",
              "name",
              "miembro",
              "members",
              "propietario",
              "owners",
              "limites",
              "borders",
              "eliminar",
              "delete");
      nodes.forEach(
          (action, node) -> {
            if (sender.hasPermission("viciontprotections.user." + node)) options.add(action);
          });
      Map<String, String> admin =
          Map.of(
              "dar",
              "give",
              "seleccion",
              "selection",
              "bandera",
              "flags",
              "transferir",
              "transfer",
              "recargar",
              "reload");
      admin.forEach(
          (action, node) -> {
            if (sender.hasPermission("viciontprotections.admin." + node)) options.add(action);
          });
    } else {
      String first = args[0].toLowerCase(Locale.ROOT);
      if ((first.equals("dar") || first.equals("give"))
          && sender.hasPermission("viciontprotections.admin.give")) {
        if (args.length == 2) {
          Bukkit.getOnlinePlayers().forEach(p -> options.add(p.getName()));
          if (name.equals("givepr")) options.addAll(List.of("small", "medium", "large"));
        } else if (args.length == 3)
          Arrays.stream(Material.values())
              .filter(m -> m.isBlock() && m.isItem() && !m.isAir())
              .forEach(m -> options.add(m.name()));
        else if (args.length <= 6) {
          options.addAll(List.of("16", "32", "64", "128"));
          if (args.length == 6) options.add("todo");
        } else if (args.length == 7) options.addAll(List.of("1", "2", "4", "16"));
      } else if (Set.of("miembro", "member", "propietario", "owner").contains(first)) {
        boolean owners = first.equals("propietario") || first.equals("owner");
        if (sender.hasPermission("viciontprotections.user." + (owners ? "owners" : "members"))
            || sender.hasPermission("viciontprotections.admin.manage")) {
          if (args.length == 2) options.addAll(List.of("añadir", "quitar"));
          else if (args.length == 3)
            for (OfflinePlayer p : Bukkit.getOfflinePlayers())
              if (p.getName() != null) options.add(p.getName());
        }
      } else if ((first.equals("bandera") || first.equals("flag"))
          && sender.hasPermission("viciontprotections.admin.flags")) {
        if (args.length == 2) options.addAll(manager.guard().editableFlags());
        else if (args.length == 3) options.addAll(List.of("permitir", "denegar", "restablecer"));
      } else if (first.equals("eliminar") && args.length == 2) options.add("confirmar");
      else if ((first.equals("seleccion")
                  && sender.hasPermission("viciontprotections.admin.selection")
              || first.equals("transferir")
                  && sender.hasPermission("viciontprotections.admin.transfer"))
          && args.length == 2) {
        for (OfflinePlayer p : Bukkit.getOfflinePlayers())
          if (p.getName() != null) options.add(p.getName());
      }
    }
    return complete(options, args);
  }

  private List<String> complete(Collection<String> options, String[] args) {
    String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
    return options.stream()
        .filter(s -> s.toLowerCase(Locale.ROOT).startsWith(last))
        .distinct()
        .sorted()
        .toList();
  }
}
