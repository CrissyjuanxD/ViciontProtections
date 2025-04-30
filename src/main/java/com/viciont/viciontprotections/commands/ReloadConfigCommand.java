package com.viciont.viciontprotections.commands;

import com.viciont.viciontprotections.ViciontProtections;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

public class ReloadConfigCommand implements CommandExecutor {
    private final ViciontProtections plugin;

    public ReloadConfigCommand(ViciontProtections plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("viciontprotections.admin.reload")) {
            sender.sendMessage(plugin.formatMessage(plugin.getConfig().getString("messages.no_permission")));
            return true;
        }

        try {
            // Recargar config.yml
            File configFile = new File(plugin.getDataFolder(), "config.yml");
            FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
            plugin.getConfig().setDefaults(config);

            // Recargar valores
            plugin.reloadConfig();

            sender.sendMessage(plugin.formatMessage(plugin.getConfig().getString("messages.config_reloaded")));
            return true;
        } catch (Exception e) {
            sender.sendMessage(plugin.formatMessage("&cError al recargar la configuración: " + e.getMessage()));
            plugin.getLogger().severe("Error al recargar config.yml: " + e.getMessage());
            return false;
        }
    }
}
