package com.viciont.viciontprotections.ui;

import com.viciont.viciontprotections.api.ProtectionException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.*;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class Messages {
  public enum Theme {
    PUBLIC,
    ADMIN,
    WORLDGUARD,
    WORLDEDIT
  }

  private final JavaPlugin plugin;
  private YamlConfiguration messages;

  public Messages(JavaPlugin plugin) {
    this.plugin = plugin;
    reload();
  }

  public void reload() {
    File file = new File(plugin.getDataFolder(), "messages.yml");
    if (!file.exists()) plugin.saveResource("messages.yml", false);
    messages = YamlConfiguration.loadConfiguration(file);
    try (var input =
        new InputStreamReader(
            Objects.requireNonNull(plugin.getResource("messages.yml")), StandardCharsets.UTF_8)) {
      messages.setDefaults(YamlConfiguration.loadConfiguration(input));
    } catch (IOException error) {
      throw new IllegalStateException(error);
    }
  }

  public static String color(String value) {
    var match = Pattern.compile("&#([a-fA-F0-9]{6})").matcher(value);
    StringBuilder output = new StringBuilder();
    while (match.find())
      match.appendReplacement(
          output, net.md_5.bungee.api.ChatColor.of("#" + match.group(1)).toString());
    match.appendTail(output);
    return net.md_5.bungee.api.ChatColor.translateAlternateColorCodes('&', output.toString());
  }

  public String text(String key, String... replacements) {
    String value = messages.getString(key, key);
    for (int i = 0; i + 1 < replacements.length; i += 2)
      value = value.replace("%" + replacements[i] + "%", replacements[i + 1]);
    return value;
  }

  public void send(CommandSender sender, String value) {
    send(sender, Theme.PUBLIC, value);
  }

  public void send(CommandSender sender, Theme theme, String value) {
    sender.sendMessage(color(text("prefix." + theme.name().toLowerCase(Locale.ROOT)) + value));
  }

  public void error(CommandSender sender, Throwable failure) {
    while (failure.getCause() != null
        && (failure instanceof java.util.concurrent.CompletionException))
      failure = failure.getCause();
    send(
        sender,
        "&#F2B5DC"
            + (failure instanceof ProtectionException || failure instanceof IllegalArgumentException
                ? failure.getMessage()
                : "No se pudo completar la operación. Consulta la consola."));
  }

  public void entered(Player player, String name, String owner) {
    String value = text("entered", "name", name, "owner", owner);
    send(player, value);
    player
        .spigot()
        .sendMessage(
            ChatMessageType.ACTION_BAR,
            TextComponent.fromLegacyText(color(text("entered-actionbar", "name", name))));
  }

  public void link(CommandSender sender, String label, String target, boolean command) {
    if (!(sender instanceof Player player)) {
      send(sender, label + " " + target);
      return;
    }
    TextComponent button =
        new TextComponent(TextComponent.fromLegacyText(color("&#CDA0FF۞ &#F0BDE0[" + label + "]")));
    button.setClickEvent(
        new ClickEvent(
            command ? ClickEvent.Action.RUN_COMMAND : ClickEvent.Action.OPEN_URL, target));
    player.spigot().sendMessage(button);
  }

  public String guideUrl(String platform) {
    String url = plugin.getConfig().getString("guide." + platform, " ").trim();
    return url.matches("https://[^\\s]+") ? url : "";
  }

  public void guideChat(CommandSender sender) {
    send(sender, "&#EBC9FFGuía de protecciones");
    for (String line : messages.getStringList("guide")) send(sender, line);
    for (String site : List.of("spigot", "modrinth")) {
      String url = guideUrl(site);
      if (!url.isEmpty())
        link(sender, "Guía en " + (site.equals("spigot") ? "Spigot" : "Modrinth"), url, false);
    }
  }

  public List<String> guideLines() {
    return messages.getStringList("guide");
  }
}
