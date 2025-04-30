package com.viciont.viciontprotections.commands;

import com.viciont.viciontprotections.ViciontProtections;
import com.viciont.viciontprotections.managers.ProtectionManager;
import com.viciont.viciontprotections.models.Protection;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;

public class DeleteProtectionCommand implements CommandExecutor, TabCompleter {
    
    private final ViciontProtections plugin;
    private final ProtectionManager protectionManager;
    
    public DeleteProtectionCommand(ViciontProtections plugin, ProtectionManager protectionManager) {
        this.plugin = plugin;
        this.protectionManager = protectionManager;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("viciontprotections.admin.remove")) {
            sender.sendMessage(plugin.formatMessage(plugin.getConfig().getString("messages.no_permission")));
            return true;
        }
        
        if (args.length != 1) {
            sender.sendMessage(plugin.formatMessage(plugin.getConfig().getString("messages.invalid_arguments")));
            return false;
        }
        
        String protectionName = args[0];
        Protection protection = protectionManager.getProtectionByName(protectionName);
        
        if (protection == null) {
            sender.sendMessage(plugin.formatMessage(plugin.getConfig().getString("messages.protection_not_found")));
            return true;
        }
        
        // Get protection block location
        Location blockLocation = protection.getCenter();

        protectionManager.deleteProtection(protection);
        
        // Remove the protection block
        if (blockLocation.getBlock().getType() == Material.REDSTONE_BLOCK) {
            blockLocation.getBlock().setType(Material.AIR);
        }

        sender.sendMessage(plugin.formatMessage(plugin.getConfig().getString("messages.protection_deleted")));
        
        return true;
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            List<Protection> protections = protectionManager.getAllProtections();
            
            for (Protection protection : protections) {
                if (protection.getName() != null && protection.getName().toLowerCase().startsWith(args[0].toLowerCase())) {
                    completions.add(protection.getName());
                }
            }
            
            return completions;
        }
        
        return new ArrayList<>();
    }
}