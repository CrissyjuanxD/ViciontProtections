package com.viciont.viciontprotections.api;

import java.util.Objects;
import java.util.UUID;

/**
 * Solicitud para crear una región, con o sin bloque físico. El nombre del mundo es estable al
 * migrar servidores.
 */
public record CreateProtectionRequest(
    UUID primaryOwner, String worldName, Bounds bounds, String name, Anchor anchor) {
  public CreateProtectionRequest {
    Objects.requireNonNull(primaryOwner);
    Objects.requireNonNull(worldName);
    Objects.requireNonNull(bounds);
  }

  public static CreateProtectionRequest region(
      UUID owner, String world, Bounds bounds, String name) {
    return new CreateProtectionRequest(owner, world, bounds, name, null);
  }
}
