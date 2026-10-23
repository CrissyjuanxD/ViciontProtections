package com.viciont.viciontprotections;

import com.viciont.viciontprotections.api.ViciontProtectionsApi;
import com.viciont.viciontprotections.api.clan.Clan;
import com.viciont.viciontprotections.api.clan.ViciontClansApi;
import com.viciont.viciontprotections.clans.*;
import com.viciont.viciontprotections.commands.ClanCommand;
import com.viciont.viciontprotections.commands.ProtectionCommand;
import com.viciont.viciontprotections.database.DatabaseManager;
import com.viciont.viciontprotections.integration.WorldGuardBridge;
import com.viciont.viciontprotections.listeners.ProtectionListener;
import com.viciont.viciontprotections.managers.ProtectionManager;
import com.viciont.viciontprotections.security.AccessPolicy;
import com.viciont.viciontprotections.ui.*;
import java.util.Objects;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class ViciontProtections extends JavaPlugin {
  private DatabaseManager database;
  private ProtectionManager protections;
  private Boundaries boundaries;
  private Messages messages;
  private ClanManager clans;
  private ClanChat clanChat;
  private ClanTags clanTags;
  private ClanFounding founding;

  @Override
  public void onLoad() {
    WorldGuardBridge.registerFlag();
  }

  @Override
  public void onEnable() {
    try {
      saveDefaultConfig();
      cleanLegacyConfig();
      addMissingSections();
      getConfig().options().copyDefaults(true);
      saveConfig();
      AccessPolicy.applyUserDefaults(this);
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
      var listener = new ProtectionListener(this, protections, messages, boundaries, dialogs);
      var events = getServer().getPluginManager();
      events.registerEvents(listener, this);
      getServer()
          .getServicesManager()
          .register(ViciontProtectionsApi.class, protections, this, ServicePriority.Normal);

      // Viciont Clans: mismas protecciones y base de datos, con su propio archivo clans.yml.
      var clanSettings = new ClanSettings(this);
      clans =
          new ClanManager(
              this, database.clans(), protections, clanSettings, new ClanMessages(this, clanSettings));
      var wars = new ClanWars(this, clans);
      clanChat = new ClanChat(this, clans);
      clans.connect(wars, clanChat);
      var clanViews = new ClanViews(clans, dialogs);
      founding = new ClanFounding(this, clans);
      founding.connect(clanViews);
      clanViews.connect(founding);
      clanTags = new ClanTags(this, clans);
      var clanListener = new ClanListener(this, clans, clanViews);
      listener.setTerritories(clanListener);
      AccessPolicy.setTerritories(id -> clans.territoryClan(id).map(Clan::name).orElse(null));
      for (var handler : java.util.List.of(clans, clanChat, founding, clanTags, clanListener))
        events.registerEvents(handler, this);
      commands.setReloadHook(clanSettings::reload);
      var clanCommand = new ClanCommand(this, clans, founding, clanViews, boundaries);
      getDescription()
          .getCommands()
          .keySet()
          .forEach(
              name -> {
                var command = Objects.requireNonNull(getCommand(name));
                boolean clan = name.equals("clan") || name.equals("cc") || name.equals("prclan");
                command.setExecutor(clan ? clanCommand : commands);
                command.setTabCompleter(clan ? clanCommand : commands);
              });
      getServer()
          .getServicesManager()
          .register(ViciontClansApi.class, clans, this, ServicePriority.Normal);
      if (events.isPluginEnabled("PlaceholderAPI")) ClanPlaceholders.register(this, clans);
      getLogger()
          .info(
              "ViciontProtections "
                  + getDescription().getVersion()
                  + " habilitado: "
                  + protections.getProtections().size()
                  + " protecciones y "
                  + clans.getClans().size()
                  + " clanes, seguridad WorldGuard y API disponibles. Diálogos: "
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

  /**
   * Las secciones nuevas de config.yml (como permissions en 2.0.3) se añaden al final con su
   * comentario; copyDefaults las dejaba arriba del archivo y sin explicar.
   */
  private void addMissingSections() {
    var config = getConfig();
    var defaults = config.getDefaults();
    if (defaults == null) return;
    for (String key : defaults.getKeys(false)) {
      if (config.contains(key, true)) continue;
      var section = defaults.getConfigurationSection(key);
      if (section == null) config.set(key, defaults.get(key));
      else
        for (String path : section.getKeys(true))
          if (!section.isConfigurationSection(path)) config.set(key + "." + path, section.get(path));
      config.setComments(key, defaults.getComments(key));
    }
  }

  @Override
  public void onDisable() {
    getServer().getServicesManager().unregisterAll(this);
    if (founding != null) founding.cancelAll();
    if (clanTags != null) clanTags.close();
    if (clanChat != null) clanChat.close();
    if (clans != null) clans.close();
    AccessPolicy.setTerritories(null);
    if (boundaries != null) boundaries.close();
    if (database != null) database.close();
    getLogger().info("ViciontProtections deshabilitado.");
  }

  public ViciontProtectionsApi getApi() {
    if (protections == null) throw new IllegalStateException("La API todavía no está disponible.");
    return protections;
  }

  public ViciontClansApi getClansApi() {
    if (clans == null) throw new IllegalStateException("La API de clanes todavía no está disponible.");
    return clans;
  }
}
