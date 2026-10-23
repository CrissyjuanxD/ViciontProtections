package com.viciont.viciontprotections.clans;

import com.viciont.viciontprotections.api.ProtectionException;
import com.viciont.viciontprotections.ui.Messages;
import java.util.*;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.*;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Mensajes de Viciont Clans con su prefijo y su paleta (clans.yml). */
public final class ClanMessages {
  public enum Theme {
    PUBLIC("publico"),
    ADMIN("admin"),
    WAR("guerra");

    private final String key;

    Theme(String key) {
      this.key = key;
    }
  }

  private final JavaPlugin plugin;
  private final ClanSettings settings;

  public ClanMessages(JavaPlugin plugin, ClanSettings settings) {
    this.plugin = plugin;
    this.settings = settings;
  }

  public ClanSettings settings() {
    return settings;
  }

  public Map<String, String> palette() {
    return settings.palette();
  }

  public String color(String value) {
    return Messages.color(value, settings.palette());
  }

  /** Nombre del clan en su color, para incrustar en un texto con paleta. */
  public static String paint(String name, String hex) {
    return "&" + hex + name;
  }

  public void send(CommandSender sender, String value) {
    send(sender, Theme.PUBLIC, value);
  }

  public void send(CommandSender sender, Theme theme, String value) {
    sender.sendMessage(color(settings.prefix(theme.key) + value));
  }

  public void header(CommandSender sender, Theme theme, String title) {
    sender.sendMessage(color(settings.prefix(theme.key) + "{titulo}" + title));
  }

  public void line(CommandSender sender, String value) {
    sender.sendMessage(color(" {titulo}۞ {texto}" + value));
  }

  public void fail(CommandSender sender, String value) {
    send(sender, "{error}" + value);
  }

  public void error(CommandSender sender, Throwable failure) {
    fail(sender, describe(failure, sender));
  }

  /** Mensaje seguro para el jugador; los fallos inesperados se registran en la consola. */
  public String describe(Throwable failure, CommandSender sender) {
    while (failure instanceof CompletionException && failure.getCause() != null)
      failure = failure.getCause();
    if (failure instanceof ProtectionException || failure instanceof IllegalArgumentException)
      return failure.getMessage();
    plugin
        .getLogger()
        .log(
            Level.SEVERE,
            "Error inesperado en clanes"
                + (sender == null ? "" : " al atender a " + sender.getName())
                + ":",
            failure);
    return "No se pudo completar la operación. Consulta la consola.";
  }

  public void broadcast(Theme theme, String value) {
    String text = color(settings.prefix(theme.key) + value);
    for (Player player : Bukkit.getOnlinePlayers()) player.sendMessage(text);
    Bukkit.getConsoleSender().sendMessage(text);
  }

  public void actionBar(Player player, String value) {
    if (value == null || value.isBlank()) return;
    player
        .spigot()
        .sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(color(value)));
  }

  public TextComponent component(String value) {
    return Messages.component(value, settings.palette());
  }

  public TextComponent button(String label, String hover, String target, boolean run) {
    return Messages.button(label, hover, target, run, settings.palette());
  }

  public void components(CommandSender sender, boolean prefixed, BaseComponent... parts) {
    List<BaseComponent> line = new ArrayList<>();
    line.add(component(prefixed ? settings.prefix(Theme.PUBLIC.key) : " "));
    line.addAll(Arrays.asList(parts));
    if (sender instanceof Player player)
      player.spigot().sendMessage(line.toArray(BaseComponent[]::new));
    else {
      StringBuilder plain = new StringBuilder();
      for (BaseComponent part : line) plain.append(part.toLegacyText());
      sender.sendMessage(plain.toString());
    }
  }
}
