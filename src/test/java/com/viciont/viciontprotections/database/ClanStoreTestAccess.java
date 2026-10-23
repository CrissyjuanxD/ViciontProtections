package com.viciont.viciontprotections.database;

import com.viciont.viciontprotections.api.clan.Clan;

/** Acceso desde otros paquetes de pruebas a los datos de ejemplo. */
public final class ClanStoreTestAccess {
  private ClanStoreTestAccess() {}

  public static Clan clan(String name) {
    return ClanStoreTest.clan(name);
  }
}
