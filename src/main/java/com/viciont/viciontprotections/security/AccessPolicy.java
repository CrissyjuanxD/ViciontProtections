package com.viciont.viciontprotections.security;

import com.viciont.viciontprotections.models.Protection;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

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

  public static boolean allows(CommandSender sender, Protection protection, Action action) {
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
