package com.viciont.viciontprotections.api;

/** Cuboide de coordenadas de bloque; ambos extremos están incluidos. */
public record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
  public Bounds {
    if (minX > maxX || minY > maxY || minZ > maxZ)
      throw new IllegalArgumentException("Límites invertidos.");
    if (minX < -30_000_000 || maxX > 30_000_000 || minZ < -30_000_000 || maxZ > 30_000_000)
      throw new IllegalArgumentException("La protección supera los límites del mundo.");
  }

  public int width() {
    return maxX - minX + 1;
  }

  public int height() {
    return maxY - minY + 1;
  }

  public int depth() {
    return maxZ - minZ + 1;
  }

  public boolean contains(int x, int y, int z) {
    return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
  }

  public boolean intersects(Bounds other) {
    return minX <= other.maxX
        && maxX >= other.minX
        && minY <= other.maxY
        && maxY >= other.minY
        && minZ <= other.maxZ
        && maxZ >= other.minZ;
  }

  public static Bounds centered(
      int x, int y, int z, int width, int depth, int height, int minWorldY, int maxWorldY) {
    if (width <= 0 || depth <= 0 || height < 0)
      throw new IllegalArgumentException("El tamaño debe ser positivo.");
    int minX = Math.subtractExact(x, width / 2), minZ = Math.subtractExact(z, depth / 2);
    int minY = height == 0 ? minWorldY : Math.subtractExact(y, height / 2);
    int maxY = height == 0 ? maxWorldY - 1 : Math.addExact(minY, height - 1);
    if (minY < minWorldY || maxY >= maxWorldY)
      throw new IllegalArgumentException("La altura queda fuera del mundo.");
    return new Bounds(
        minX, minY, minZ, Math.addExact(minX, width - 1), maxY, Math.addExact(minZ, depth - 1));
  }
}
