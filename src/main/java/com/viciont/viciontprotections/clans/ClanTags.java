package com.viciont.viciontprotections.clans;

import com.viciont.viciontprotections.api.clan.Clan;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.*;

/**
 * Nombre del clan encima del nick. Es una entidad de texto que sigue al jugador cada tick con
 * interpolación: no va montada sobre él (un pasajero impediría que otros plugins lo
 * teletransporten) y no usa el scoreboard, así que convive con el «below name», los equipos y los
 * prefijos de otros plugins. Su dueño no la ve, como su propio nick, y se oculta cuando el nick no
 * se vería: invisibilidad, espectador, muerte, equipos con el nombre oculto o jugadores ocultos.
 */
public final class ClanTags implements Listener, AutoCloseable {
  private static final class Tag {
    TextDisplay display;
    String text;
    boolean sneaking;
    final Set<UUID> hidden = new HashSet<>();
  }

  private final JavaPlugin plugin;
  private final ClanManager manager;
  private final ClanSettings settings;
  private final ClanMessages messages;
  private final NamespacedKey key;
  private final Map<UUID, Tag> tags = new HashMap<>();
  private final BukkitTask task;
  private int tick;

  public ClanTags(JavaPlugin plugin, ClanManager manager) {
    this.plugin = plugin;
    this.manager = manager;
    this.settings = manager.settings();
    this.messages = manager.messages();
    this.key = new NamespacedKey(plugin, "clan_tag");
    for (World world : Bukkit.getWorlds())
      for (TextDisplay display : world.getEntitiesByClass(TextDisplay.class))
        if (display.getPersistentDataContainer().has(key, PersistentDataType.BYTE)) display.remove();
    task = Bukkit.getScheduler().runTaskTimer(plugin, this::update, 5, 1);
  }

  private void update() {
    tick++;
    boolean enabled = settings.enabled() && settings.flag("etiqueta.activa");
    Set<UUID> online = new HashSet<>();
    for (Player player : Bukkit.getOnlinePlayers()) {
      online.add(player.getUniqueId());
      String text = enabled ? text(player) : null;
      Tag tag = tags.get(player.getUniqueId());
      if (text == null) {
        if (tag != null) remove(player.getUniqueId());
        continue;
      }
      Location target = target(player);
      if (tag == null
          || tag.display == null
          || !tag.display.isValid()
          || tag.display.getWorld() != target.getWorld()
          || tag.display.getLocation().distanceSquared(target) > 64) {
        if (tag != null) remove(player.getUniqueId());
        tag = spawn(player, target, text);
        tags.put(player.getUniqueId(), tag);
        visibility(player, tag);
        continue;
      }
      if (tag.display.getLocation().distanceSquared(target) > 1.0E-4) tag.display.teleport(target);
      if (!text.equals(tag.text)) {
        tag.display.setText(text);
        tag.text = text;
      }
      boolean sneaking = player.isSneaking();
      if (sneaking != tag.sneaking) {
        // Como el nick: al agacharse no se ve a través de los bloques y se atenúa.
        tag.display.setSeeThrough(!sneaking);
        tag.display.setTextOpacity(sneaking ? (byte) 0x80 : (byte) -1);
        tag.sneaking = sneaking;
      }
      if (tick % 20 == 0) visibility(player, tag);
    }
    for (UUID id : List.copyOf(tags.keySet())) if (!online.contains(id)) remove(id);
  }

  /** Texto de la etiqueta, o null si no debe verse. */
  private String text(Player player) {
    if (player.isDead()
        || player.getGameMode() == GameMode.SPECTATOR
        || player.isInvisible()
        || player.hasPotionEffect(PotionEffectType.INVISIBILITY)
        || !player.getPassengers().isEmpty()
        || settings.worldDisabled(player.getWorld().getName())
        || hiddenByTeam(player)) return null;
    Clan clan = manager.getPlayerClan(player.getUniqueId()).orElse(null);
    if (clan == null) return null;
    boolean war = manager.wars().active(clan.id()).map(w -> w.fighting(System.currentTimeMillis())).orElse(false);
    String format = settings.text(war ? "etiqueta.formato-guerra" : "etiqueta.formato");
    return format.isBlank() ? null : messages.color(ClanChat.fill(format, clan));
  }

  private static boolean hiddenByTeam(Player player) {
    Team team = player.getScoreboard().getEntryTeam(player.getName());
    return team != null && team.getOption(Team.Option.NAME_TAG_VISIBILITY) == Team.OptionStatus.NEVER;
  }

  private Location target(Player player) {
    Location location = player.getLocation();
    location.setYaw(0);
    location.setPitch(0);
    return location.add(0, player.getHeight() + height(player), 0);
  }

  /** Sobre el nick; más alto si el servidor muestra algo debajo del nombre. */
  private double height(Player player) {
    String value = settings.text("etiqueta.altura").trim();
    if (!value.equalsIgnoreCase("auto"))
      try {
        return Double.parseDouble(value.replace(',', '.'));
      } catch (NumberFormatException ignored) {
      }
    return player.getScoreboard().getObjective(DisplaySlot.BELOW_NAME) != null ? 0.8 : 0.55;
  }

  private Tag spawn(Player owner, Location target, String text) {
    Tag tag = new Tag();
    tag.text = text;
    float range = (float) Math.max(8, settings.number("etiqueta.distancia", 48)) / 64f;
    tag.display =
        target
            .getWorld()
            .spawn(
                target,
                TextDisplay.class,
                display -> {
                  display.setPersistent(false);
                  display.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
                  display.setBillboard(Display.Billboard.CENTER);
                  display.setAlignment(TextDisplay.TextAlignment.CENTER);
                  display.setSeeThrough(true);
                  display.setShadowed(false);
                  display.setBackgroundColor(Color.fromARGB(64, 0, 0, 0));
                  display.setViewRange(range);
                  display.setTeleportDuration(3);
                  display.setText(text);
                  owner.hideEntity(plugin, display);
                });
    return tag;
  }

  /**
   * Quien no puede ver al jugador (vanish, otros plugins) tampoco ve su etiqueta, y el dueño nunca
   * ve la suya (también si se creó antes de que su conexión terminara de entrar).
   */
  private void visibility(Player owner, Tag tag) {
    if (owner.canSee(tag.display)) owner.hideEntity(plugin, tag.display);
    for (Player viewer : Bukkit.getOnlinePlayers()) {
      if (viewer == owner) continue;
      boolean see = viewer.canSee(owner);
      if (!see && tag.hidden.add(viewer.getUniqueId())) viewer.hideEntity(plugin, tag.display);
      else if (see && tag.hidden.remove(viewer.getUniqueId())) viewer.showEntity(plugin, tag.display);
    }
  }

  private void remove(UUID player) {
    Tag tag = tags.remove(player);
    if (tag != null && tag.display != null) tag.display.remove();
  }

  @EventHandler
  public void join(PlayerJoinEvent event) {
    Player joined = event.getPlayer();
    Bukkit.getScheduler()
        .runTaskLater(
            plugin,
            () -> {
              for (var entry : tags.entrySet()) {
                Player owner = Bukkit.getPlayer(entry.getKey());
                if (owner != null && joined.isOnline()) visibility(owner, entry.getValue());
              }
            },
            5);
  }

  @EventHandler
  public void quit(PlayerQuitEvent event) {
    remove(event.getPlayer().getUniqueId());
    for (Tag tag : tags.values()) tag.hidden.remove(event.getPlayer().getUniqueId());
  }

  @Override
  public void close() {
    task.cancel();
    for (UUID id : List.copyOf(tags.keySet())) remove(id);
  }
}
