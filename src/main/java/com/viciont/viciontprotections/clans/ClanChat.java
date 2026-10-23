package com.viciont.viciontprotections.clans;

import com.viciont.viciontprotections.api.clan.*;
import com.viciont.viciontprotections.api.event.clan.ClanChatEvent;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Chat de los clanes: nombre del clan delante del chat público, chat privado con /cc y modo clan,
 * copia en la consola y en un archivo diario, espías del staff y un historial corto para el
 * diálogo del chat.
 */
public final class ClanChat implements Listener, AutoCloseable {
  private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyy-MM-dd");
  private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

  private final JavaPlugin plugin;
  private final ClanManager manager;
  private final ClanSettings settings;
  private final ClanMessages messages;
  private final Set<UUID> clanMode = ConcurrentHashMap.newKeySet();
  private final Set<UUID> spies = ConcurrentHashMap.newKeySet();
  private final Map<UUID, Deque<String>> history = new ConcurrentHashMap<>();
  private final ExecutorService files =
      Executors.newSingleThreadExecutor(
          task -> {
            Thread thread = new Thread(task, "ViciontClans-Chat");
            thread.setDaemon(true);
            return thread;
          });

  public ClanChat(JavaPlugin plugin, ClanManager manager) {
    this.plugin = plugin;
    this.manager = manager;
    this.settings = manager.settings();
    this.messages = manager.messages();
  }

  /** Nombre del clan como sale delante del chat público, o vacío si no tiene. */
  public String publicTag(Player player) {
    return manager
        .getPlayerClan(player.getUniqueId())
        .map(clan -> messages.color(fill(settings.text("chat.publico"), clan)))
        .orElse("");
  }

  static String fill(String format, Clan clan) {
    return format.replace("%color%", "&" + clan.color()).replace("%clan%", clan.name());
  }

  @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
  public void clanMode(AsyncPlayerChatEvent event) {
    Player player = event.getPlayer();
    if (!clanMode.contains(player.getUniqueId())) return;
    var clan = manager.getPlayerClan(player.getUniqueId());
    if (clan.isEmpty()) {
      clanMode.remove(player.getUniqueId());
      return;
    }
    event.setCancelled(true);
    send(player, clan.get(), event.getMessage());
  }

  /** Después del formato del servidor (prioridad normal), se añade el nombre del clan delante. */
  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void publicChat(AsyncPlayerChatEvent event) {
    if (!settings.enabled() || !settings.flag("chat.publico-activo")) return;
    String tag = publicTag(event.getPlayer());
    if (!tag.isEmpty()) event.setFormat(tag.replace("%", "%%") + event.getFormat());
  }

  /** Mensaje de un jugador a su clan. Seguro fuera del hilo principal. */
  public boolean send(Player player, Clan clan, String text) {
    String message = text.replace("§", "").trim();
    if (message.isEmpty()) return false;
    var event = new ClanChatEvent(player, clan, message);
    Bukkit.getPluginManager().callEvent(event);
    if (event.isCancelled()) return false;
    message = event.getMessage().replace("§", "");
    String rank = clan.rank(player.getUniqueId()).map(settings::rankName).orElse("");
    String line =
        messages
            .color(fill(settings.text("chat.clan"), clan).replace("%rango%", rank).replace("%jugador%", player.getName()))
            .replace("%mensaje%", message);
    deliver(clan, line, player.getName(), message);
    return true;
  }

  /** Aviso del sistema o de un plugin conectado en el chat del clan. */
  public void notice(Clan clan, String text) {
    String line = messages.color(fill(settings.text("chat.aviso"), clan).replace("%mensaje%", text));
    deliver(clan, line, "Aviso", ChatColor.stripColor(messages.color(text)));
  }

  private void deliver(Clan clan, String line, String author, String plain) {
    for (UUID member : clan.members().keySet()) {
      Player online = Bukkit.getPlayer(member);
      if (online != null) online.sendMessage(line);
    }
    if (!spies.isEmpty()) {
      String spy =
          messages
              .color(fill(settings.text("chat.espia"), clan).replace("%jugador%", author))
              .replace("%mensaje%", plain);
      for (UUID id : spies) {
        Player online = Bukkit.getPlayer(id);
        if (online != null
            && !clan.isMember(id)
            && online.hasPermission("viciontprotections.admin.clan.spy")) online.sendMessage(spy);
      }
    }
    String console =
        settings
            .text("chat.consola")
            .replace("%clan%", clan.name())
            .replace("%jugador%", author)
            .replace("%mensaje%", plain);
    if (!console.isBlank()) Bukkit.getConsoleSender().sendMessage(console);
    Deque<String> lines = history.computeIfAbsent(clan.id(), k -> new ConcurrentLinkedDeque<>());
    lines.addLast(line);
    int keep = Math.max(1, settings.number("chat.historial", 10));
    while (lines.size() > keep) lines.pollFirst();
    if (settings.flag("chat.archivo")) log(clan.name(), author, plain);
  }

  private void log(String clan, String author, String message) {
    LocalDateTime now = LocalDateTime.now();
    String entry = "[" + TIME.format(now) + "] [" + clan + "] " + author + ": " + message + System.lineSeparator();
    Path file = plugin.getDataFolder().toPath().resolve("logs").resolve("chat-clanes-" + DAY.format(now) + ".log");
    try {
      files.execute(
          () -> {
            try {
              Files.createDirectories(file.getParent());
              Files.writeString(file, entry, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException failure) {
              plugin.getLogger().log(Level.WARNING, "No se pudo escribir el registro del chat de clanes.", failure);
            }
          });
    } catch (RejectedExecutionException closed) {
      // El plugin se está deshabilitando.
    }
  }

  /** Últimos mensajes del clan, del más antiguo al más reciente. */
  public List<String> history(UUID clan) {
    Deque<String> lines = history.get(clan);
    return lines == null ? List.of() : List.copyOf(lines);
  }

  /** Activa o desactiva el modo clan; devuelve si queda activo. */
  public boolean toggleMode(Player player) {
    if (clanMode.remove(player.getUniqueId())) return false;
    clanMode.add(player.getUniqueId());
    return true;
  }

  public boolean inClanMode(Player player) {
    return clanMode.contains(player.getUniqueId());
  }

  public boolean toggleSpy(Player player) {
    if (spies.remove(player.getUniqueId())) return false;
    spies.add(player.getUniqueId());
    return true;
  }

  /** Tras salir de un clan: si ya no tiene ninguno, se apaga su modo clan. */
  void forget(UUID player, UUID clan) {
    if (manager.getPlayerClans(player).isEmpty()) clanMode.remove(player);
  }

  public void quit(UUID player) {
    clanMode.remove(player);
    spies.remove(player);
  }

  @Override
  public void close() {
    files.shutdown();
    try {
      files.awaitTermination(5, TimeUnit.SECONDS);
    } catch (InterruptedException interrupted) {
      Thread.currentThread().interrupt();
    }
  }
}
