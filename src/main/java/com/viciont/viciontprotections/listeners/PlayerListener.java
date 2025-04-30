package com.viciont.viciontprotections.listeners;

import com.viciont.viciontprotections.ViciontProtections;
import com.viciont.viciontprotections.managers.ProtectionManager;
import com.viciont.viciontprotections.models.Protection;
import com.viciont.viciontprotections.utils.MessageCooldown;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerListener implements Listener {
    
    private final ViciontProtections plugin;
    private final ProtectionManager protectionManager;
    private final MessageCooldown messageCooldown;
    
    public PlayerListener(ViciontProtections plugin, ProtectionManager protectionManager) {
        this.plugin = plugin;
        this.protectionManager = protectionManager;
        this.messageCooldown = new MessageCooldown(3);
    }
    
    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() != event.getTo().getBlockX() ||
            event.getFrom().getBlockY() != event.getTo().getBlockY() ||
            event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {

            protectionManager.updatePlayerProtection(event.getPlayer());
        }
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        protectionManager.updatePlayerProtection(event.getPlayer());
    }
    
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        protectionManager.updatePlayerProtection(event.getPlayer());
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) {
            return;
        }

        Player player = event.getPlayer();

        if (player.hasPermission("viciontprotections.admin.bypass")) {
            return;
        }

        Protection protection = protectionManager.getProtectionAt(event.getClickedBlock().getLocation());

        if (protection != null && !protection.canAccess(player.getUniqueId())) {
            event.setCancelled(true);

            if (messageCooldown.canSendMessage(player)) {
                player.sendMessage(plugin.formatMessage(
                        plugin.getConfig().getString("messages.no_permission_interact")));
            }
        }
    }
}