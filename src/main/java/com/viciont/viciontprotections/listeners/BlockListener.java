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
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemStack;

public class BlockListener implements Listener {

    private final ViciontProtections plugin;
    private final ProtectionManager protectionManager;

    public BlockListener(ViciontProtections plugin, ProtectionManager protectionManager) {
        this.plugin = plugin;
        this.protectionManager = protectionManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItemInHand();
        Location location = event.getBlock().getLocation();

        if (protectionManager.isProtectionBlock(item)) {
            if (protectionManager.isLocationProtected(location)) {
                event.setCancelled(true);
                player.sendMessage(plugin.formatMessage(plugin.getConfig().getString("messages.already_protected")));
                return;
            }

            int size = protectionManager.getProtectionSize(item);

            // Creamos la protección sin nombre (null)
            Protection protection = protectionManager.createProtection(location, size, player, null);

            if (protection != null) {
                // AÑADIR A LA LISTA PARA OBLIGARLO A NOMBRAR
                protectionManager.playersNeedingToName.add(player.getUniqueId());

                // --> SOLUCIÓN: Actualizamos manualmente la protección en la que está el jugador <--
                protectionManager.updatePlayerProtection(player);

                player.sendMessage(plugin.formatMessage(plugin.getConfig().getString("messages.protection_created")));
                player.sendTitle("§c§l¡FALTA EL NOMBRE!", "§eUsa /addnamepr <nombre>", 10, 100, 20);
                player.sendMessage("§e§l========================================");
                player.sendMessage("§c§l¡ATENCIÓN! §7Acabas de crear una zona protegida.");
                player.sendMessage("§7Para poder moverte y usarla, §cDEBES§7 ponerle un nombre.");
                player.sendMessage("§7Escribe el comando: §a/addnamepr <ElNombreQueQuieras>");
                player.sendMessage("§e§l========================================");
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
                return;
            }

            if (!protection.canAccess(player.getUniqueId()) && !player.hasPermission("viciontprotections.admin.bypass")) {
                event.setCancelled(true);
                player.sendMessage(plugin.formatMessage(plugin.getConfig().getString("messages.no_permission_break")));
            }
        }
    }

    @EventHandler
    public void onPistonExtend(BlockPistonExtendEvent event) {
        for (org.bukkit.block.Block block : event.getBlocks()) {
            if (protectionManager.isLocationProtected(block.getLocation()) ||
                    protectionManager.isLocationProtected(block.getLocation().add(event.getDirection().getDirection()))) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (org.bukkit.block.Block block : event.getBlocks()) {
            if (protectionManager.isLocationProtected(block.getLocation())) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onLiquidFlow(BlockFromToEvent event) {
        Protection from = protectionManager.getProtectionAt(event.getBlock().getLocation());
        Protection to = protectionManager.getProtectionAt(event.getToBlock().getLocation());

        if (from == null && to != null) {
            event.setCancelled(true);
        } else if (from != null && to != null && from.getId() != to.getId()) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> protectionManager.isLocationProtected(block.getLocation()));
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> protectionManager.isLocationProtected(block.getLocation()));
    }
}