package com.viciont.viciontprotections.listeners;

import com.viciont.viciontprotections.ViciontProtections;
import com.viciont.viciontprotections.managers.ProtectionManager;
import com.viciont.viciontprotections.models.Protection;
import com.viciont.viciontprotections.utils.MessageCooldown;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.*;

public class PlayerListener implements Listener {

    private final ViciontProtections plugin;
    private final ProtectionManager protectionManager;
    private final MessageCooldown messageCooldown;

    public PlayerListener(ViciontProtections plugin, ProtectionManager protectionManager) {
        this.plugin = plugin;
        this.protectionManager = protectionManager;
        this.messageCooldown = new MessageCooldown(3);
    }

    // --- LÓGICA DE CONGELAMIENTO PARA OBLIGAR A NOMBRAR ---
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (protectionManager.playersNeedingToName.contains(player.getUniqueId())) {
            String cmd = event.getMessage().toLowerCase();
            if (!cmd.startsWith("/addnamepr")) {
                event.setCancelled(true);
                player.sendMessage("§c§l¡ALTO! §7Debes ponerle nombre a tu protección antes de usar otros comandos.");
                player.sendMessage("§7Usa: §a/addnamepr <nombre>");
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerMoveForceName(PlayerMoveEvent event) {
        if (protectionManager.playersNeedingToName.contains(event.getPlayer().getUniqueId())) {
            if (event.getFrom().getBlockX() != event.getTo().getBlockX() || event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {
                event.setCancelled(true);
                if (messageCooldown.canSendMessage(event.getPlayer())) {
                    event.getPlayer().sendMessage("§c¡No puedes moverte hasta nombrar tu protección! Usa §a/addnamepr <nombre>");
                }
            }
        } else {
            // Logica normal de movimiento para avisar entrada/salida
            if (event.getFrom().getBlockX() != event.getTo().getBlockX() ||
                    event.getFrom().getBlockY() != event.getTo().getBlockY() ||
                    event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {
                protectionManager.updatePlayerProtection(event.getPlayer());
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        protectionManager.updatePlayerProtection(event.getPlayer());

        // Si el jugador entra y resulta que está en su protección sin nombre, lo volvemos a congelar
        Protection current = protectionManager.getPlayerCurrentProtection(event.getPlayer());
        if (current != null && current.getName() == null && current.isOwner(event.getPlayer().getUniqueId())) {
            protectionManager.playersNeedingToName.add(event.getPlayer().getUniqueId());
            event.getPlayer().sendMessage("§c§l¡ATENCIÓN! §7Tienes una protección sin nombre en esta ubicación.");
            event.getPlayer().sendMessage("§7Debes ponerle nombre usando §a/addnamepr <nombre>§7 para poder moverte.");
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        protectionManager.playersNeedingToName.remove(event.getPlayer().getUniqueId());
        protectionManager.updatePlayerProtection(event.getPlayer());
    }

    // --- SEGURIDAD DE INTERACCIONES Y ENTIDADES ---
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null) return;
        Player player = event.getPlayer();
        if (player.hasPermission("viciontprotections.admin.bypass")) return;

        Protection protection = protectionManager.getProtectionAt(event.getClickedBlock().getLocation());
        if (protection != null && !protection.canAccess(player.getUniqueId())) {
            if (event.getAction() == Action.RIGHT_CLICK_BLOCK || event.getAction() == Action.PHYSICAL) {
                event.setCancelled(true);
                if (event.getAction() != Action.PHYSICAL && messageCooldown.canSendMessage(player)) {
                    player.sendMessage(plugin.formatMessage(plugin.getConfig().getString("messages.no_permission_interact")));
                }
            }
        }
    }

    @EventHandler
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        Protection protection = protectionManager.getProtectionAt(event.getBlockClicked().getLocation());
        if (protection != null && !protection.canAccess(event.getPlayer().getUniqueId()) && !event.getPlayer().hasPermission("viciontprotections.admin.bypass")) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(plugin.formatMessage(plugin.getConfig().getString("messages.no_permission_interact")));
        }
    }

    @EventHandler
    public void onBucketFill(PlayerBucketFillEvent event) {
        Protection protection = protectionManager.getProtectionAt(event.getBlockClicked().getLocation());
        if (protection != null && !protection.canAccess(event.getPlayer().getUniqueId()) && !event.getPlayer().hasPermission("viciontprotections.admin.bypass")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        if (event.getRemover() instanceof Player) {
            Player player = (Player) event.getRemover();
            Protection protection = protectionManager.getProtectionAt(event.getEntity().getLocation());
            if (protection != null && !protection.canAccess(player.getUniqueId()) && !player.hasPermission("viciontprotections.admin.bypass")) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onHangingPlace(HangingPlaceEvent event) {
        Player player = event.getPlayer();
        if (player != null) {
            Protection protection = protectionManager.getProtectionAt(event.getEntity().getLocation());
            if (protection != null && !protection.canAccess(player.getUniqueId()) && !player.hasPermission("viciontprotections.admin.bypass")) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player) {
            Player player = (Player) event.getDamager();
            Protection protection = protectionManager.getProtectionAt(event.getEntity().getLocation());

            if (protection != null && !protection.canAccess(player.getUniqueId()) && !player.hasPermission("viciontprotections.admin.bypass")) {
                if (!(event.getEntity() instanceof org.bukkit.entity.Monster)) {
                    event.setCancelled(true);
                    if (messageCooldown.canSendMessage(player)) {
                        player.sendMessage(plugin.formatMessage(plugin.getConfig().getString("messages.no_permission_interact")));
                    }
                }
            }
        }
    }

    @EventHandler
    public void onEntityInteract(PlayerInteractEntityEvent event) {
        Protection protection = protectionManager.getProtectionAt(event.getRightClicked().getLocation());
        if (protection != null && !protection.canAccess(event.getPlayer().getUniqueId()) && !event.getPlayer().hasPermission("viciontprotections.admin.bypass")) {
            event.setCancelled(true);
        }
    }
}