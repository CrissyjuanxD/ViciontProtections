package com.viciont.viciontprotections.clans;

import com.sk89q.worldguard.bukkit.protection.events.DisallowedPVPEvent;
import com.viciont.viciontprotections.api.clan.*;
import com.viciont.viciontprotections.listeners.ProtectionListener;
import com.viciont.viciontprotections.models.ClanRequest;
import com.viciont.viciontprotections.models.Protection;
import com.viciont.viciontprotections.ui.ClanViews;
import java.util.*;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.hanging.*;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.*;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.projectiles.ProjectileSource;

/**
 * WorldGuard deja pasar a todo el clan a sus territorios; aquí se aplican las casillas de cada
 * rango (construir, cofres, puertas, animales). También abre el PvP de las guerras, anota las
 * bajas y gestiona los bloques y avisos de los territorios.
 */
public final class ClanListener implements Listener, ProtectionListener.Territories {
  private final JavaPlugin plugin;
  private final ClanManager manager;
  private final ClanMessages messages;
  private final ClanViews views;
  private final Map<UUID, Long> warned = new HashMap<>();

  public ClanListener(JavaPlugin plugin, ClanManager manager, ClanViews views) {
    this.plugin = plugin;
    this.manager = manager;
    this.messages = manager.messages();
    this.views = views;
  }

  // ------------------------------------------------------------------ casillas de rango

  /** true si el rango del jugador en ese territorio no tiene la casilla (y se le avisa). */
  private boolean denied(Player player, Location location, String permission) {
    if (player.hasPermission("viciontprotections.admin.manage")) return false;
    Clan clan = manager.getClanAt(location).orElse(null);
    if (clan == null) return false;
    ClanRank rank = clan.rank(player.getUniqueId()).orElse(null);
    if (rank == null || manager.allows(clan, rank, permission)) return false;
    long now = System.currentTimeMillis();
    Long last = warned.get(player.getUniqueId());
    if (last == null || now - last > 1500) {
      warned.put(player.getUniqueId(), now);
      String what = manager.permission(permission).map(p -> p.name().toLowerCase(Locale.ROOT)).orElse(permission);
      messages.actionBar(player, "{error}Tu rango (" + manager.getRankName(rank) + ") no permite: " + what + ".");
    }
    return true;
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void place(BlockPlaceEvent event) {
    if (denied(event.getPlayer(), event.getBlockPlaced().getLocation(), "construir")) event.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void breakBlock(BlockBreakEvent event) {
    if (denied(event.getPlayer(), event.getBlock().getLocation(), "construir")) event.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void bucket(PlayerBucketEmptyEvent event) {
    if (denied(event.getPlayer(), event.getBlock().getLocation(), "construir")) event.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void bucket(PlayerBucketFillEvent event) {
    if (denied(event.getPlayer(), event.getBlock().getLocation(), "construir")) event.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void sign(SignChangeEvent event) {
    if (denied(event.getPlayer(), event.getBlock().getLocation(), "construir")) event.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGH)
  public void interact(PlayerInteractEvent event) {
    Block block = event.getClickedBlock();
    if (block == null || event.useInteractedBlock() == Event.Result.DENY) return;
    String permission = null;
    if (event.getAction() == Action.PHYSICAL)
      permission = block.getType() == Material.FARMLAND ? "construir" : "puertas";
    else if (event.getAction() == Action.RIGHT_CLICK_BLOCK && block.getType().isInteractable()) {
      Material type = block.getType();
      if (type == Material.ENDER_CHEST
          || type == Material.CRAFTING_TABLE
          || type.name().endsWith("_TABLE")
          || type == Material.ANVIL
          || type.name().endsWith("_ANVIL")
          || type == Material.STONECUTTER
          || type == Material.GRINDSTONE
          || type == Material.LOOM
          || type == Material.ENCHANTING_TABLE) return;
      if (Tag.DOORS.isTagged(type)
          || Tag.TRAPDOORS.isTagged(type)
          || Tag.FENCE_GATES.isTagged(type)
          || Tag.BUTTONS.isTagged(type)
          || type == Material.LEVER
          || type == Material.BELL) permission = "puertas";
      else if (block.getState() instanceof InventoryHolder) permission = "cofres";
      else permission = "construir";
    }
    if (permission != null && denied(event.getPlayer(), block.getLocation(), permission)) {
      event.setUseInteractedBlock(Event.Result.DENY);
      if (event.getAction() == Action.PHYSICAL) event.setCancelled(true);
    }
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void entity(PlayerInteractEntityEvent event) {
    Entity target = event.getRightClicked();
    String permission =
        target instanceof Hanging || target instanceof ArmorStand ? "construir" : target instanceof Monster ? null : "animales";
    if (target instanceof InventoryHolder && !(target instanceof LivingEntity)) permission = "cofres";
    if (target instanceof Player) permission = null;
    if (permission != null && denied(event.getPlayer(), target.getLocation(), permission)) event.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void armorStand(PlayerArmorStandManipulateEvent event) {
    if (denied(event.getPlayer(), event.getRightClicked().getLocation(), "construir")) event.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void damage(EntityDamageByEntityEvent event) {
    Player player = player(event.getDamager());
    Entity victim = event.getEntity();
    if (player == null || victim instanceof Player || victim instanceof Monster) return;
    String permission = victim instanceof Hanging || victim instanceof ArmorStand ? "construir" : "animales";
    if (denied(player, victim.getLocation(), permission)) event.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void hanging(HangingBreakByEntityEvent event) {
    Player player = player(event.getRemover());
    if (player != null && denied(player, event.getEntity().getLocation(), "construir")) event.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void hanging(HangingPlaceEvent event) {
    if (event.getPlayer() != null && denied(event.getPlayer(), event.getEntity().getLocation(), "construir"))
      event.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void vehicle(VehicleDamageEvent event) {
    Player player = player(event.getAttacker());
    if (player != null && denied(player, event.getVehicle().getLocation(), "animales")) event.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void vehicle(VehicleEnterEvent event) {
    if (event.getEntered() instanceof Player player && denied(player, event.getVehicle().getLocation(), "animales"))
      event.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void shear(PlayerShearEntityEvent event) {
    if (denied(event.getPlayer(), event.getEntity().getLocation(), "animales")) event.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void leash(PlayerLeashEntityEvent event) {
    if (denied(event.getPlayer(), event.getEntity().getLocation(), "animales")) event.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void open(InventoryOpenEvent event) {
    if (event.getPlayer() instanceof Player player
        && event.getInventory().getHolder() instanceof Entity holder
        && !(holder instanceof Player)
        && denied(player, holder.getLocation(), "cofres")) event.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
  public void lectern(PlayerTakeLecternBookEvent event) {
    if (denied(event.getPlayer(), event.getLectern().getLocation(), "cofres")) event.setCancelled(true);
  }

  private static Player player(Entity entity) {
    if (entity instanceof Player player) return player;
    if (entity instanceof Projectile projectile) {
      ProjectileSource shooter = projectile.getShooter();
      if (shooter instanceof Player player) return player;
    }
    return null;
  }

  // ------------------------------------------------------------------ guerras

  /** WorldGuard prohíbe el PvP aquí: si los dos clanes están en guerra, se permite. */
  @EventHandler(ignoreCancelled = true)
  public void pvp(DisallowedPVPEvent event) {
    if (manager.wars().fight(event.getAttacker(), event.getDefender()) != null) event.setCancelled(true);
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void death(PlayerDeathEvent event) {
    Player victim = event.getEntity(), killer = victim.getKiller();
    if (killer != null && killer != victim) manager.wars().kill(killer, victim);
  }

  // ------------------------------------------------------------------ entrar y salir

  @EventHandler
  public void join(PlayerJoinEvent event) {
    Player player = event.getPlayer();
    Bukkit.getScheduler()
        .runTaskLater(
            plugin,
            () -> {
              if (!player.isOnline()) return;
              for (ClanRequest invite : manager.invitations(player.getUniqueId()))
                manager.getClan(invite.clan()).ifPresent(clan -> views.invitationNotice(player, clan, invite));
              manager.getPlayerClan(player.getUniqueId())
                  .flatMap(clan -> manager.wars().active(clan.id()).map(war -> Map.entry(clan, war)))
                  .ifPresent(
                      entry -> {
                        ClanWar war = entry.getValue();
                        UUID enemy = war.enemyOf(entry.getKey().id());
                        long now = System.currentTimeMillis();
                        messages.send(
                            player,
                            ClanMessages.Theme.WAR,
                            "{texto}Tu clan está en guerra con {dato}"
                                + war.nameOf(enemy)
                                + "{texto}. "
                                + (war.fighting(now)
                                    ? "{acento}El PvP está abierto."
                                    : "El PvP empieza en {dato}" + ClanWars.duration(war.startsAt() - now) + "{texto}."));
                      });
            },
            40);
  }

  @EventHandler
  public void quit(PlayerQuitEvent event) {
    warned.remove(event.getPlayer().getUniqueId());
    manager.chat().quit(event.getPlayer().getUniqueId());
  }

  // ------------------------------------------------------------------ ProtectionListener

  @Override
  public boolean anchorBroken(Player player, Protection protection) {
    Clan clan = manager.territoryClan(protection.id()).orElse(null);
    if (clan == null) return false;
    ClanTerritory territory = clan.territories().get(protection.id());
    if (!territory.home()) {
      messages.fail(player, "Esta protección es territorio de " + clan.name() + ". Para recuperarla, el clan debe devolverla con /clan territorio quitar.");
      return true;
    }
    boolean admin = player.hasPermission("viciontprotections.admin.clan.manage");
    if (!clan.leader().equals(player.getUniqueId()) && !admin) {
      messages.fail(player, "Es el bloque del clan " + clan.name() + ": solo su líder puede romperlo, y al hacerlo el clan se disuelve.");
      return true;
    }
    views.confirmDissolve(player, clan);
    return true;
  }

  @Override
  public boolean crossed(Player player, Protection previous, Protection next) {
    Clan from = previous == null ? null : manager.territoryClan(previous.id()).orElse(null);
    Clan to = next == null ? null : manager.territoryClan(next.id()).orElse(null);
    if (from == null && to == null) return false;
    if (from != null && to != null && from.id().equals(to.id())) return true;
    ClanSettings settings = manager.settings();
    if (to != null) {
      String entered = ClanChat.fill(settings.text("avisos.entrada"), to);
      if (!entered.isBlank()) messages.send(player, entered);
      messages.actionBar(player, ClanChat.fill(settings.text("avisos.entrada-actionbar"), to));
      return true;
    }
    if (next != null) return false;
    String left = ClanChat.fill(settings.text("avisos.salida"), from);
    if (!left.isBlank()) messages.send(player, left);
    return true;
  }
}
