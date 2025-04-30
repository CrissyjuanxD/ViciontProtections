package com.viciont.viciontprotections;

import com.viciont.viciontprotections.commands.*;
import com.viciont.viciontprotections.database.DatabaseManager;
import com.viciont.viciontprotections.listeners.BlockListener;
import com.viciont.viciontprotections.listeners.PlayerListener;
import com.viciont.viciontprotections.managers.ProtectionManager;
import com.viciont.viciontprotections.managers.TradeManager;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

public final class ViciontProtections extends JavaPlugin {
    
    private DatabaseManager databaseManager;
    private ProtectionManager protectionManager;
    private TradeManager tradeManager;

    @Override
    public void onEnable() {
        // Save default config
        saveDefaultConfig();
        
        // Initialize database
        databaseManager = new DatabaseManager(this);
        databaseManager.initialize();
        
        // Initialize protection manager
        protectionManager = new ProtectionManager(this, databaseManager);
        tradeManager = new TradeManager(this, protectionManager);
        
        // Register event listeners
        getServer().getPluginManager().registerEvents(new BlockListener(this, protectionManager), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this, protectionManager), this);
        
        // Register commands
        getCommand("givepr").setExecutor(new GiveProtectionCommand(this, protectionManager));
        getCommand("addnamepr").setExecutor(new NameProtectionCommand(this, protectionManager));
        getCommand("addmember").setExecutor(new AddPlayerCommand(this, protectionManager));
        getCommand("delmember").setExecutor(new RemovePlayerCommand(this, protectionManager));
        getCommand("addowner").setExecutor(new AddOwnerCommand(this, protectionManager));
        getCommand("prlist").setExecutor(new ProtectionListCommand(this, protectionManager));
        getCommand("newnamepr").setExecutor(new NewProtectionNameCommand(this, protectionManager));
        getCommand("modnamepr").setExecutor(new ModifyProtectionNameCommand(this, protectionManager));
        getCommand("modmember").setExecutor(new ModifyProtectionCommand(this, protectionManager));
        getCommand("modowner").setExecutor(new ModifyOwnerCommand(this, protectionManager));
        getCommand("removepr").setExecutor(new DeleteProtectionCommand(this, protectionManager));
        getCommand("ownerlist").setExecutor(new OwnerListCommand(this, protectionManager));
        getCommand("memberlist").setExecutor(new MemberListCommand(this, protectionManager));
        getCommand("delowner").setExecutor(new DeleteOwnerCommand(this, protectionManager));
        getCommand("prvillager").setExecutor(new ProtectionVillagerCommand(this, tradeManager));
        getCommand("vpreload").setExecutor(new ReloadConfigCommand(this));
        
        // Register tab completers
        getCommand("givepr").setTabCompleter(new GiveProtectionCommand(this, protectionManager));
        getCommand("modnamepr").setTabCompleter(new ModifyProtectionNameCommand(this, protectionManager));
        getCommand("modmember").setTabCompleter(new ModifyProtectionCommand(this, protectionManager));
        getCommand("modowner").setTabCompleter(new ModifyOwnerCommand(this, protectionManager));
        getCommand("removepr").setTabCompleter(new DeleteProtectionCommand(this, protectionManager));
        getCommand("delowner").setTabCompleter(new DeleteOwnerCommand(this, protectionManager));

        
        getLogger().info("ViciontProtections has been enabled!");
    }

    @Override
    public void onDisable() {
        if (databaseManager != null) {
            databaseManager.close();
        }
        getLogger().info("ViciontProtections has been disabled!");
    }
    
    public String formatMessage(String message) {
        String prefix = getConfig().getString("messages.prefix", "");
        return ChatColor.translateAlternateColorCodes('&', prefix + message);
    }
}