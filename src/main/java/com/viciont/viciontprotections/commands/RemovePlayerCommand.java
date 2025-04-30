package com.viciont.viciontprotections.commands;

import com.viciont.viciontprotections.ViciontProtections;
import com.viciont.viciontprotections.managers.ProtectionManager;
import com.viciont.viciontprotections.models.Protection;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class RemovePlayerCommand implements CommandExecutor {
    
    private final ViciontProtections plugin;
    private final ProtectionManager protectionManager;
    
    public RemovePlayerCommand(ViciontProtections plugin, ProtectionManager protectionManager) {
        this.plugin = plugin;
        this.protectionManager = protectionManager;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.formatMessage("§cEste comando solo puede ser ejecutado por un jugador."));
            return true;
        }
        
        Player player = (Player) sender;
        
        if (!player.hasPermission("viciontprotections.user.del")) {
            player.sendMessage(plugin.formatMessage(plugin.getConfig().getString("messages.no_permission")));
            return true;
        }
        
        if (args.length != 1) {
            player.sendMessage(plugin.formatMessage(plugin.getConfig().getString("messages.invalid_arguments")));
            return false;
        }
        
        Protection protection = protectionManager.getPlayerCurrentProtection(player);
        
        if (protection == null) {
            player.sendMessage(plugin.formatMessage(plugin.getConfig().getString("messages.not_in_protection")));
            return true;
        }

        if (!protection.isOwner(player.getUniqueId()) && !player.hasPermission("viciontprotections.admin.bypass")) {
            player.sendMessage(plugin.formatMessage(plugin.getConfig().getString("messages.not_owner")));
            return true;
        }
        
        if (protection.getName() == null) {
            player.sendMessage(plugin.formatMessage(plugin.getConfig().getString("messages.protection_name_required")));
            return true;
        }

        // Obtener el jugador objetivo (online u offline)
        Player targetPlayer = Bukkit.getPlayer(args[0]);
        UUID targetUuid;

        if (targetPlayer != null) {
            targetUuid = targetPlayer.getUniqueId();
        } else {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(args[0]);
            if (!offlinePlayer.hasPlayedBefore()) {
                player.sendMessage(plugin.formatMessage(plugin.getConfig().getString("messages.player_not_found")));
                return true;
            }
            targetUuid = offlinePlayer.getUniqueId();
        }

        // Verificación CORREGIDA - ahora comprueba el jugador objetivo
        if (!protection.getMembers().contains(targetUuid)) {
            player.sendMessage(plugin.formatMessage(plugin.getConfig().getString("messages.player_not_member")));
            return true;
        }

        protectionManager.removeMember(protection, targetUuid);

        String playerName = targetPlayer != null ?
                targetPlayer.getName() :
                Bukkit.getOfflinePlayer(targetUuid).getName();

        String message = plugin.getConfig().getString("messages.player_removed")
                .replace("%player%", playerName != null ? playerName : "Jugador Desconocido");

        player.sendMessage(plugin.formatMessage(message));

        return true;
    }
}