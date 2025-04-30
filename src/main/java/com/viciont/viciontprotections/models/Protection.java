package com.viciont.viciontprotections.models;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;

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
        int y = center.getBlockY();
        
        // Create temporary visual boundaries with yellow wool
        for (int x = minX; x <= maxX; x++) {
            showTemporaryBlock(new Location(center.getWorld(), x, y, minZ));
            showTemporaryBlock(new Location(center.getWorld(), x, y, maxZ));
        }
        
        for (int z = minZ + 1; z < maxZ; z++) {
            showTemporaryBlock(new Location(center.getWorld(), minX, y, z));
            showTemporaryBlock(new Location(center.getWorld(), maxX, y, z));
        }
    }
    
    private void showTemporaryBlock(Location location) {
        // Find the highest non-air block at this XZ coordinate
        int highestY = location.getWorld().getHighestBlockYAt(location);
        Location highestLocation = new Location(location.getWorld(), location.getX(), highestY + 1, location.getZ());
        
        // Only show the block if there's air
        if (highestLocation.getBlock().getType() == Material.AIR) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.sendBlockChange(highestLocation, Material.YELLOW_WOOL.createBlockData());
            }
            
            // Schedule removal after 1 minute (1200 ticks)
            Bukkit.getScheduler().runTaskLater(Bukkit.getPluginManager().getPlugin("ViciontProtections"), () -> {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.sendBlockChange(highestLocation, highestLocation.getBlock().getBlockData());
                }
            }, 1200L);
        }
    }
    
    public String getPrimaryOwnerName() {
        if (primaryOwner != null) {
            return Bukkit.getOfflinePlayer(primaryOwner).getName();
        }
        return "Unknown";
    }
}