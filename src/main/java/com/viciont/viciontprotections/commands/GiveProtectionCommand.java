package com.viciont.viciontprotections.commands;

import com.viciont.viciontprotections.ViciontProtections;
import com.viciont.viciontprotections.managers.ProtectionManager;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class GiveProtectionCommand implements CommandExecutor, TabCompleter {
    
    private final ViciontProtections plugin;
    private final ProtectionManager protectionManager;
    
    public GiveProtectionCommand(ViciontProtections plugin, ProtectionManager protectionManager) {
        this.plugin = plugin;
        this.protectionManager = protectionManager;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("viciontprotections.admin.give")) {
            sender.sendMessage(plugin.formatMessage(plugin.getConfig().getString("messages.no_permission")));
            return true;
        }
        
        if (args.length != 1) {
            sender.sendMessage(plugin.formatMessage(plugin.getConfig().getString("messages.invalid_arguments")));
            return false;
        }
        
        String type = args[0];
        Player player = null;
        
        if (sender instanceof Player) {
            player = (Player) sender;
        }
        
        if (player == null) {
            sender.sendMessage(plugin.formatMessage("§cEste comando solo puede ser ejecutado por un jugador."));
            return true;
        }
        
        // Check if type is valid
        if (!plugin.getConfig().isConfigurationSection("protection_types." + type)) {
            sender.sendMessage(plugin.formatMessage("§cTipo de protección inválido. Opciones: small, medium, large"));
            return true;
        }
        
        // Create and give protection block
        ItemStack protectionBlock = protectionManager.createProtectionBlock(type);
        player.getInventory().addItem(protectionBlock);
        
        sender.sendMessage(plugin.formatMessage("§aHas recibido un bloque de protección " + 
                                              plugin.getConfig().getString("protection_types." + type + ".name")));
        
        return true;
    }
    
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            List<String> types = protectionManager.getProtectionTypes();
            
            for (String type : types) {
                if (type.startsWith(args[0].toLowerCase())) {
                    completions.add(type);
                }
            }
            
            return completions;
        }
        
        return new ArrayList<>();
    }
}