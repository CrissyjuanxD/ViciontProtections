package com.viciont.viciontprotections.commands;

import com.viciont.viciontprotections.ViciontProtections;
import com.viciont.viciontprotections.managers.ProtectionManager;
import com.viciont.viciontprotections.models.Protection;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.List;

public class ProtectionListCommand implements CommandExecutor {
    
    private final ViciontProtections plugin;
    private final ProtectionManager protectionManager;
    
    public ProtectionListCommand(ViciontProtections plugin, ProtectionManager protectionManager) {
        this.plugin = plugin;
        this.protectionManager = protectionManager;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        List<Protection> protections = protectionManager.getAllProtections();
        
        if (protections.isEmpty()) {
            sender.sendMessage(plugin.formatMessage(plugin.getConfig().getString("messages.no_protections")));
            return true;
        }
        
        sender.sendMessage(plugin.formatMessage(plugin.getConfig().getString("messages.protection_list")));
        
        for (Protection protection : protections) {
            if (protection.getName() != null) {
                String message = plugin.getConfig().getString("messages.protection_list_entry")
                        .replace("%name%", protection.getName())
                        .replace("%owner%", protection.getPrimaryOwnerName());
                
                sender.sendMessage(plugin.formatMessage(message));
            }
        }
        
        return true;
    }
}