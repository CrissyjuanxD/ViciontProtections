package com.viciont.viciontprotections.commands;

import com.viciont.viciontprotections.ViciontProtections;
import com.viciont.viciontprotections.managers.ProtectionManager;
import com.viciont.viciontprotections.models.Protection;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class NameProtectionCommand implements CommandExecutor {

    private final ViciontProtections plugin;
    private final ProtectionManager protectionManager;

    public NameProtectionCommand(ViciontProtections plugin, ProtectionManager protectionManager) {
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

        if (!player.hasPermission("viciontprotections.user.name")) {
            player.sendMessage(plugin.formatMessage(plugin.getConfig().getString("messages.no_permission")));
            return true;
        }

        if (args.length < 1) {
            player.sendMessage(plugin.formatMessage(plugin.getConfig().getString("messages.invalid_arguments")));
            return false;
        }

        // Buscamos la protección en memoria
        Protection protection = protectionManager.getPlayerCurrentProtection(player);

        // --> SOLUCIÓN: Si falla, forzamos la búsqueda física en las coordenadas del jugador <--
        if (protection == null) {
            protection = protectionManager.getProtectionAt(player.getLocation());
        }

        if (protection == null) {
            player.sendMessage(plugin.formatMessage(plugin.getConfig().getString("messages.not_in_protection")));
            return true;
        }

        if (!protection.isOwner(player.getUniqueId())) {
            player.sendMessage(plugin.formatMessage(plugin.getConfig().getString("messages.not_owner")));
            return true;
        }

        if (protection.getName() != null) {
            player.sendMessage(plugin.formatMessage("§cEsta protección ya tiene nombre. Usa /modnamepr si quieres cambiarlo."));
            return true;
        }

        String name = String.join(" ", args);
        if (protectionManager.getProtectionByName(name) != null) {
            player.sendMessage(plugin.formatMessage("§cYa existe una protección con ese nombre. Por favor, elige otro."));
            return true;
        }

        // Le asignamos el nombre a la protección en BD
        protectionManager.setProtectionName(protection, name);

        // ¡LIBERAMOS AL JUGADOR! Ya puede moverse y usar otros comandos
        protectionManager.playersNeedingToName.remove(player.getUniqueId());

        String message = plugin.getConfig().getString("messages.protection_named")
                .replace("%name%", name);

        player.sendMessage(plugin.formatMessage(message));

        return true;
    }
}