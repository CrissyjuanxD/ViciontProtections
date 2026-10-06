package com.viciont.viciontprotections.api;

import java.util.Objects;
import org.bukkit.Material;

/**
 * Altura 0 protege desde el fondo hasta el techo del mundo. Las medidas son longitudes, no radios.
 */
public record ProtectionBlock(Material material, int width, int depth, int height) {
  public ProtectionBlock {
    Objects.requireNonNull(material);
    if (material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR)
      throw new IllegalArgumentException("El material debe ser un bloque colocable.");
    if (width <= 0
        || depth <= 0
        || height < 0
        || width > 30_000_000
        || depth > 30_000_000
        || height > 65536) throw new IllegalArgumentException("Las dimensiones no son válidas.");
  }

  public static ProtectionBlock square(Material material, int size) {
    return new ProtectionBlock(material, size, size, 0);
  }

  public String dimensions() {
    return width + " × " + depth + (height == 0 ? " · altura completa" : " × " + height);
  }
}
