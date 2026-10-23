package com.viciont.viciontprotections.api.clan;

import java.util.Objects;
import java.util.UUID;

/**
 * Pertenencia de un jugador a un clan. {@code active} marca el clan que muestra quien está en
 * varios por decisión de la administración.
 */
public record ClanMember(UUID player, ClanRank rank, long joinedAt, boolean active) {
  public ClanMember {
    Objects.requireNonNull(player);
    Objects.requireNonNull(rank);
  }

  public ClanMember withRank(ClanRank value) {
    return new ClanMember(player, value, joinedAt, active);
  }

  public ClanMember withActive(boolean value) {
    return new ClanMember(player, rank, joinedAt, value);
  }
}
