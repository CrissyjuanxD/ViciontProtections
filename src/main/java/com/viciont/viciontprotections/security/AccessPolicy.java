package com.viciont.viciontprotections.security;

import com.viciont.viciontprotections.models.Protection;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

/** La misma política se usa al ejecutar comandos y al construir los diálogos. */
public final class AccessPolicy {
  public enum Action {
    INFO,
    MEMBERS,
    OWNERS,
    RENAME,
    DELETE,
    BORDERS,
    TRANSFER,
    FLAGS
  }

  private AccessPolicy() {}

  /**
   * Protecciones que son territorio de un clan: su acceso lo gestionan los rangos del clan, así que
   * aquí no se pueden añadir ni quitar miembros, transferir ni eliminar.
   */
  private static volatile java.util.function.Function<java.util.UUID, String> territory = id -> null;

  /** Recibe el nombre del clan dueño de cada protección, o null si no es territorio. */
  public static void setTerritories(java.util.function.Function<java.util.UUID, String> clanName) {
    territory = clanName == null ? id -> null : clanName;
  }

  public static boolean isTerritory(Protection protection) {
    return clanOf(protection) != null;
  }

  /** Nombre del clan si la protección es territorio de uno. */
  public static String clanOf(Protection protection) {
    return territory.apply(protection.id());
  }

  /** Acciones que se gestionan desde /clan cuando la protección es territorio de un clan. */
  public static boolean lockedByClan(Protection protection, Action action) {
    return (action == Action.MEMBERS
            || action == Action.OWNERS
            || action == Action.DELETE
            || action == Action.TRANSFER)
        && isTerritory(protection);
  }

  public static String permission(Action action) {
    return switch (action) {
      case INFO -> "viciontprotections.user.info";
      case MEMBERS -> "viciontprotections.user.members";
      case OWNERS -> "viciontprotections.user.owners";
      case RENAME -> "viciontprotections.user.name";
      case DELETE -> "viciontprotections.user.delete";
      case BORDERS -> "viciontprotections.user.borders";
      case TRANSFER -> "viciontprotections.admin.transfer";
      case FLAGS -> "viciontprotections.admin.flags";
    };
  }

  /**
   * Con permissions.require-user-permissions en false los permisos de usuario y los de clan de los
   * jugadores son de todos, así el plugin funciona sin plugin de permisos; en true vuelven a ser solo
   * de OP. Los de administración (también admin.clan.*) no cambian, y un plugin de permisos puede
   * seguir dándolos o quitándolos.
   */
  public static void applyUserDefaults(Plugin plugin) {
    boolean required =
        plugin.getConfig().getBoolean("permissions.require-user-permissions", false);
    PermissionDefault value = required ? PermissionDefault.OP : PermissionDefault.TRUE;
    PluginManager manager = plugin.getServer().getPluginManager();
    for (Permission declared : plugin.getDescription().getPermissions()) {
      String name = declared.getName();
      if (!name.startsWith("viciontprotections.user.") && !name.startsWith("viciontprotections.clan.")) continue;
      Permission registered = manager.getPermission(name);
      if (registered != null && registered.getDefault() != value) registered.setDefault(value);
    }
  }

  public static boolean allows(CommandSender sender, Protection protection, Action action) {
    if (lockedByClan(protection, action)) return false;
    if (sender.hasPermission("viciontprotections.admin.manage")) return true;
    if (!(sender instanceof Player player) || !sender.hasPermission(permission(action)))
      return false;
    if (action == Action.TRANSFER || action == Action.FLAGS) return true;
    var id = player.getUniqueId();
    return switch (action) {
      case INFO -> protection.canAccess(id);
      case MEMBERS, BORDERS -> protection.isOwner(id);
      case OWNERS, RENAME, DELETE -> protection.primaryOwner().equals(id);
      case TRANSFER, FLAGS -> false;
    };
  }
}
