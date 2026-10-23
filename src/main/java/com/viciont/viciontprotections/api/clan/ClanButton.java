package com.viciont.viciontprotections.api.clan;

import java.util.Objects;

/**
 * Botón que otro plugin añade al menú del clan. Lo ven los miembros con al menos {@code minimum}
 * y, si {@code permission} no es nulo, con esa casilla de permiso. El comando se ejecuta como el
 * jugador y admite {@code %clan%} (ID del clan), {@code %clan_nombre%} y {@code %jugador%}.
 *
 * @param label texto del botón con los colores de messages.yml, por ejemplo {@code {dato}Banco}
 */
public record ClanButton(
    String key,
    String label,
    String description,
    ClanRank minimum,
    String permission,
    String command) {
  public ClanButton {
    Objects.requireNonNull(key);
    Objects.requireNonNull(label);
    Objects.requireNonNull(command);
    minimum = minimum == null ? ClanRank.GUEST : minimum;
    description = description == null ? "" : description;
    if (!command.startsWith("/"))
      throw new IllegalArgumentException("El comando del botón debe empezar por /.");
  }
}
