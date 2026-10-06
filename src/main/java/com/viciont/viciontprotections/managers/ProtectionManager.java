package com.viciont.viciontprotections.managers;

import com.viciont.viciontprotections.ViciontProtections;
import com.viciont.viciontprotections.database.DatabaseManager;
import com.viciont.viciontprotections.models.Protection;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ProtectionManager {

    private final ViciontProtections plugin;
    private final DatabaseManager databaseManager;
    private final Map<UUID, Protection> playerCurrentProtection;

    // Lista de jugadores obligados a nombrar la protección
    public final Set<UUID> playersNeedingToName = ConcurrentHashMap.newKeySet();

    public ProtectionManager(ViciontProtections plugin, DatabaseManager databaseManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.playerCurrentProtection = new HashMap<>();

        loadProtections();
    }

    private void loadProtections() {
        plugin.getLogger().info("Loading protections from database...");
    }

    public ItemStack createProtectionBlock(String type) {
        String protectionName = plugin.getConfig().getString("protection_types." + type + ".name");
        int size = plugin.getConfig().getInt("protection_types." + type + ".size");

        ItemStack item = new ItemStack(Material.REDSTONE_BLOCK);
        ItemMeta meta = item.getItemMeta();

        switch(type.toLowerCase()) {
            case "small": meta.setCustomModelData(1001); break;
            case "medium": meta.setCustomModelData(1002); break;
            case "large": meta.setCustomModelData(1003); break;
        }

        meta.setDisplayName(ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + protectionName);
        List<String> lore = new ArrayList<>();
        lore.add(" ");
        lore.add("§7§l>> §3Tamaño: §f" + size + "x" + size);
        lore.add(" ");
        lore.add("§6Coloca este bloque para crear una protección.");
        lore.add(" ");

        meta.setLore(lore);
        item.setItemMeta(meta);

        return item;
    }

    public String getProtectionType(int size) {
        for (String type : plugin.getConfig().getConfigurationSection("protection_types").getKeys(false)) {
            int protectionSize = plugin.getConfig().getInt("protection_types." + type + ".size");
            if (protectionSize == size) return type;
        }
        return null;
    }

    public int getProtectionSize(ItemStack item) {
        if (item == null || item.getType() != Material.REDSTONE_BLOCK || !item.hasItemMeta()) return 0;
        ItemMeta meta = item.getItemMeta();
        if (!meta.hasCustomModelData()) return 0;

        int cmd = meta.getCustomModelData();
        if (cmd != 1001 && cmd != 1002 && cmd != 1003) return 0;

        String displayName = ChatColor.stripColor(meta.getDisplayName());
        for (String type : plugin.getConfig().getConfigurationSection("protection_types").getKeys(false)) {
            String protectionName = plugin.getConfig().getString("protection_types." + type + ".name");
            int size = plugin.getConfig().getInt("protection_types." + type + ".size");
            if (displayName.equalsIgnoreCase(ChatColor.stripColor(protectionName))) return size;
        }
        return 0;
    }

    public boolean isProtectionBlock(ItemStack item) {
        if (item == null || item.getType() != Material.REDSTONE_BLOCK || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (!meta.hasCustomModelData()) return false;
        int cmd = meta.getCustomModelData();
        return cmd == 1001 || cmd == 1002 || cmd == 1003;
    }

    public Protection createProtection(Location location, int size, Player player, String name) {
        Location center = new Location(location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
        int protectionId = databaseManager.createProtection(center, size, name);

        if (protectionId != -1) {
            Protection protection = new Protection(protectionId, name, center, size);
            protection.setPrimaryOwner(player.getUniqueId());
            databaseManager.addProtectionOwner(protectionId, player.getUniqueId(), true);
            protection.visualizeBoundaries();
            return protection;
        }
        return null;
    }

    public boolean isLocationProtected(Location location) { return databaseManager.getProtectionByLocation(location) != null; }
    public Protection getProtectionAt(Location location) { return databaseManager.getProtectionByLocation(location); }
    public Protection getProtectionById(int id) { return databaseManager.getProtectionById(id); }
    public Protection getProtectionByName(String name) { return databaseManager.getProtectionByName(name); }

    public void setProtectionName(Protection protection, String name) {
        protection.setName(name);
        databaseManager.setProtectionName(protection.getId(), name);
    }

    public void addMember(Protection protection, UUID playerUuid) {
        if (protection.isMember(playerUuid)) return;
        databaseManager.addProtectionMember(protection.getId(), playerUuid);
        protection.addMember(playerUuid);
        refreshProtectionCache(protection);
    }

    public void removeMember(Protection protection, UUID playerUuid) {
        if (!protection.isMember(playerUuid)) return;
        databaseManager.removeProtectionMember(protection.getId(), playerUuid);
        protection.removeMember(playerUuid);
        refreshProtectionCache(protection);
    }

    public void addOwner(Protection protection, UUID playerUuid) {
        if (protection.isOwner(playerUuid)) return;
        protection.addOwner(playerUuid);
        databaseManager.addProtectionOwner(protection.getId(), playerUuid, false);
        if (!protection.isMember(playerUuid)) addMember(protection, playerUuid);
        refreshProtectionCache(protection);
    }

    public void removeOwner(Protection protection, UUID playerUuid) {
        if (!playerUuid.equals(protection.getPrimaryOwner())) {
            databaseManager.removeProtectionOwner(protection.getId(), playerUuid);
        }
        protection.removeOwner(playerUuid);
        refreshProtectionCache(protection);
        if (protection.isMember(playerUuid)) removeMember(protection, playerUuid);
    }

    public void deleteProtection(Protection protection) { databaseManager.deleteProtection(protection.getId()); }

    public void updatePlayerProtection(Player player) {
        Protection currentProtection = getProtectionAt(player.getLocation());
        Protection previousProtection = playerCurrentProtection.get(player.getUniqueId());

        if (currentProtection != null && (previousProtection == null || previousProtection.getId() != currentProtection.getId())) {
            playerCurrentProtection.put(player.getUniqueId(), currentProtection);
            if (currentProtection.getName() != null) {
                String message = plugin.getConfig().getString("messages.enter_protection")
                        .replace("%protection_name%", currentProtection.getName())
                        .replace("%owner%", currentProtection.getPrimaryOwnerName());
                player.sendMessage(plugin.formatMessage(message));
            }
        } else if (currentProtection == null && previousProtection != null) {
            playerCurrentProtection.remove(player.getUniqueId());
            if (previousProtection.getName() != null) {
                String message = plugin.getConfig().getString("messages.exit_protection")
                        .replace("%protection_name%", previousProtection.getName());
                player.sendMessage(plugin.formatMessage(message));
            }
        }
    }

    public void refreshProtectionCache(Protection protection) {
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
        if (protection != null) refreshProtectionCache(protection);
        return protection;
    }

    public Protection getPlayerCurrentProtection(Player player) { return playerCurrentProtection.get(player.getUniqueId()); }
    public List<Protection> getAllProtections() { return databaseManager.getAllProtections(); }

    public List<String> getProtectionTypes() {
        List<String> types = new ArrayList<>();
        for (String type : plugin.getConfig().getConfigurationSection("protection_types").getKeys(false)) types.add(type);
        return types;
    }
}