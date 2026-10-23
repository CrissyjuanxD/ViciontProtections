package com.viciont.viciontprotections.api.clan;

import com.viciont.viciontprotections.api.CreateProtectionRequest;
import com.viciont.viciontprotections.api.ProtectionBlock;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

/**
 * Servicio de clanes: {@code Bukkit.getServicesManager().load(ViciontClansApi.class)}. Convive con
 * {@code ViciontProtectionsApi}, que no cambia.
 *
 * <p>Las consultas devuelven instantáneas inmutables y se pueden hacer desde cualquier hilo,
 * salvo {@link #getClanAt}, que usa WorldGuard y requiere el hilo principal. Las escrituras
 * admiten cualquier hilo y completan su futuro en el principal tras guardarse; nunca bloquees el
 * hilo del servidor esperando un futuro. Los cambios lanzados desde la API no comprueban los
 * permisos de ningún jugador: hazlo antes de pedirlos.
 */
public interface ViciontClansApi {
  // ------------------------------------------------------------------ consultas

  List<Clan> getClans();

  Optional<Clan> getClan(UUID clan);

  /** Sin distinguir mayúsculas. */
  Optional<Clan> getClanByName(String name);

  /** Clan activo del jugador: el único al que pertenece o el elegido con /clan activo. */
  Optional<Clan> getPlayerClan(UUID player);

  /** Todos los clanes del jugador (más de uno solo si la administración lo puso en varios). */
  List<Clan> getPlayerClans(UUID player);

  /** Clan dueño del territorio en esa ubicación. Hilo principal. */
  Optional<Clan> getClanAt(Location location);

  Optional<ClanRank> getRank(UUID clan, UUID player);

  /**
   * Casilla de permiso del rango del jugador en ese clan: las del plugin ({@code construir},
   * {@code cofres}, {@code invitar}...) o las registradas por extensiones.
   */
  boolean hasPermission(UUID clan, UUID player, String permission);

  /** Casillas disponibles, incluidas las de extensiones, en el orden del menú de permisos. */
  List<ClanPermission> getPermissions();

  /** Guerra en curso del clan. */
  Optional<ClanWar> getWar(UUID clan);

  /** Guerras del clan, la más reciente primero, incluida la que esté en curso. */
  List<ClanWar> getWars(UUID clan);

  /** Nombre visible del rango según clans.yml. */
  String getRankName(ClanRank rank);

  // ------------------------------------------------------------------ acciones

  /** Bloque de clan: al colocarlo, quien no tenga clan lo funda en esa zona. Hilo principal. */
  ItemStack createClanBlock(ProtectionBlock block, int amount);

  boolean isClanBlock(ItemStack item);

  /**
   * Funda un clan sobre una zona sin pasar por los requisitos de fundación (por ejemplo el
   * depósito). Con ancla, el bloque de esa posición pasa a ser el bloque de clan.
   */
  CompletableFuture<Clan> foundClan(
      UUID founder, String name, String color, CreateProtectionRequest territory);

  /**
   * Disuelve el clan indicando el motivo, que se anuncia a sus miembros. El bloque de clan y su
   * zona se pierden y las protecciones aportadas vuelven a quien las aportó. Se guarda una copia
   * que el líder puede recuperar durante unos días (clans.yml, clanes.copia-dias).
   */
  CompletableFuture<Void> disbandClan(UUID clan, String reason);

  /**
   * Como {@link #disbandClan(UUID, String)}; con {@code unpaid} el clan se disuelve por no pagar
   * (por ejemplo, el mantenimiento) y no se guarda ninguna copia.
   */
  CompletableFuture<Void> disbandClan(UUID clan, String reason, boolean unpaid);

  /** Invitación de 5 minutos. {@code inviter} puede ser nulo. */
  CompletableFuture<Void> invite(UUID clan, UUID player, ClanRank rank, UUID inviter);

  /** Añade directamente, aunque el jugador ya esté en otro clan. No sirve para nombrar líder. */
  CompletableFuture<Clan> addMember(UUID clan, UUID player, ClanRank rank);

  CompletableFuture<Clan> removeMember(UUID clan, UUID player);

  /** Cambia el rango; con {@link ClanRank#LEADER} transfiere el liderazgo. */
  CompletableFuture<Clan> setRank(UUID clan, UUID player, ClanRank rank);

  /** Convierte una protección personal de un miembro en territorio del clan. */
  CompletableFuture<Clan> addTerritory(UUID clan, UUID protection);

  /** Publica un aviso en el chat del clan, por ejemplo «Alex ha aportado +5 DinoCoins». */
  void broadcast(UUID clan, String message);

  /** Anota en la guerra la recompensa pagada, para la crónica del clan. */
  CompletableFuture<ClanWar> setWarReward(UUID war, String reward);

  // ------------------------------------------------------------------ extensiones

  /** Añade un botón al menú del clan. Una clave repetida reemplaza al botón anterior. */
  void registerButton(Plugin plugin, ClanButton button);

  /**
   * Línea de estado del menú del clan, como el saldo. La función se llama en el hilo principal
   * al abrir el menú y devuelve texto con colores o null para no mostrar nada.
   */
  void registerStatusLine(Plugin plugin, String key, BiFunction<Clan, Player, String> line);

  /** Casilla de permiso propia que el líder ajusta en el menú de permisos. */
  void registerPermission(Plugin plugin, ClanPermission permission);

  void registerFoundingRequirement(Plugin plugin, FoundingRequirement requirement);

  /**
   * Tema propio en la guía de clanes, por ejemplo «Banco del clan». Las líneas admiten los colores
   * de la paleta de clanes ({texto}, {dato}...).
   */
  void registerGuideTopic(Plugin plugin, String key, String title, List<String> lines);

  /** Retira todo lo registrado por ese plugin; también ocurre al deshabilitarse. */
  void unregisterAll(Plugin plugin);
}
