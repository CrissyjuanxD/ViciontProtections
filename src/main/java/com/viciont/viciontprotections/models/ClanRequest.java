package com.viciont.viciontprotections.models;

import com.viciont.viciontprotections.api.clan.ClanRank;
import java.util.*;

/**
 * Invitación a un clan o solicitud para aportar una protección como territorio. Caduca en {@code
 * expiresAt}.
 *
 * @param player el invitado o quien ofrece su protección
 * @param protection la protección ofrecida; null en invitaciones
 * @param rank rango con el que entra el invitado; null en solicitudes de territorio
 */
public record ClanRequest(
    UUID id,
    Type type,
    UUID clan,
    UUID player,
    UUID protection,
    ClanRank rank,
    UUID createdBy,
    long createdAt,
    long expiresAt) {
  public enum Type {
    INVITE,
    TERRITORY
  }

  public ClanRequest {
    Objects.requireNonNull(id);
    Objects.requireNonNull(type);
    Objects.requireNonNull(clan);
    Objects.requireNonNull(player);
    if (type == Type.INVITE && rank == null)
      throw new IllegalArgumentException("Una invitación necesita un rango.");
    if (type == Type.TERRITORY && protection == null)
      throw new IllegalArgumentException("Una solicitud de territorio necesita una protección.");
  }

  public boolean expired(long now) {
    return now >= expiresAt;
  }
}
