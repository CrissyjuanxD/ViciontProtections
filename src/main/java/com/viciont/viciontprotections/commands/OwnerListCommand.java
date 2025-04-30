package com.viciont.viciontprotections.commands;

import com.viciont.viciontprotections.ViciontProtections;
import com.viciont.viciontprotections.managers.ProtectionManager;
import com.viciont.viciontprotections.models.Protection;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.UUID;

public class OwnerListCommand implements CommandExecutor {
    
    private final ViciontProtections plugin;
    private final ProtectionManager protectionManager;
    
    public OwnerListCommand(ViciontProtections plugin, ProtectionManager protectionManager) {
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
        
        Protection protection = protectionManager.getPlayerCurrentProtection(player);
        
        if (protection == null) {
            player.sendMessage(plugin.formatMessage(plugin.getConfig().getString("messages.not_in_protection")));
            return true;
        }
        
/*        if (!protection.getPrimaryOwner().equals(player.getUniqueId())) {
            player.sendMessage(plugin.formatMessage("§cSolo el dueño principal puede ver la lista de dueños."));
            return true;
        }*/

        player.sendMessage(" ");
        player.sendMessage("§eLista de dueños de la protección:");
        player.sendMessage("§6Dueño principal: §e" + protection.getPrimaryOwnerName());
        
        for (UUID ownerUuid : protection.getOwners()) {
            if (!ownerUuid.equals(protection.getPrimaryOwner())) {
                String ownerName = Bukkit.getOfflinePlayer(ownerUuid).getName();
                player.sendMessage("§7- §e" + ownerName);
            }
        }
        player.sendMessage(" ");
        
        return true;
    }
}