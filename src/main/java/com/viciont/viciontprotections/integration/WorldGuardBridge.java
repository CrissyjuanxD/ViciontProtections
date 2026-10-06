package com.viciont.viciontprotections.integration;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.*;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.*;
import com.viciont.viciontprotections.api.*;
import com.viciont.viciontprotections.models.Protection;
import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Player;

/**
 * Integración mediante la API pública de WG7/WE7; WorldGuard aplica la seguridad de las regiones.
 */
public final class WorldGuardBridge {
  private static StringFlag managed;
  private String denyMessage = "&#B07CFF۞ &#FF8FB8No tienes permiso para hacer eso en esta protección.";

  public static void registerFlag() {
    var registry = WorldGuard.getInstance().getFlagRegistry();
    try {
      managed = new StringFlag("viciont-protection");
      registry.register(managed);
    } catch (FlagConflictException conflict) {
      if (registry.get("viciont-protection") instanceof StringFlag existing) managed = existing;
      else
        throw new IllegalStateException(
            "La marca viciont-protection está ocupada por un tipo incompatible.", conflict);
    }
  }

  /** Mensaje que WorldGuard muestra al denegar una acción; admite los colores de messages.yml. */
  public void setDenyMessage(String message) {
    if (message != null && !message.isBlank()) denyMessage = message;
  }

  public RegionManager manager(World world) {
    if (world == null) throw new ProtectionException("El mundo de esa protección no está cargado.");
    RegionManager manager = regions(world);
    if (manager == null)
      throw new ProtectionException("WorldGuard no tiene habilitadas las regiones en este mundo.");
    return manager;
  }

  /** null si WorldGuard tiene las regiones desactivadas en ese mundo. */
  private static RegionManager regions(World world) {
    if (managed == null)
      throw new IllegalStateException(
          "La bandera viciont-protection no está registrada: reinicia el servidor en lugar de"
              + " recargar el plugin.");
    return WorldGuard.getInstance().getPlatform().getRegionContainer().get(BukkitAdapter.adapt(world));
  }

  private ProtectedCuboidRegion region(String id, Bounds b) {
    return new ProtectedCuboidRegion(
        id,
        BlockVector3.at(b.minX(), b.minY(), b.minZ()),
        BlockVector3.at(b.maxX(), b.maxY(), b.maxZ()));
  }

  public void validate(CreateProtectionRequest request, Player actor) {
    World world = Bukkit.getWorld(request.worldName());
    RegionManager manager = manager(world);
    Bounds bounds = request.bounds();
    if (bounds.minY() < world.getMinHeight() || bounds.maxY() >= world.getMaxHeight())
      throw new ProtectionException("La altura de la protección supera los límites del mundo.");
    if (!world
            .getWorldBorder()
            .isInside(new Location(world, bounds.minX() + 0.5, 0, bounds.minZ() + 0.5))
        || !world
            .getWorldBorder()
            .isInside(new Location(world, bounds.maxX() + 0.5, 0, bounds.maxZ() + 0.5)))
      throw new ProtectionException("La protección supera el borde del mundo.");
    var candidate = region("vp_pending", bounds);
    for (ProtectedRegion other : manager.getApplicableRegions(candidate)) {
      if (!other.getId().equals("__global__"))
        throw new ProtectionException(
            "La zona se cruza con otra región protegida: " + other.getId() + ".");
    }
    if (actor != null) {
      var local = WorldGuardPlugin.inst().wrapPlayer(actor);
      if (!WorldGuard.getInstance()
          .getPlatform()
          .getSessionManager()
          .hasBypass(local, BukkitAdapter.adapt(world))) {
        var query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
        for (int x : new int[] {bounds.minX(), bounds.maxX()})
          for (int z : new int[] {bounds.minZ(), bounds.maxZ()}) {
            Location corner =
                new Location(world, x, Math.max(world.getMinHeight(), bounds.minY()), z);
            if (!query.testBuild(BukkitAdapter.adapt(corner), local))
              throw new ProtectionException(
                  "WorldGuard no permite crear una protección en esta zona.");
          }
      }
    }
  }

  public void apply(Protection protection) {
    World world = Bukkit.getWorld(protection.worldName());
    if (world == null) return; // Se materializa al cargar el mundo; los datos siguen en la base.
    RegionManager manager = manager(world);
    ProtectedRegion existing = manager.getRegion(protection.regionId());
    if (existing != null && existing.getFlag(managed) == null)
      throw new ProtectionException("El identificador de región ya está ocupado.");
    var region = region(protection.regionId(), protection.bounds());
    region.setFlag(managed, protection.id().toString());
    region.getOwners().addPlayer(protection.primaryOwner());
    protection.owners().forEach(region.getOwners()::addPlayer);
    protection.members().forEach(region.getMembers()::addPlayer);
    for (StateFlag flag : List.of(Flags.CHEST_ACCESS, Flags.USE, Flags.INTERACT)) {
      region.setFlag(flag, StateFlag.State.DENY);
      region.setFlag(flag.getRegionGroupFlag(), RegionGroup.NON_MEMBERS);
    }
    for (StateFlag flag :
        List.of(
            Flags.TNT,
            Flags.CREEPER_EXPLOSION,
            Flags.OTHER_EXPLOSION,
            Flags.GHAST_FIREBALL,
            Flags.FIRE_SPREAD,
            Flags.LAVA_FIRE,
            Flags.ENDER_BUILD)) region.setFlag(flag, StateFlag.State.DENY);
    region.setFlag(Flags.DENY_MESSAGE, com.viciont.viciontprotections.ui.Messages.color(denyMessage));
    for (var entry : protection.flags().entrySet())
      region.setFlag(stateFlag(entry.getKey()), StateFlag.State.valueOf(entry.getValue()));
    manager.addRegion(region);
  }

  public void remove(Protection protection) {
    World world = Bukkit.getWorld(protection.worldName());
    if (world == null) return;
    RegionManager manager = regions(world);
    if (manager == null) return;
    ProtectedRegion region = manager.getRegion(protection.regionId());
    if (region != null && protection.id().toString().equals(region.getFlag(managed)))
      manager.removeRegion(protection.regionId());
  }

  public Set<UUID> at(Location location) {
    if (location.getWorld() == null) return Set.of();
    RegionManager manager = regions(location.getWorld());
    if (manager == null) return Set.of();
    Set<UUID> result = new HashSet<>();
    for (ProtectedRegion region :
        manager
            .getApplicableRegions(
                BlockVector3.at(
                    location.getBlockX(), location.getBlockY(), location.getBlockZ()))) {
      String id = region.getFlag(managed);
      if (id != null)
        try {
          result.add(UUID.fromString(id));
        } catch (IllegalArgumentException ignored) {
        }
    }
    return result;
  }

  public void reconcile(World world, Collection<Protection> protections) {
    RegionManager manager = regions(world);
    if (manager == null) {
      long stored = protections.stream().filter(p -> p.worldName().equals(world.getName())).count();
      if (stored > 0)
        Bukkit.getLogger()
            .warning(
                "[ViciontProtections] WorldGuard tiene las regiones desactivadas en "
                    + world.getName()
                    + "; sus "
                    + stored
                    + " protecciones no estarán activas hasta habilitarlas.");
      return;
    }
    Set<String> valid = new HashSet<>();
    for (Protection protection : protections)
      if (protection.worldName().equals(world.getName())) {
        apply(protection);
        valid.add(protection.regionId());
      }
    for (ProtectedRegion region : new ArrayList<>(manager.getRegions().values()))
      if (region.getFlag(managed) != null
          && region.getId().startsWith("vp_")
          && !valid.contains(region.getId())) manager.removeRegion(region.getId());
  }

  public StateFlag stateFlag(String name) {
    Flag<?> flag = WorldGuard.getInstance().getFlagRegistry().get(name.toLowerCase(Locale.ROOT));
    if (!(flag instanceof StateFlag state))
      throw new ProtectionException(
          "Esa opción no es una bandera de permitir/denegar de WorldGuard.");
    if (state == Flags.BUILD || state == Flags.PASSTHROUGH)
      throw new ProtectionException(
          "Construcción y paso de protección se gestionan con los propietarios y miembros.");
    return state;
  }

  public List<String> editableFlags() {
    List<String> result = new ArrayList<>();
    for (Flag<?> flag : WorldGuard.getInstance().getFlagRegistry())
      if (flag instanceof StateFlag && flag != Flags.BUILD && flag != Flags.PASSTHROUGH)
        result.add(flag.getName());
    result.sort(String::compareTo);
    return result;
  }

}
