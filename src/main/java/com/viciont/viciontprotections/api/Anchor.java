package com.viciont.viciontprotections.api;

import java.util.Objects;

/**
 * Bloque físico que originó una protección. Las regiones creadas mediante API pueden no tenerlo.
 */
public record Anchor(int x, int y, int z, ProtectionBlock block) {
  public Anchor {
    Objects.requireNonNull(block);
  }
}
