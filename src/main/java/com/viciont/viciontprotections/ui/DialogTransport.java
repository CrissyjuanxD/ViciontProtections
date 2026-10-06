package com.viciont.viciontprotections.ui;

import com.google.gson.JsonObject;
import java.lang.reflect.*;
import java.util.function.Consumer;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Muestra diálogos JSON sin depender de clases de 1.21.6 en tiempo de compilación.
 *
 * <ol>
 *   <li>Spigot 1.21.6+: {@code player.showDialog(Dialog)} con la API de diálogos de BungeeCord.
 *   <li>Paper: {@code /minecraft:dialog} ejecutado por un emisor silencioso de {@code
 *       Server#createCommandSender}, sin avisos a los OP ni registros en la consola.
 *   <li>Otros: el mismo comando desde la consola.
 * </ol>
 */
final class DialogTransport {
  private final JavaPlugin plugin;
  private Method spigotShow;
  private CommandSender sender;
  private boolean warned;

  DialogTransport(JavaPlugin plugin) {
    this.plugin = plugin;
    try {
      Class<?> dialog = Class.forName("net.md_5.bungee.api.dialog.Dialog");
      spigotShow = Player.class.getMethod("showDialog", dialog);
    } catch (ReflectiveOperationException | LinkageError notSpigot) {
      spigotShow = null;
    }
    if (spigotShow == null)
      try {
        Method create =
            Bukkit.getServer().getClass().getMethod("createCommandSender", Consumer.class);
        Consumer<Object> ignore = feedback -> {};
        sender = (CommandSender) create.invoke(Bukkit.getServer(), ignore);
      } catch (ReflectiveOperationException | LinkageError | ClassCastException notPaper) {
        sender = Bukkit.getConsoleSender();
      }
  }

  String mode() {
    return spigotShow != null
        ? "Spigot"
        : sender == Bukkit.getConsoleSender() ? "comando de consola" : "Paper";
  }

  /** Devuelve falso si el servidor rechaza el diálogo; quien llama muestra la versión de chat. */
  boolean show(Player player, JsonObject dialog) {
    try {
      if (spigotShow != null) {
        spigotShow.invoke(player, SpigotDialogs.convert(dialog));
        return true;
      }
      return Bukkit.dispatchCommand(sender, "minecraft:dialog show " + target(player) + " " + dialog);
    } catch (InvocationTargetException failure) {
      warn(failure.getCause() == null ? failure : failure.getCause());
    } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
      warn(failure);
    }
    return false;
  }

  private static String target(Player player) {
    String name = player.getName();
    return name.matches("[A-Za-z0-9_.+-]+") ? name : '"' + name.replace("\"", "\\\"") + '"';
  }

  private void warn(Throwable failure) {
    String message = "No se pudo mostrar un diálogo (" + mode() + "); se usa el chat en su lugar.";
    if (warned) plugin.getLogger().warning(message + " " + failure);
    else plugin.getLogger().log(Level.WARNING, message, failure);
    warned = true;
  }
}
