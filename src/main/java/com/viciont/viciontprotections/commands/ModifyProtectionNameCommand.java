package com.viciont.viciontprotections.commands;

import com.viciont.viciontprotections.ViciontProtections;
import com.viciont.viciontprotections.managers.ProtectionManager;
import com.viciont.viciontprotections.models.Protection;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;

public class ModifyProtectionNameCommand implements CommandExecutor, TabCompleter {
    
    private final ViciontProtections plugin;
    private final ProtectionManager protectionManager;
    
    public ModifyProtectionNameCommand(ViciontProtections plugin, ProtectionManager protectionManager) {
        this.plugin = plugin;
        this.protectionManager = protectionManager;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("viciontprotections.admin.name")) {
            sender.sendMessage(plugin.formatMessage(plugin.getConfig().getString("messages.no_permission")));
            return true;
        }
        
        if (args.length < 2) {
            sender.sendMessage(plugin.formatMessage(plugin.getConfig().getString("messages.invalid_arguments")));
            return false;
        }
        
        String currentName = args[0];
        Protection protection = protectionManager.getProtectionByName(currentName);
        
        if (protection == null) {
            sender.sendMessage(plugin.formatMessage(plugin.getConfig().getString("messages.protection_not_found")));
            return true;
        }
        
        // Join remaining arguments as the new name
        String newName = String.join(" ", args).substring(args[0].length() + 1);

        protectionManager.setProtectionName(protection, newName);

        String message = plugin.getConfig().getString("messages.protection_name_changed")
                .replace("%name%", newName);
        
        sender.sendMessage(plugin.formatMessage(message));
        
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