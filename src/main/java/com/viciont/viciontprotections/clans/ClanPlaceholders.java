package com.viciont.viciontprotections.clans;

import com.viciont.viciontprotections.api.clan.*;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Marcadores de PlaceholderAPI: %viciont_clan% (como sale en el chat), %viciont_clan_nombre%,
 * %viciont_clan_rango%, %viciont_clan_color% y %viciont_clan_guerra%. Solo se carga si
 * PlaceholderAPI está instalado.
 */
public final class ClanPlaceholders extends PlaceholderExpansion {
  private final JavaPlugin plugin;
  private final ClanManager manager;

  private ClanPlaceholders(JavaPlugin plugin, ClanManager manager) {
    this.plugin = plugin;
    this.manager = manager;
  }

  public static void register(JavaPlugin plugin, ClanManager manager) {
    new ClanPlaceholders(plugin, manager).register();
  }

  @Override
  public String getIdentifier() {
    return "viciont";
  }

  @Override
  public String getAuthor() {
    return "CrissyjuanxD";
  }

  @Override
  public String getVersion() {
    return plugin.getDescription().getVersion();
  }

  @Override
  public boolean persist() {
    return true;
  }

  @Override
  public String onRequest(OfflinePlayer player, String params) {
    if (player == null) return "";
    Clan clan = manager.getPlayerClan(player.getUniqueId()).orElse(null);
    return switch (params.toLowerCase(java.util.Locale.ROOT)) {
      case "clan" -> clan == null ? "" : manager.messages().color(ClanChat.fill(manager.settings().text("chat.publico"), clan));
      case "clan_nombre" -> clan == null ? "" : clan.name();
      case "clan_rango" -> clan == null ? "" : clan.rank(player.getUniqueId()).map(manager::getRankName).orElse("");
      case "clan_color" -> clan == null ? "" : clan.color();
      case "clan_guerra" ->
          clan == null ? "" : manager.wars().active(clan.id()).map(w -> w.nameOf(w.enemyOf(clan.id()))).orElse("");
      default -> null;
    };
  }
}
