package com.viciont.viciontprotections.models;

import com.viciont.viciontprotections.api.Anchor;
import com.viciont.viciontprotections.api.Bounds;
import java.util.*;

/** Instantánea inmutable: las modificaciones se realizan mediante ViciontProtectionsApi. */
public record Protection(
    UUID id,
    String name,
    String worldName,
    Bounds bounds,
    UUID primaryOwner,
    Set<UUID> owners,
    Set<UUID> members,
    Map<String, String> flags,
    Anchor anchor,
    long createdAt) {
  public Protection {
    Objects.requireNonNull(id);
    Objects.requireNonNull(name);
    Objects.requireNonNull(worldName);
    Objects.requireNonNull(bounds);
    Objects.requireNonNull(primaryOwner);
    owners = Set.copyOf(owners);
    members = Set.copyOf(members);
    flags = Map.copyOf(flags);
    if (owners.contains(primaryOwner)
        || members.contains(primaryOwner)
        || !Collections.disjoint(owners, members))
      throw new IllegalArgumentException("Los roles de una protección no pueden duplicarse.");
  }

  public boolean isOwner(UUID player) {
    return primaryOwner.equals(player) || owners.contains(player);
  }

  public boolean canAccess(UUID player) {
    return isOwner(player) || members.contains(player);
  }

  public String shortId() {
    return id.toString().substring(0, 8);
  }

  public String regionId() {
    return "vp_" + id.toString().replace("-", "");
  }

  public Protection withName(String value) {
    return new Protection(
        id, value, worldName, bounds, primaryOwner, owners, members, flags, anchor, createdAt);
  }

  public Protection withRoles(UUID creator, Set<UUID> newOwners, Set<UUID> newMembers) {
    return new Protection(
        id, name, worldName, bounds, creator, newOwners, newMembers, flags, anchor, createdAt);
  }

  public Protection withFlags(Map<String, String> value) {
    return new Protection(
        id, name, worldName, bounds, primaryOwner, owners, members, value, anchor, createdAt);
  }
}
