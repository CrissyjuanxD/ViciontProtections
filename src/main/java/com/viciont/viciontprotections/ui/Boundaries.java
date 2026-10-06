package com.viciont.viciontprotections.ui;

import com.viciont.viciontprotections.managers.ProtectionManager;
import com.viciont.viciontprotections.models.Protection;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** Un único temporizador, sin cargar chunks y con un límite de partículas por observador. */
public final class Boundaries implements AutoCloseable {
  private static final Color[] COLORS = {
    Color.fromRGB(186, 139, 255),
    Color.fromRGB(226, 185, 255),
    Color.fromRGB(255, 170, 216),
    Color.fromRGB(242, 203, 238)
  };
  private final Map<UUID, UUID> viewers = new HashMap<>();
  private final ProtectionManager manager;
  private final BukkitTask task;
  private int frame;

  public Boundaries(JavaPlugin plugin, ProtectionManager manager) {
    this.manager = manager;
    task = Bukkit.getScheduler().runTaskTimer(plugin, this::draw, 10, 10);
  }

  public boolean toggle(Player player, Protection protection) {
    if (protection.id().equals(viewers.get(player.getUniqueId()))) {
      viewers.remove(player.getUniqueId());
      return false;
    }
    viewers.put(player.getUniqueId(), protection.id());
    return true;
  }

  public void forget(UUID player) {
    viewers.remove(player);
  }

  private void draw() {
    frame++;
    for (var entry : new HashMap<>(viewers).entrySet()) {
      Player player = Bukkit.getPlayer(entry.getKey());
      Protection protection = manager.getProtection(entry.getValue()).orElse(null);
      if (player == null
          || protection == null
          || !player.getWorld().getName().equals(protection.worldName())
          || !com.viciont.viciontprotections.security.AccessPolicy.allows(
              player,
              protection,
              com.viciont.viciontprotections.security.AccessPolicy.Action.BORDERS)) {
        viewers.remove(entry.getKey());
        continue;
      }
      var b = protection.bounds();
      double width = b.width(), depth = b.depth(), perimeter = 2 * (width + depth);
      Location at = player.getLocation();
      int count = 160;
      for (int i = 0; i < count; i++) {
        double distance = i * perimeter / count, x, z;
        if (distance < width) {
          x = b.minX() + distance;
          z = b.minZ();
        } else if ((distance -= width) < depth) {
          x = b.maxX() + 1;
          z = b.minZ() + distance;
        } else if ((distance -= depth) < width) {
          x = b.maxX() + 1 - distance;
          z = b.maxZ() + 1;
        } else {
          x = b.minX();
          z = b.maxZ() + 1 - (distance - width);
        }
        if (Math.pow(x - at.getX(), 2) + Math.pow(z - at.getZ(), 2) > 96 * 96
            || !player
                .getWorld()
                .isChunkLoaded(((int) Math.floor(x)) >> 4, ((int) Math.floor(z)) >> 4)) continue;
        double y =
            Math.max(
                b.minY(),
                Math.min(b.maxY() + 1, at.getY() + Math.sin((i + frame) * 0.4) * 1.2 + 1));
        player.spawnParticle(
            Particle.DUST,
            x,
            y,
            z,
            1,
            new Particle.DustOptions(COLORS[(i + frame) % COLORS.length], 1.3f));
      }
    }
  }

  public void close() {
    task.cancel();
    viewers.clear();
  }
}
