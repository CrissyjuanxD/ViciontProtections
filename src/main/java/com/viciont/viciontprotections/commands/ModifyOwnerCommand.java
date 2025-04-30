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
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class ModifyOwnerCommand implements CommandExecutor, TabCompleter {
    
    private final ViciontProtections plugin;
    private final ProtectionManager protectionManager;
    
    public ModifyOwnerCommand(ViciontProtections plugin, ProtectionManager protectionManager) {
        this.plugin = plugin;
        this.protectionManager = protectionManager;
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("viciontprotections.admin.owner")) {
            sender.sendMessage(plugin.formatMessage(plugin.getConfig().getString("messages.no_permission")));
            return true;
        }

        if (args.length < 3) {
            sender.sendMessage(plugin.formatMessage(plugin.getConfig().getString("messages.invalid_arguments")));
            return false;
        }

        String action = args[args.length - 2];
        String playerName = args[args.length - 1];
        String protectionName = String.join(" ", Arrays.copyOfRange(args, 0, args.length - 2));
        
        Protection protection = protectionManager.getProtectionByName(protectionName);
        
        if (protection == null) {
            sender.sendMessage(plugin.formatMessage(plugin.getConfig().getString("messages.protection_not_found")));
            return true;
        }
        
        Player targetPlayer = Bukkit.getPlayer(playerName);
        
        if (targetPlayer == null) {
            sender.sendMessage(plugin.formatMessage(plugin.getConfig().getString("messages.player_not_found")));
            return true;
        }
        
        if (action.equalsIgnoreCase("add")) {

            // Verificar si el jugador ya es owner
            if (protection.isOwner(targetPlayer.getUniqueId())) {
                sender.sendMessage(plugin.formatMessage(plugin.getConfig().getString("messages.already_owner")));
                return true;
            }
            protectionManager.addOwner(protection, targetPlayer.getUniqueId());
            protection = protectionManager.getProtectionByName(protectionName);

            if (!protection.isMember(targetPlayer.getUniqueId())) {
                protectionManager.addMember(protection, targetPlayer.getUniqueId());
            }

            String message = plugin.getConfig().getString("messages.owner_added")
                    .replace("%player%", targetPlayer.getName());
            
            sender.sendMessage(plugin.formatMessage(message));
        } else if (action.equalsIgnoreCase("del")) {
            // Verificar si es el owner primario
            if (protection.getPrimaryOwner().equals(targetPlayer.getUniqueId())) {
                sender.sendMessage(plugin.formatMessage(plugin.getConfig().getString("messages.no_delete_ownerprimary")));
                return true;
            }

            // Verificar si el jugador es realmente owner
            if (!protection.isOwner(targetPlayer.getUniqueId())) {
                sender.sendMessage(plugin.formatMessage(plugin.getConfig().getString("messages.not_owner1")));
                return true;
            }

            // Eliminar owner
            try {
                protectionManager.removeOwner(protection, targetPlayer.getUniqueId());
                protection = protectionManager.getProtectionByName(protectionName);

                if (protection.isMember(targetPlayer.getUniqueId())) {
                    protectionManager.removeMember(protection, targetPlayer.getUniqueId());
                }

                String message = plugin.getConfig().getString("messages.owner_removed")
                        .replace("%player%", targetPlayer.getName());
                sender.sendMessage(plugin.formatMessage(message));
            } catch (Exception e) {
                sender.sendMessage(plugin.formatMessage("§cError al eliminar el owner: " + e.getMessage()));
            }
        } else {
            sender.sendMessage(plugin.formatMessage("§cAcción inválida. Usa 'add' o 'del'."));
        }
        
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            // Autocompletar nombres de protección (pueden contener espacios)
            List<String> completions = new ArrayList<>();
            List<Protection> protections = protectionManager.getAllProtections();

            String input = args[0].toLowerCase();
            for (Protection protection : protections) {
                if (protection.getName() != null && protection.getName().toLowerCase().startsWith(input)) {
                    completions.add(protection.getName());
                }
            }
            return completions;

        } else if (args.length >= 2) {
            if (args.length == 2 || (args.length > 2 && args[args.length - 2].isEmpty())) {
                List<String> actions = Arrays.asList("add", "del");
                String input = args.length == 2 ? args[1].toLowerCase() : "";
                return actions.stream()
                        .filter(action -> action.startsWith(input))
                        .collect(Collectors.toList());
            }

            // Si estamos en el último argumento (jugador)
            if (!args[args.length - 1].isEmpty()) {
                String input = args[args.length - 1].toLowerCase();
                return Bukkit.getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase().startsWith(input))
                        .collect(Collectors.toList());
            }
        }

        return new ArrayList<>();
    }
}