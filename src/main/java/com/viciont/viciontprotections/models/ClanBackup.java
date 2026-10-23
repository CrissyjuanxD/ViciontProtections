package com.viciont.viciontprotections.models;

import com.viciont.viciontprotections.api.clan.Clan;
import java.util.Objects;

/**
 * Copia de un clan disuelto, sin territorios. Su líder la recupera colocando un bloque de clan
 * antes de que caduque: vuelven el nombre, el color, los rangos, los permisos, las banderas y la
 * historia de guerras, y el territorio principal pasa a ser el del bloque nuevo.
 *
 * @param homeSize medidas del territorio principal que tenía, solo para mostrarlas
 */
public record ClanBackup(Clan clan, long dissolvedAt, String reason, String homeSize) {
  public ClanBackup {
    Objects.requireNonNull(clan);
    reason = reason == null ? "" : reason;
    homeSize = homeSize == null ? "" : homeSize;
  }

  public boolean expired(long now, long keep) {
    return now >= dissolvedAt + keep;
  }
}
