package com.viciont.viciontprotections.models;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class Protection {
    
    private final int id;
    private String name;
    private final Location center;
    private final int size;
    private UUID primaryOwner;
    private final Set<UUID> owners;
    private final Set<UUID> members;
    
    public Protection(int id, String name, Location center, int size) {
        this.id = id;
        this.name = name;
        this.center = center;
        this.size = size;
        this.owners = new HashSet<>();
        this.members = new HashSet<>();
    }
    
    public int getId() {
        return id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public Location getCenter() {
        return center;
    }
    
    public int getSize() {
        return size;
    }
    
    public UUID getPrimaryOwner() {
        return primaryOwner;
    }
    
    public void setPrimaryOwner(UUID primaryOwner) {
        this.primaryOwner = primaryOwner;
        this.owners.add(primaryOwner);
    }
    
    public Set<UUID> getOwners() {
        return owners;
    }

    public void addOwner(UUID owner) {
        if (!owners.contains(owner)) {
            owners.add(owner);
        }
    }

    public void removeOwner(UUID owner) {
        if (!owner.equals(primaryOwner)) {
            owners.remove(owner);
        } else {
            throw new IllegalStateException("No se puede eliminar al dueño principal");
        }
    }
    
    public Set<UUID> getMembers() {
        return members;
    }
    
    public void addMember(UUID member) {
        members.add(member);
    }
    
    public void removeMember(UUID member) {
        members.remove(member);
    }
    
    public boolean isOwner(UUID player) {
        return owners.contains(player);
    }
    
    public boolean isMember(UUID player) {
        return members.contains(player);
    }

    public boolean isCenterBlock(Location location) {
        return location.getBlockX() == center.getBlockX() &&
                location.getBlockY() == center.getBlockY() &&
                location.getBlockZ() == center.getBlockZ() &&
                location.getWorld().equals(center.getWorld());
    }
    
    public boolean canAccess(UUID player) {
        return isOwner(player) || isMember(player);
    }
    
    public boolean containsLocation(Location location) {
        if (!location.getWorld().equals(center.getWorld())) {
            return false;
        }
        
        int halfSize = size / 2;
        int minX = center.getBlockX() - halfSize;
        int maxX = center.getBlockX() + halfSize;
        int minZ = center.getBlockZ() - halfSize;
        int maxZ = center.getBlockZ() + halfSize;
        
        return location.getBlockX() >= minX && location.getBlockX() <= maxX &&
               location.getBlockZ() >= minZ && location.getBlockZ() <= maxZ;
    }

    public void visualizeBoundaries() {
        int halfSize = size / 2;
        int minX = center.getBlockX() - halfSize;
        int maxX = center.getBlockX() + halfSize;
        int minZ = center.getBlockZ() - halfSize;
        int maxZ = center.getBlockZ() + halfSize;

        org.bukkit.plugin.java.JavaPlugin plugin = org.bukkit.plugin.java.JavaPlugin.getPlugin(com.viciont.viciontprotections.ViciontProtections.class);
        Particle.DustOptions dustOptions = new Particle.DustOptions(org.bukkit.Color.YELLOW, 1.5F);

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks >= 60) { // Dura 30 segundos (60 ejecuciones * 10 ticks)
                    this.cancel();
                    return;
                }
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (!player.getWorld().equals(center.getWorld())) continue;
                    // Solo mostramos partículas a los que estén cerca para evitar lag
                    if (player.getLocation().distanceSquared(center) > 15000) continue;

                    // Dibujar paredes de partículas cada 3 bloques
                    for (int x = minX; x <= maxX; x += 3) {
                        spawnParticleLine(player, x, minZ, dustOptions);
                        spawnParticleLine(player, x, maxZ, dustOptions);
                    }
                    for (int z = minZ; z <= maxZ; z += 3) {
                        spawnParticleLine(player, minX, z, dustOptions);
                        spawnParticleLine(player, maxX, z, dustOptions);
                    }
                }
                ticks++;
            }

            private void spawnParticleLine(Player p, int x, int z, Particle.DustOptions options) {
                int highestY = p.getWorld().getHighestBlockYAt(x, z);
                // Dibuja una línea vertical de 3 partículas
                p.spawnParticle(Particle.DUST, x + 0.5, highestY + 1.5, z + 0.5, 4, 0, 1, 0, options);
            }

        }.runTaskTimer(plugin, 0L, 10L);
    }
    
    public String getPrimaryOwnerName() {
        if (primaryOwner != null) {
            return Bukkit.getOfflinePlayer(primaryOwner).getName();
        }
        return "Unknown";
    }
}