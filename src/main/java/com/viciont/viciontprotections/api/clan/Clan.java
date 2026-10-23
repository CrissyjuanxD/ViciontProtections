package com.viciont.viciontprotections.api.clan;

import java.util.*;

/**
 * Instantánea inmutable de un clan; los cambios se hacen con {@link ViciontClansApi}.
 *
 * @param color color del clan en formato {@code #RRGGBB}
 * @param home protección del bloque de clan (territorio principal)
 * @param permissions cambios del líder sobre los permisos por defecto de cada rango
 * @param flags banderas de WorldGuard que el líder aplicó a todos sus territorios
 */
public record Clan(
    UUID id,
    String name,
    String color,
    UUID leader,
    UUID home,
    long foundedAt,
    Map<UUID, ClanMember> members,
    Map<UUID, ClanTerritory> territories,
    Map<ClanRank, Map<String, Boolean>> permissions,
    Map<String, String> flags) {
  public Clan {
    Objects.requireNonNull(id);
    Objects.requireNonNull(name);
    Objects.requireNonNull(color);
    Objects.requireNonNull(leader);
    members = Map.copyOf(members);
    territories = Map.copyOf(territories);
    Map<ClanRank, Map<String, Boolean>> copy = new EnumMap<>(ClanRank.class);
    permissions.forEach((rank, values) -> copy.put(rank, Map.copyOf(values)));
    permissions = Collections.unmodifiableMap(copy);
    flags = Map.copyOf(flags);
    ClanMember chief = members.get(leader);
    if (chief == null || chief.rank() != ClanRank.LEADER)
      throw new IllegalArgumentException("El líder debe ser miembro con rango de líder.");
    if (members.values().stream().filter(m -> m.rank() == ClanRank.LEADER).count() != 1)
      throw new IllegalArgumentException("Un clan tiene exactamente un líder.");
  }

  public Optional<ClanRank> rank(UUID player) {
    ClanMember member = members.get(player);
    return member == null ? Optional.empty() : Optional.of(member.rank());
  }

  public boolean isMember(UUID player) {
    return members.containsKey(player);
  }

  /** Miembros de ese rango ordenados por antigüedad. */
  public List<ClanMember> members(ClanRank rank) {
    return members.values().stream()
        .filter(m -> m.rank() == rank)
        .sorted(Comparator.comparingLong(ClanMember::joinedAt))
        .toList();
  }

  public Clan withMembers(UUID newLeader, Map<UUID, ClanMember> value) {
    return new Clan(id, name, color, newLeader, home, foundedAt, value, territories, permissions, flags);
  }

  public Clan withTerritories(UUID newHome, Map<UUID, ClanTerritory> value) {
    return new Clan(id, name, color, leader, newHome, foundedAt, members, value, permissions, flags);
  }

  public Clan withName(String value) {
    return new Clan(id, value, color, leader, home, foundedAt, members, territories, permissions, flags);
  }

  public Clan withColor(String value) {
    return new Clan(id, name, value, leader, home, foundedAt, members, territories, permissions, flags);
  }

  public Clan withPermissions(Map<ClanRank, Map<String, Boolean>> value) {
    return new Clan(id, name, color, leader, home, foundedAt, members, territories, value, flags);
  }

  public Clan withFlags(Map<String, String> value) {
    return new Clan(id, name, color, leader, home, foundedAt, members, territories, permissions, value);
  }
}
