package com.viciont.viciontprotections.api.clan;

import java.util.*;

/**
 * Casilla de permiso de la tabla de rangos. El líder siempre la tiene; para el resto de rangos
 * parte de {@code defaults} y el líder puede cambiarla salvo que el servidor la bloquee.
 *
 * @param key clave única en minúsculas, por ejemplo {@code banco-retirar}
 * @param name texto de la casilla en el menú de permisos
 */
public record ClanPermission(String key, String name, String description, Set<ClanRank> defaults) {
  public ClanPermission {
    Objects.requireNonNull(key);
    Objects.requireNonNull(name);
    if (!key.matches("[a-z0-9][a-z0-9_-]{0,47}"))
      throw new IllegalArgumentException("Clave de permiso no válida: " + key);
    description = description == null ? "" : description;
    defaults = defaults.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(defaults));
  }
}
