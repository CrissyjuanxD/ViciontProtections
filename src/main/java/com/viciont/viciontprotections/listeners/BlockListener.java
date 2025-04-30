package com.viciont.viciontprotections.listeners;

import com.viciont.viciontprotections.ViciontProtections;
import com.viciont.viciontprotections.managers.ProtectionManager;
import com.viciont.viciontprotections.models.Protection;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.inventory.ItemStack;

public class BlockListener implements Listener {

    private final ViciontProtections plugin;
    private final ProtectionManager protectionManager;

    public BlockListener(ViciontProtections plugin, ProtectionManager protectionManager) {
        this.plugin = plugin;
        this.protectionManager = protectionManager;
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItemInHand();
        Location location = event.getBlock().getLocation();

        if (protectionManager.isProtectionBlock(item)) {
            if (protectionManager.isLocationProtected(location)) {
                event.setCancelled(true);
                player.sendMessage((plugin.formatMessage(plugin.getConfig().getString("messages.already_protected"))));
                return;
            }

            int size = protectionManager.getProtectionSize(item);

            Protection protection = protectionManager.createProtection(location, size, player);

            if (protection != null) {
                player.sendMessage(plugin.formatMessage(plugin.getConfig().getString("messages.protection_created")));
            } else {
                event.setCancelled(true);
                player.sendMessage(plugin.formatMessage(plugin.getConfig().getString("messages.error_creating_protection")));
            }
        } else {
            Protection protection = protectionManager.getProtectionAt(location);

            if (protection != null && !protection.canAccess(player.getUniqueId()) && !player.hasPermission("viciontprotections.admin.bypass")) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Location location = event.getBlock().getLocation();
        Protection protection = protectionManager.getProtectionAt(location);

        if (protection != null) {
            if (protection.isCenterBlock(location)) {
                handleProtectionBlockBreak(event, player, protection);
                return;
            }

            // Para otros bloques en el área protegida
            if (!protection.canAccess(player.getUniqueId()) && !player.hasPermission("viciontprotections.admin.bypass")) {
                event.setCancelled(true);
                player.sendMessage(plugin.formatMessage(plugin.getConfig().getString("messages.no_permission_break")));
            }
        }
    }

    private void handleProtectionBlockBreak(BlockBreakEvent event, Player player, Protection protection) {
        // Solo el dueño principal o admins pueden romperlo
        if (protection.getPrimaryOwner().equals(player.getUniqueId()) ||
                player.hasPermission("viciontprotections.admin.bypass")) {

            protectionManager.deleteProtection(protection);

            String type = protectionManager.getProtectionType(protection.getSize());
            if (type != null) {
                ItemStack protectionBlock = protectionManager.createProtectionBlock(type);
                player.getInventory().addItem(protectionBlock);
                event.setDropItems(false);
            }

            player.sendMessage(plugin.formatMessage(plugin.getConfig().getString("messages.protection_deleted")));
        } else {
            event.setCancelled(true);
            player.sendMessage(plugin.formatMessage(plugin.getConfig().getString("messages.no_permission_break_protection")));
        }
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> {
            Protection protection = protectionManager.getProtectionAt(block.getLocation());
            if (protection != null) {
                return block.getLocation().equals(protection.getCenter());
            }
            return false;
        });
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> {
            Protection protection = protectionManager.getProtectionAt(block.getLocation());
            if (protection != null) {
                return block.getLocation().equals(protection.getCenter());
            }
            return false;
        });
    }
}