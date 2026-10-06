package com.viciont.viciontprotections;

import com.viciont.viciontprotections.api.ViciontProtectionsApi;
import com.viciont.viciontprotections.commands.ProtectionCommand;
import com.viciont.viciontprotections.database.DatabaseManager;
import com.viciont.viciontprotections.integration.WorldGuardBridge;
import com.viciont.viciontprotections.listeners.ProtectionListener;
import com.viciont.viciontprotections.managers.ProtectionManager;
import com.viciont.viciontprotections.ui.*;
import java.util.Objects;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class ViciontProtections extends JavaPlugin {
  private DatabaseManager database;
  private ProtectionManager protections;
  private Boundaries boundaries;
  private Messages messages;

  @Override
  public void onLoad() {
    WorldGuardBridge.registerFlag();
  }

  @Override
  public void onEnable() {
    try {
      saveDefaultConfig();
      getConfig().options().copyDefaults(true);
      saveConfig();
      messages = new Messages(this);
      database = DatabaseManager.open(this);
      protections = new ProtectionManager(this, database, new WorldGuardBridge());
      boundaries = new Boundaries(this, protections);
      var dialogs = new ProtectionDialogs(this, messages, protections);
      var commands = new ProtectionCommand(this, protections, messages, dialogs, boundaries);
      getDescription()
          .getCommands()
          .keySet()
          .forEach(
              name -> {
                var command = Objects.requireNonNull(getCommand(name));
                command.setExecutor(commands);
                command.setTabCompleter(commands);
              });
      getServer()
          .getPluginManager()
          .registerEvents(new ProtectionListener(this, protections, messages, boundaries), this);
      getServer()
          .getServicesManager()
          .register(ViciontProtectionsApi.class, protections, this, ServicePriority.Normal);
      getLogger()
          .info(
              "ViciontProtections "
                  + getDescription().getVersion()
                  + " habilitado: "
                  + protections.getProtections().size()
                  + " protecciones, seguridad WorldGuard y API disponibles.");
    } catch (Exception failure) {
      getLogger()
          .log(
              java.util.logging.Level.SEVERE,
              "No se pudo iniciar ViciontProtections. Se deshabilita para evitar escrituras"
                  + " incompletas.",
              failure);
      getServer().getPluginManager().disablePlugin(this);
    }
  }

  @Override
  public void onDisable() {
    getServer().getServicesManager().unregisterAll(this);
    if (boundaries != null) boundaries.close();
    if (database != null) database.close();
    getLogger().info("ViciontProtections deshabilitado.");
  }

  public ViciontProtectionsApi getApi() {
    if (protections == null) throw new IllegalStateException("La API todavía no está disponible.");
    return protections;
  }
}
