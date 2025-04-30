package com.viciont.viciontprotections.commands;

import com.viciont.viciontprotections.ViciontProtections;
import com.viciont.viciontprotections.managers.TradeManager;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.util.Vector;

public class ProtectionVillagerCommand implements CommandExecutor {

    private final ViciontProtections plugin;
    private final TradeManager tradeManager;

    public ProtectionVillagerCommand(ViciontProtections plugin, TradeManager tradeManager) {
        this.plugin = plugin;
        this.tradeManager = tradeManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(plugin.formatMessage("§cEste comando solo puede ser ejecutado por un jugador."));
            return true;
        }

        Player player = (Player) sender;

        if (!player.hasPermission("viciontprotections.admin.villager")) {
            player.sendMessage(plugin.formatMessage(plugin.getConfig().getString("messages.no_permission")));
            return true;
        }

        Location location;

        if (args.length == 3) {
            try {
                double x = Double.parseDouble(args[0]);
                double y = Double.parseDouble(args[1]);
                double z = Double.parseDouble(args[2]);
                location = new Location(player.getWorld(), x, y, z);
            } catch (NumberFormatException e) {
                player.sendMessage(plugin.formatMessage("§cCoordenadas inválidas."));
                return true;
            }
        } else if (args.length == 0) {
            location = player.getTargetBlock(null, 5).getLocation().add(0.5, 1, 0.5);
        } else {
            player.sendMessage(plugin.formatMessage("§cUso: /prvillager [x] [y] [z]"));
            return true;
        }

        // Spawn villager mirando hacia el jugador
        Location spawnLocation = location.clone();

        Villager villager = (Villager) player.getWorld().spawnEntity(spawnLocation, EntityType.VILLAGER);

        Vector direction = player.getLocation().toVector().subtract(spawnLocation.toVector()).normalize();
        Location lookAt = spawnLocation.clone();
        lookAt.setDirection(direction);
        villager.teleport(lookAt);

        // Configuración del aldeano
        villager.setCustomName(ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("villager.name", "&c&lComerciante de Protecciones")));
        villager.setCustomNameVisible(true);

        boolean disableAI = plugin.getConfig().getBoolean("villager.disable_ai", true);
        villager.setAI(!disableAI);
        villager.setInvulnerable(true);
        villager.setCollidable(false);

        // Configurar profesión y experiencia
        villager.setVillagerExperience(0);
        villager.setVillagerLevel(1);
        villager.setProfession(Villager.Profession.CLERIC);

        // Configurar trades
        tradeManager.setupAllTrades(villager);

        player.sendMessage(plugin.formatMessage(plugin.getConfig().getString("messages.villager_spawned")));
        return true;
    }
}