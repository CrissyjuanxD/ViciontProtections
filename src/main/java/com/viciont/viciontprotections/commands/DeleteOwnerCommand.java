package com.viciont.viciontprotections.commands;

import com.viciont.viciontprotections.ViciontProtections;
import com.viciont.viciontprotections.managers.ProtectionManager;
import com.viciont.viciontprotections.models.Protection;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class DeleteOwnerCommand implements CommandExecutor, TabCompleter {
    
    private final ViciontProtections plugin;
    private final ProtectionManager protectionManager;
    
    public DeleteOwnerCommand(ViciontProtections plugin, ProtectionManager protectionManager) {
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
        
        if (!player.hasPermission("viciontprotections.user.owner")) {
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
        
        if (!protection.getPrimaryOwner().equals(player.getUniqueId())) {
            player.sendMessage(plugin.formatMessage(plugin.getConfig().getString("messages.only_ownerprimary")));
            return true;
        }
        
        Player targetPlayer = Bukkit.getPlayer(args[0]);
        UUID targetUuid;
        
        if (targetPlayer != null) {
            targetUuid = targetPlayer.getUniqueId();
        } else {
            // Try to find offline player
            targetUuid = Bukkit.getOfflinePlayer(args[0]).getUniqueId();
        }
        
        if (targetUuid.equals(protection.getPrimaryOwner())) {
            player.sendMessage(plugin.formatMessage(plugin.getConfig().getString("messages.no_delete_ownerprimary")));
            return true;
        }
        
        if (!protection.isOwner(targetUuid)) {
            player.sendMessage(plugin.formatMessage(plugin.getConfig().getString("messages.not_owner1")));
            return true;
        }
        
        // Remove owner
        protectionManager.removeOwner(protection, targetUuid);

        String message = plugin.getConfig().getString("messages.owner_removed")
                .replace("%player%", targetPlayer.getName());
        player.sendMessage(plugin.formatMessage(message));
        
        return true;
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender instanceof Player) {
            List<String> completions = new ArrayList<>();
            Protection protection = protectionManager.getPlayerCurrentProtection((Player) sender);
            
            if (protection != null && protection.getPrimaryOwner().equals(((Player) sender).getUniqueId())) {
                for (UUID ownerUuid : protection.getOwners()) {
                    if (!ownerUuid.equals(protection.getPrimaryOwner())) {
                        String ownerName = Bukkit.getOfflinePlayer(ownerUuid).getName();
                        if (ownerName != null && ownerName.toLowerCase().startsWith(args[0].toLowerCase())) {
                            completions.add(ownerName);
                        }
                    }
                }
            }
            
            return completions;
        }
        
        return new ArrayList<>();
    }
}