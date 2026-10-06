package com.viciont.viciontprotections.ui;

import com.viciont.viciontprotections.api.ProtectionException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;
import java.util.regex.*;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.*;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Mensajes en español con la paleta morada y rosa. Admite {@code &#RRGGBB}, códigos {@code &},
 * {@code <gradient:#RRGGBB:#RRGGBB>texto</gradient>} y los colores de la paleta, como {@code
 * {dato}}.
 */
public final class Messages {
  public enum Theme {
    PUBLIC("publico"),
    ADMIN("admin"),
    WORLDGUARD("worldguard"),
    WORLDEDIT("worldedit");

    private final String key;

    Theme(String key) {
      this.key = key;
    }
  }

  /** Versión de messages.yml; al subirla se guarda una copia del archivo antiguo y se regenera. */
  static final int VERSION = 3;

  private static final Pattern HEX = Pattern.compile("&#([a-fA-F0-9]{6})");
  private static final Pattern TOKEN = Pattern.compile("\\{([a-z]+)}");
  private static final Pattern GRADIENT =
      Pattern.compile(
          "<gradient:#([a-fA-F0-9]{6}):#([a-fA-F0-9]{6})>(.*?)</gradient>", Pattern.DOTALL);
  private static final Map<String, String> DEFAULT_PALETTE =
      Map.of(
          "titulo", "B07CFF",
          "texto", "E9DCFF",
          "suave", "C4A6EE",
          "dato", "F5B3DA",
          "acento", "F06AAE",
          "error", "FF8FB8",
          "admin", "C99BFF",
          "worldguard", "A58BFF",
          "worldedit", "FFA3D1");
  private static volatile Map<String, String> palette = DEFAULT_PALETTE;

  private final JavaPlugin plugin;
  private YamlConfiguration messages;

  public Messages(JavaPlugin plugin) {
    this.plugin = plugin;
    reload();
  }

  public void reload() {
    File file = new File(plugin.getDataFolder(), "messages.yml");
    if (file.exists()) {
      int version = YamlConfiguration.loadConfiguration(file).getInt("messages-version", 0);
      if (version < VERSION) {
        try {
          Path backup = file.toPath().resolveSibling("messages-v" + version + "-anterior.yml");
          Files.move(file.toPath(), backup, StandardCopyOption.REPLACE_EXISTING);
          plugin
              .getLogger()
              .info(
                  "messages.yml era de una versión anterior: se guardó como "
                      + backup.getFileName()
                      + " y se creó uno nuevo con el prefijo y la paleta actuales.");
        } catch (IOException failure) {
          plugin.getLogger().log(Level.WARNING, "No se pudo actualizar messages.yml.", failure);
        }
      }
    }
    if (!file.exists()) plugin.saveResource("messages.yml", false);
    messages = YamlConfiguration.loadConfiguration(file);
    try (var input =
        new InputStreamReader(
            Objects.requireNonNull(plugin.getResource("messages.yml")), StandardCharsets.UTF_8)) {
      messages.setDefaults(YamlConfiguration.loadConfiguration(input));
    } catch (IOException error) {
      throw new IllegalStateException(error);
    }
    Map<String, String> colors = new HashMap<>(DEFAULT_PALETTE);
    var section = messages.getConfigurationSection("colores");
    if (section != null)
      for (String key : section.getKeys(false)) {
        String value = section.getString(key, "").trim().replace("#", "");
        if (value.matches("[a-fA-F0-9]{6}")) colors.put(key.toLowerCase(Locale.ROOT), value);
      }
    palette = Map.copyOf(colors);
  }

  /** Traduce paleta, degradados y colores hex/clásicos a texto con formato de Minecraft. */
  public static String color(String value) {
    if (value == null) return "";
    Matcher token = TOKEN.matcher(value);
    StringBuilder tokens = new StringBuilder();
    while (token.find()) {
      String hex = palette.get(token.group(1));
      token.appendReplacement(
          tokens, Matcher.quoteReplacement(hex == null ? token.group() : "&#" + hex));
    }
    token.appendTail(tokens);
    Matcher gradient = GRADIENT.matcher(tokens);
    StringBuilder gradients = new StringBuilder();
    while (gradient.find())
      gradient.appendReplacement(
          gradients,
          Matcher.quoteReplacement(gradient(gradient.group(3), gradient.group(1), gradient.group(2))));
    gradient.appendTail(gradients);
    Matcher match = HEX.matcher(gradients);
    StringBuilder output = new StringBuilder();
    while (match.find())
      match.appendReplacement(
          output,
          Matcher.quoteReplacement(
              net.md_5.bungee.api.ChatColor.of("#" + match.group(1)).toString()));
    match.appendTail(output);
    return net.md_5.bungee.api.ChatColor.translateAlternateColorCodes('&', output.toString());
  }

  static String gradient(String text, String from, String to) {
    int start = Integer.parseInt(from, 16), end = Integer.parseInt(to, 16);
    StringBuilder formats = new StringBuilder();
    List<Character> visible = new ArrayList<>();
    List<String> formatBefore = new ArrayList<>();
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c == '&' && i + 1 < text.length() && "klmnoKLMNO".indexOf(text.charAt(i + 1)) >= 0) {
        formats.append('&').append(text.charAt(++i));
        continue;
      }
      visible.add(c);
      formatBefore.add(formats.toString());
    }
    StringBuilder result = new StringBuilder();
    int steps = Math.max(1, visible.size() - 1);
    for (int i = 0; i < visible.size(); i++) {
      double t = (double) i / steps;
      int r = mix(start >> 16, end >> 16, t),
          g = mix(start >> 8 & 0xFF, end >> 8 & 0xFF, t),
          b = mix(start & 0xFF, end & 0xFF, t);
      result
          .append(String.format("&#%02X%02X%02X", r, g, b))
          .append(formatBefore.get(i))
          .append(visible.get(i));
    }
    return result.toString();
  }

  private static int mix(int a, int b, double t) {
    return (int) Math.round(a + (b - a) * t);
  }

  public String text(String key, String... replacements) {
    String value = messages.getString(key);
    if (value == null) value = "";
    return replace(value, replacements);
  }

  private static String replace(String value, String... replacements) {
    for (int i = 0; i + 1 < replacements.length; i += 2)
      value = value.replace("%" + replacements[i] + "%", replacements[i + 1]);
    return value;
  }

  public String prefix(Theme theme) {
    return text("prefijos." + theme.key);
  }

  /** Mensaje de una línea con el prefijo público. */
  public void send(CommandSender sender, String value) {
    send(sender, Theme.PUBLIC, value);
  }

  public void send(CommandSender sender, Theme theme, String value) {
    sender.sendMessage(color(prefix(theme) + value));
  }

  /** Mensaje configurable de messages.yml; una cadena vacía lo desactiva. */
  public void notice(CommandSender sender, String key, String... replacements) {
    String value = text(key, replacements);
    if (!value.isBlank()) send(sender, value);
  }

  /** Encabezado con prefijo para bloques de varias líneas. */
  public void header(CommandSender sender, Theme theme, String title) {
    sender.sendMessage(color(prefix(theme) + "{titulo}" + title));
  }

  /** Línea de un bloque, sin prefijo y con la viñeta de la rueda. */
  public void line(CommandSender sender, String value) {
    sender.sendMessage(color(" {suave}۞ {texto}" + value));
  }

  public void fail(CommandSender sender, String value) {
    send(sender, "{error}" + value);
  }

  public void error(CommandSender sender, Throwable failure) {
    while (failure.getCause() != null && failure instanceof CompletionException)
      failure = failure.getCause();
    boolean expected =
        failure instanceof ProtectionException || failure instanceof IllegalArgumentException;
    if (!expected)
      plugin
          .getLogger()
          .log(Level.SEVERE, "Error inesperado al atender a " + sender.getName() + ":", failure);
    fail(sender, expected ? failure.getMessage() : text("errores.inesperado"));
  }

  public void entered(Player player, String name, String owner) {
    notice(player, "avisos.entrada", "name", name, "owner", owner);
    actionBar(player, text("avisos.entrada-actionbar", "name", name, "owner", owner));
  }

  public void left(Player player, String name) {
    notice(player, "avisos.salida", "name", name);
    actionBar(player, text("avisos.salida-actionbar", "name", name));
  }

  public void actionBar(Player player, String value) {
    if (value == null || value.isBlank()) return;
    player
        .spigot()
        .sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(color(value)));
  }

  /** Texto con formato convertido a componentes para combinarlo con botones. */
  public static TextComponent component(String value) {
    return new TextComponent(TextComponent.fromLegacyText(color(value)));
  }

  /**
   * Botón de chat: {@code run} ejecuta el comando al pulsarlo; si es falso, lo escribe en la barra
   * de chat para completarlo. Las URL se abren directamente.
   */
  public static TextComponent button(String label, String hover, String target, boolean run) {
    TextComponent button = component(label);
    if (target != null) {
      ClickEvent.Action action =
          target.startsWith("https://")
              ? ClickEvent.Action.OPEN_URL
              : run ? ClickEvent.Action.RUN_COMMAND : ClickEvent.Action.SUGGEST_COMMAND;
      button.setClickEvent(new ClickEvent(action, target));
    }
    if (hover != null && !hover.isEmpty())
      button.setHoverEvent(
          new HoverEvent(
              HoverEvent.Action.SHOW_TEXT, new Text(TextComponent.fromLegacyText(color(hover)))));
    return button;
  }

  /** Envía una línea compuesta; a la consola le llega como texto plano. */
  public void components(CommandSender sender, boolean prefixed, BaseComponent... parts) {
    List<BaseComponent> line = new ArrayList<>();
    line.add(component(prefixed ? prefix(Theme.PUBLIC) : " "));
    line.addAll(Arrays.asList(parts));
    if (sender instanceof Player player) player.spigot().sendMessage(line.toArray(BaseComponent[]::new));
    else {
      StringBuilder plain = new StringBuilder();
      for (BaseComponent part : line) plain.append(part.toLegacyText());
      sender.sendMessage(plain.toString());
    }
  }

  public String guideUrl(String platform) {
    String url = plugin.getConfig().getString("guide." + platform, "").trim();
    return url.matches("https://\\S+") ? url : "";
  }

  public List<String> guideLines() {
    return messages.getStringList("guia");
  }

  public void guideChat(CommandSender sender) {
    header(sender, Theme.PUBLIC, "Guía de protecciones");
    for (String line : guideLines()) sender.sendMessage(color(" " + line));
    List<BaseComponent> links = new ArrayList<>();
    for (String site : List.of("spigot", "modrinth")) {
      String url = guideUrl(site);
      if (!url.isEmpty())
        links.add(
            button(
                "{acento}[Guía en " + (site.equals("spigot") ? "Spigot" : "Modrinth") + "] ",
                "{texto}Abrir " + url,
                url,
                true));
    }
    if (!links.isEmpty()) components(sender, false, links.toArray(BaseComponent[]::new));
  }
}
