package com.viciont.viciontprotections.ui;

import com.viciont.viciontprotections.api.Bounds;
import com.viciont.viciontprotections.managers.ProtectionManager;
import com.viciont.viciontprotections.models.Protection;
import com.viciont.viciontprotections.security.AccessPolicy;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Bordes personales con partículas en degradado morado y rosa pastel. Un único temporizador, sin
 * cargar chunks, solo cerca del observador y con un límite de partículas por jugador.
 */
public final class Boundaries implements AutoCloseable {
  private static final Color[] COLORS = {
    Color.fromRGB(176, 124, 255), // morado
    Color.fromRGB(212, 179, 255), // morado pastel
    Color.fromRGB(245, 179, 218), // rosa pastel
    Color.fromRGB(240, 106, 174), // rosa
    Color.fromRGB(233, 200, 255), // lila
    Color.fromRGB(255, 196, 228) // rosa claro
  };
  /** El cliente ignora partículas normales a más de 32 bloques. */
  private static final double RADIUS = 30;

  private static final int BUDGET = 700;

  private final Map<UUID, UUID> viewers = new HashMap<>();
  private final ProtectionManager manager;
  private final BukkitTask task;
  private int frame;

  public Boundaries(JavaPlugin plugin, ProtectionManager manager) {
    this.manager = manager;
    task = Bukkit.getScheduler().runTaskTimer(plugin, this::draw, 10, 8);
  }

  /** Alterna los límites de una protección para ese jugador; devuelve si quedan visibles. */
  public boolean toggle(Player player, Protection protection) {
    if (isViewing(player, protection)) {
      viewers.remove(player.getUniqueId());
      return false;
    }
    viewers.put(player.getUniqueId(), protection.id());
    return true;
  }

  public boolean isViewing(Player player, Protection protection) {
    return protection.id().equals(viewers.get(player.getUniqueId()));
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
          || !AccessPolicy.allows(player, protection, AccessPolicy.Action.BORDERS)) {
        viewers.remove(entry.getKey());
        continue;
      }
      draw(player, protection.bounds());
    }
  }

  private void draw(Player player, Bounds b) {
    Location at = player.getLocation();
    World world = player.getWorld();
    // El borde exterior de los bloques: maxX + 1 y maxZ + 1.
    double minX = b.minX(), minZ = b.minZ(), maxX = b.maxX() + 1, maxZ = b.maxZ() + 1;
    double width = maxX - minX, depth = maxZ - minZ, perimeter = 2 * (width + depth);
    double low = clampY(b, at.getY() + 0.3), high = clampY(b, at.getY() + 2.1);
    int budget = BUDGET;
    for (double distance = 0; distance < perimeter && budget > 0; distance += 0.75) {
      double x, z;
      double d = distance;
      if (d < width) {
        x = minX + d;
        z = minZ;
      } else if ((d -= width) < depth) {
        x = maxX;
        z = minZ + d;
      } else if ((d -= depth) < width) {
        x = maxX - d;
        z = maxZ;
      } else {
        x = minX;
        z = maxZ - (d - width);
      }
      if (!near(at, x, z) || !world.isChunkLoaded(floor(x) >> 4, floor(z) >> 4)) continue;
      Color color = gradient(distance / 6.0 + frame * 0.35);
      dust(player, x, low, z, color, 1.15f);
      if (high > low) dust(player, x, high, z, gradient(distance / 6.0 + frame * 0.35 + 1.5), 0.9f);
      budget -= 2;
    }
    // Columnas en las esquinas, con un brillo que sube.
    for (double[] corner : new double[][] {{minX, minZ}, {maxX, minZ}, {maxX, maxZ}, {minX, maxZ}}) {
      if (!near(at, corner[0], corner[1])
          || !world.isChunkLoaded(floor(corner[0]) >> 4, floor(corner[1]) >> 4)) continue;
      for (int step = 0; step < 14 && budget > 0; step++, budget--) {
        double y = clampY(b, at.getY() - 1.5 + step * 0.5);
        boolean spark = (step + frame) % 7 == 0;
        dust(player, corner[0], y, corner[1], spark ? COLORS[3] : gradient(step * 0.5 + frame * 0.2), spark ? 1.6f : 1.2f);
      }
    }
  }

  private static boolean near(Location at, double x, double z) {
    double dx = x - at.getX(), dz = z - at.getZ();
    return dx * dx + dz * dz <= RADIUS * RADIUS;
  }

  private static double clampY(Bounds b, double y) {
    return Math.max(b.minY(), Math.min(b.maxY() + 1, y));
  }

  private static int floor(double value) {
    return (int) Math.floor(value);
  }

  /** Interpola suavemente entre los colores de la paleta. */
  private static Color gradient(double position) {
    double wrapped = ((position % COLORS.length) + COLORS.length) % COLORS.length;
    int index = (int) wrapped;
    double t = wrapped - index;
    Color a = COLORS[index], c = COLORS[(index + 1) % COLORS.length];
    return Color.fromRGB(
        (int) Math.round(a.getRed() + (c.getRed() - a.getRed()) * t),
        (int) Math.round(a.getGreen() + (c.getGreen() - a.getGreen()) * t),
        (int) Math.round(a.getBlue() + (c.getBlue() - a.getBlue()) * t));
  }

  private static void dust(Player player, double x, double y, double z, Color color, float size) {
    player.spawnParticle(Particle.DUST, x, y, z, 1, 0, 0, 0, 0, new Particle.DustOptions(color, size));
  }

  @Override
  public void close() {
    task.cancel();
    viewers.clear();
  }
}
