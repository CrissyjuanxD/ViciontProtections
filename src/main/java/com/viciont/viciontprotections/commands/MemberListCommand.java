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

public class MemberListCommand implements CommandExecutor {

    private final ViciontProtections plugin;
    private final ProtectionManager protectionManager;

    public MemberListCommand(ViciontProtections plugin, ProtectionManager protectionManager) {
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

        // Mostrar información de la protección
        player.sendMessage(" ");
        player.sendMessage("§6§lInformación de la protección:");
        player.sendMessage("§eNombre: §7" + (protection.getName() != null ? protection.getName() : "Sin nombre"));
        player.sendMessage("§eDueño principal: §7" + protection.getPrimaryOwnerName());

        // Mostrar miembros
        if (!protection.getMembers().isEmpty()) {
            player.sendMessage("§6§lMiembros:");
            for (UUID memberUuid : protection.getMembers()) {
                // No mostrar miembros que también son owners
                if (!protection.isOwner(memberUuid)) {
                    String memberName = Bukkit.getOfflinePlayer(memberUuid).getName();
                    player.sendMessage("§7- §a" + memberName);
                }
            }
        }

        // Mostrar conteo
        int totalOwners = protection.getOwners().size();
        int totalMembers = (int) protection.getMembers().stream()
                .filter(uuid -> !protection.isOwner(uuid))
                .count();

        player.sendMessage(
                String.format("§eTotal: §7%d dueños y %d miembros", totalOwners, totalMembers)
        );
        player.sendMessage(" ");

        return true;
    }
}