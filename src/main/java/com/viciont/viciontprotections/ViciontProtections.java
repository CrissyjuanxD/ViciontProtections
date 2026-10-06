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
      cleanLegacyConfig();
      getConfig().options().copyDefaults(true);
      saveConfig();
      messages = new Messages(this);
      database = DatabaseManager.open(this);
      var guard = new WorldGuardBridge();
      guard.setDenyMessage(messages.text("avisos.denegado"));
      protections = new ProtectionManager(this, database, guard);
      boundaries = new Boundaries(this, protections);
      var dialogs = new ProtectionDialogs(this, messages, protections, boundaries);
      var chat = new ChatViews(messages, boundaries);
      var commands =
          new ProtectionCommand(this, protections, messages, dialogs, chat, boundaries);
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
          .registerEvents(
              new ProtectionListener(this, protections, messages, boundaries, dialogs), this);
      getServer()
          .getServicesManager()
          .register(ViciontProtectionsApi.class, protections, this, ServicePriority.Normal);
      getLogger()
          .info(
              "ViciontProtections "
                  + getDescription().getVersion()
                  + " habilitado: "
                  + protections.getProtections().size()
                  + " protecciones, seguridad WorldGuard y API disponibles. Diálogos: "
                  + (ProtectionDialogs.supportsVersion(getServer().getBukkitVersion())
                      ? dialogs.mode()
                      : "no disponibles en esta versión (se usa el chat)")
                  + ".");
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

  /**
   * La 1.x guardaba sus mensajes (con el prefijo antiguo) y el aldeano en config.yml. La 2.x usa
   * messages.yml, así que esas secciones se retiran tras guardar una copia del archivo.
   */
  private void cleanLegacyConfig() throws java.io.IOException {
    var config = getConfig();
    if (!config.isConfigurationSection("messages") && !config.isConfigurationSection("villager"))
      return;
    var file = new java.io.File(getDataFolder(), "config.yml").toPath();
    var backup = file.resolveSibling("config-1.x-anterior.yml");
    java.nio.file.Files.copy(file, backup, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    config.set("messages", null);
    config.set("villager", null);
    for (String type : java.util.List.of("small", "medium", "large")) {
      config.set("protection_types." + type + ".name", null);
      config.set("protection_types." + type + ".cost", null);
    }
    saveConfig();
    getLogger()
        .info(
            "config.yml de 1.x: se retiraron las secciones messages y villager (ahora los mensajes"
                + " están en messages.yml). Copia guardada en "
                + backup.getFileName()
                + ".");
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
