package com.viciont.viciontprotections.api.clan;

import java.text.Normalizer;
import java.util.*;

/**
 * Los cinco rangos fijos de un clan, de menor a mayor. Sus nombres visibles se cambian en
 * clans.yml; {@link #key()} es la clave usada en comandos, configuración y base de datos.
 */
public enum ClanRank {
  GUEST("invitado"),
  APPRENTICE("aprendiz"),
  OFFICER("oficial"),
  CO_LEADER("sublider"),
  LEADER("lider");

  private final String key;

  ClanRank(String key) {
    this.key = key;
  }

  public String key() {
    return key;
  }

  public boolean isAbove(ClanRank other) {
    return compareTo(other) > 0;
  }

  public boolean isAtLeast(ClanRank other) {
    return compareTo(other) >= 0;
  }

  /** Rango inmediatamente superior, o vacío para el líder. */
  public Optional<ClanRank> next() {
    return ordinal() + 1 < values().length ? Optional.of(values()[ordinal() + 1]) : Optional.empty();
  }

  /** Rango inmediatamente inferior, o vacío para el invitado. */
  public Optional<ClanRank> previous() {
    return ordinal() > 0 ? Optional.of(values()[ordinal() - 1]) : Optional.empty();
  }

  /** Acepta la clave en español con o sin tilde, o el nombre de la constante. */
  public static Optional<ClanRank> parse(String value) {
    if (value == null) return Optional.empty();
    String plain =
        Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "")
            .toLowerCase(Locale.ROOT)
            .replace("-", "")
            .replace("_", "");
    for (ClanRank rank : values())
      if (rank.key.equals(plain) || rank.name().replace("_", "").equalsIgnoreCase(plain))
        return Optional.of(rank);
    return Optional.empty();
  }
}
