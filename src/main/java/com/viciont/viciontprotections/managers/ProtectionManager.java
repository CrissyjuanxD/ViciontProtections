package com.viciont.viciontprotections.managers;

import com.viciont.viciontprotections.ViciontProtections;
import com.viciont.viciontprotections.database.DatabaseManager;
import com.viciont.viciontprotections.models.Protection;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class ProtectionManager {
    
    private final ViciontProtections plugin;
    private final DatabaseManager databaseManager;
    private final Map<UUID, Protection> playerCurrentProtection;
    
    public ProtectionManager(ViciontProtections plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.playerCurrentProtection = new HashMap<>();

        loadProtections();
    }
    
    private void loadProtections() {
        // This is handled by the DatabaseManager when needed
        plugin.getLogger().info("Loading protections from database...");
    }
    
    public ItemStack createProtectionBlock(String type) {
        String protectionName = plugin.getConfig().getString("protection_types." + type + ".name");
        int size = plugin.getConfig().getInt("protection_types." + type + ".size");
        
        ItemStack item = new ItemStack(Material.REDSTONE_BLOCK);
        ItemMeta meta = item.getItemMeta();

        meta.setDisplayName(protectionName);
        List<String> lore = new ArrayList<>();
        lore.add("§7Tamaño: §f" + size + "x" + size);
        lore.add("§8Coloca este bloque para crear una protección.");
        
        meta.setLore(lore);
        item.setItemMeta(meta);
        
        return item;
    }
    
    public String getProtectionType(int size) {
        for (String type : plugin.getConfig().getConfigurationSection("protection_types").getKeys(false)) {
            int protectionSize = plugin.getConfig().getInt("protection_types." + type + ".size");
            if (protectionSize == size) {
                return type;
            }
        }
        return null;
    }
    
    public int getProtectionSize(ItemStack item) {
        if (item.getType() != Material.REDSTONE_BLOCK || !item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) {
            return 0;
        }
        
        String displayName = item.getItemMeta().getDisplayName();
        
        for (String type : plugin.getConfig().getConfigurationSection("protection_types").getKeys(false)) {
            String protectionName = plugin.getConfig().getString("protection_types." + type + ".name");
            int size = plugin.getConfig().getInt("protection_types." + type + ".size");
            
            if (displayName.equals(protectionName)) {
                return size;
            }
        }
        
        return 0;
    }
    
    public boolean isProtectionBlock(ItemStack item) {
        return getProtectionSize(item) > 0;
    }
    
    public Protection createProtection(Location location, int size, Player player) {

        Location center = new Location(location.getWorld(),
                location.getBlockX(),
                location.getBlockY(),
                location.getBlockZ());

        // Create protection in database
        int protectionId = databaseManager.createProtection(center, size);
        
        if (protectionId != -1) {
            Protection protection = new Protection(protectionId, null, center, size);
            
            // Set primary owner
            protection.setPrimaryOwner(player.getUniqueId());
            databaseManager.addProtectionOwner(protectionId, player.getUniqueId(), true);
            
            // Visualize boundaries
            protection.visualizeBoundaries();
            
            return protection;
        }
        
        return null;
    }
    
    public boolean isLocationProtected(Location location) {
        return databaseManager.getProtectionByLocation(location) != null;
    }
    
    public Protection getProtectionAt(Location location) {
        return databaseManager.getProtectionByLocation(location);
    }
    
    public Protection getProtectionById(int id) {
        return databaseManager.getProtectionById(id);
    }
    
    public Protection getProtectionByName(String name) {
        return databaseManager.getProtectionByName(name);
    }
    
    public void setProtectionName(Protection protection, String name) {
        protection.setName(name);
        databaseManager.setProtectionName(protection.getId(), name);
    }

    public void addMember(Protection protection, UUID playerUuid) {
        if (protection.isMember(playerUuid)) {
            return;
        }

        databaseManager.addProtectionMember(protection.getId(), playerUuid);

        protection.addMember(playerUuid);

        refreshProtectionCache(protection);

    }

    public void removeMember(Protection protection, UUID playerUuid) {
        if (!protection.isMember(playerUuid)) {
            return;
        }

        databaseManager.removeProtectionMember(protection.getId(), playerUuid);

        protection.removeMember(playerUuid);

        refreshProtectionCache(protection);
    }

    public void addOwner(Protection protection, UUID playerUuid) {
        if (protection.isOwner(playerUuid)) {
            return;
        }

        protection.addOwner(playerUuid);

        // Añade a la base de datos (isPrimary = false para owners secundarios)
        databaseManager.addProtectionOwner(protection.getId(), playerUuid, false);

        if (!protection.isMember(playerUuid)) {
            addMember(protection, playerUuid);
        }

        refreshProtectionCache(protection);
    }

    public void removeOwner(Protection protection, UUID playerUuid) {
        try {
            if (!playerUuid.equals(protection.getPrimaryOwner())) {
                databaseManager.removeProtectionOwner(protection.getId(), playerUuid);
            }

            protection.removeOwner(playerUuid);

            refreshProtectionCache(protection);

            if (protection.isMember(playerUuid)) {
                removeMember(protection, playerUuid);
            }
        } catch (IllegalStateException e) {
            plugin.getLogger().warning(e.getMessage());
            throw e;
        }
    }
    
    public void deleteProtection(Protection protection) {
        databaseManager.deleteProtection(protection.getId());
    }
    
    public void updatePlayerProtection(Player player) {
        Protection currentProtection = getProtectionAt(player.getLocation());
        Protection previousProtection = playerCurrentProtection.get(player.getUniqueId());
        
        if (currentProtection != null && (previousProtection == null || previousProtection.getId() != currentProtection.getId())) {
            // Player entered a new protection area
            playerCurrentProtection.put(player.getUniqueId(), currentProtection);
            
            if (currentProtection.getName() != null) {
                String message = plugin.getConfig().getString("messages.enter_protection")
                        .replace("%protection_name%", currentProtection.getName())
                        .replace("%owner%", currentProtection.getPrimaryOwnerName());
                
                player.sendMessage(plugin.formatMessage(message));
            }
        } else if (currentProtection == null && previousProtection != null) {
            // Player left a protection area
            playerCurrentProtection.remove(player.getUniqueId());
            
            if (previousProtection.getName() != null) {
                String message = plugin.getConfig().getString("messages.exit_protection")
                        .replace("%protection_name%", previousProtection.getName());
                
                player.sendMessage(plugin.formatMessage(message));
            }
        }
    }

    public void refreshProtectionCache(Protection protection) {
        // Actualizar la protección en el mapa de caché
        Protection updatedProtection = databaseManager.getProtectionById(protection.getId());
        if (updatedProtection != null) {
            Bukkit.getOnlinePlayers().forEach(player -> {
                Protection current = playerCurrentProtection.get(player.getUniqueId());
                if (current != null && current.getId() == protection.getId()) {
                    playerCurrentProtection.put(player.getUniqueId(), updatedProtection);
                }
            });
        }
    }

    public Protection getProtectionByNameWithRefresh(String name) {
        Protection protection = databaseManager.getProtectionByName(name);
        if (protection != null) {
            refreshProtectionCache(protection);
        }
        return protection;
    }
    
    public Protection getPlayerCurrentProtection(Player player) {
        return playerCurrentProtection.get(player.getUniqueId());
    }
    
    public List<Protection> getAllProtections() {
        return databaseManager.getAllProtections();
    }
    
    public List<String> getProtectionTypes() {
        List<String> types = new ArrayList<>();
        for (String type : plugin.getConfig().getConfigurationSection("protection_types").getKeys(false)) {
            types.add(type);
        }
        return types;
    }
}