package com.viciont.viciontprotections.api.clan;

import java.util.*;

/**
 * Guerra entre dos clanes. Mientras {@code endedAt} sea 0 está en curso; el PvP entre ambos
 * empieza en {@code startsAt}. Los nombres se guardan para que la historia siga legible aunque un
 * clan se disuelva.
 *
 * @param tribute DinoCoins (u otra moneda) que ofreció el clan que se rindió, además del mínimo
 * @param reward texto que anota el plugin que pagó la recompensa, por ejemplo «35 DinoCoins»
 * @param chronicle bajas y hechos de la guerra, del más antiguo al más reciente
 */
public record ClanWar(
    UUID id,
    UUID attacker,
    UUID defender,
    String attackerName,
    String defenderName,
    String reason,
    long declaredAt,
    long startsAt,
    long endedAt,
    Ending ending,
    UUID winner,
    int attackerKills,
    int defenderKills,
    String terms,
    int tribute,
    String reward,
    UUID peaceProposedBy,
    String peaceTerms,
    List<String> chronicle) {
  /** Cómo terminó una guerra. */
  public enum Ending {
    /** Un líder se rindió: gana el otro bando. */
    SURRENDER,
    /** Los dos líderes firmaron un tratado de paz. */
    PEACE,
    /** La administración la cerró. */
    ADMIN,
    /** Nadie la terminó dentro del plazo máximo. */
    EXPIRED,
    /** Uno de los clanes se disolvió. */
    DISBANDED
  }

  public ClanWar {
    Objects.requireNonNull(id);
    Objects.requireNonNull(attacker);
    Objects.requireNonNull(defender);
    chronicle = List.copyOf(chronicle);
  }

  public boolean active() {
    return endedAt == 0;
  }

  /** El PvP entre los dos clanes está abierto en ese instante. */
  public boolean fighting(long now) {
    return active() && now >= startsAt;
  }

  public boolean involves(UUID clan) {
    return attacker.equals(clan) || defender.equals(clan);
  }

  public UUID enemyOf(UUID clan) {
    return attacker.equals(clan) ? defender : attacker;
  }

  public String nameOf(UUID clan) {
    return attacker.equals(clan) ? attackerName : defenderName;
  }

  public int killsOf(UUID clan) {
    return attacker.equals(clan) ? attackerKills : defenderKills;
  }
}
