package com.viciont.viciontprotections.api.clan;

import java.util.*;

/**
 * Protección que forma parte de un clan. El territorio principal es el del bloque de clan; el
 * resto son protecciones aportadas, que guardan sus propietarios y miembros anteriores para
 * devolvérselas a quien las aportó.
 */
public record ClanTerritory(
    UUID protection,
    UUID contributor,
    long contributedAt,
    boolean home,
    Set<UUID> previousOwners,
    Set<UUID> previousMembers) {
  public ClanTerritory {
    Objects.requireNonNull(protection);
    Objects.requireNonNull(contributor);
    previousOwners = Set.copyOf(previousOwners);
    previousMembers = Set.copyOf(previousMembers);
  }
}
