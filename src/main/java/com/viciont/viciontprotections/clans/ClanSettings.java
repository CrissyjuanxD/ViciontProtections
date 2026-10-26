package com.viciont.viciontprotections.clans;

import com.viciont.viciontprotections.api.clan.*;
import com.viciont.viciontprotections.ui.Messages;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** Valores de clans.yml, con los del JAR como respaldo de cualquier clave que falte. */
public final class ClanSettings {
  /** Paleta de clanes: amarillos pastel, dorado y naranja con toques morados. */
  static final Map<String, String> DEFAULT_PALETTE =
      Map.of(
          "titulo", "B98CFF",
          "texto", "FFF1CC",
          "suave", "CDB4F5",
          "dato", "FFD36B",
          "acento", "FFA94D",
          "error", "FF9F80",
          "admin", "C99BFF",
          "guerra", "FF7A45");

  /** Casillas propias del plugin, en el orden del menú de permisos. */
  static final List<ClanPermission> BUILT_IN =
      List.of(
          permission("puertas", "Usar puertas, botones y palancas", "Puertas, trampillas, vallas, botones, palancas y placas."),
          permission("construir", "Construir y romper", "Colocar y romper bloques, cubos, marcos y soportes."),
          permission("animales", "Animales, vehículos y aldeanos", "Interactuar con animales, aldeanos, monturas y vehículos."),
          permission("limites", "Ver los límites", "Mostrar el borde del territorio con partículas."),
          permission("cofres", "Abrir cofres y contenedores", "Cofres, barriles, hornos, tolvas y cajas de shulker."),
          permission("aportar", "Aportar protecciones", "Ofrecer una protección propia como territorio del clan."),
          permission("invitar", "Invitar jugadores", "Invitar con un rango inferior al propio."),
          permission("expulsar", "Expulsar", "Expulsar a miembros de rango inferior."),
          permission("rangos", "Ascender y degradar", "Cambiar el rango de miembros inferiores."),
          permission("territorios", "Gestionar territorios", "Aceptar protecciones aportadas y devolver territorios."),
          permission("banderas", "Banderas de WorldGuard", "Cambiar las banderas permitidas en los territorios."),
          permission("ajustes", "Nombre y color", "Cambiar el nombre y el color del clan."),
          permission("guerra", "Guerras", "Declarar la guerra, rendirse y firmar la paz."));

  private static ClanPermission permission(String key, String name, String description) {
    return new ClanPermission(key, name, description, Set.of());
  }

  public record Topic(String key, String title, String icon, List<String> lines) {}

  private final JavaPlugin plugin;
  private YamlConfiguration config;
  private Map<String, String> palette = DEFAULT_PALETTE;
  private final Map<ClanRank, String> rankNames = new EnumMap<>(ClanRank.class);
  private final Map<String, String> colors = new LinkedHashMap<>();
  private final List<Topic> topics = new ArrayList<>();

  public ClanSettings(JavaPlugin plugin) {
    this.plugin = plugin;
    reload();
  }

  public void reload() {
    File file = new File(plugin.getDataFolder(), "clans.yml");
    if (!file.exists()) plugin.saveResource("clans.yml", false);
    config = YamlConfiguration.loadConfiguration(file);
    try (var input =
        new InputStreamReader(
            Objects.requireNonNull(plugin.getResource("clans.yml")), StandardCharsets.UTF_8)) {
      config.setDefaults(YamlConfiguration.loadConfiguration(input));
    } catch (IOException error) {
      throw new IllegalStateException(error);
    }
    palette = Messages.palette(config.getConfigurationSection("paleta"), DEFAULT_PALETTE);
    rankNames.clear();
    for (ClanRank rank : ClanRank.values())
      rankNames.put(rank, config.getString("rangos." + rank.key(), rank.key()));
    colors.clear();
    ConfigurationSection section = section("colores");
    for (String key : section.getKeys(false)) {
      String hex = section.getString(key, "").trim();
      if (hex.matches("#[a-fA-F0-9]{6}")) colors.put(key.toLowerCase(Locale.ROOT), hex.toUpperCase(Locale.ROOT));
    }
    if (colors.isEmpty()) colors.put("lila", "#C29BFF");
    topics.clear();
    ConfigurationSection guide = section("guia.temas");
    // Incluye temas nuevos del JAR también al actualizar un clans.yml existente.
    Set<String> topicKeys = new LinkedHashSet<>(guide.getKeys(false));
    ConfigurationSection defaultGuide = config.getDefaults().getConfigurationSection("guia.temas");
    if (defaultGuide != null) topicKeys.addAll(defaultGuide.getKeys(false));
    for (String key : topicKeys)
      topics.add(
          new Topic(
              key,
              config.getString("guia.temas." + key + ".titulo", key),
              config.getString("guia.temas." + key + ".icono", "۞"),
              config.getStringList("guia.temas." + key + ".lineas")));
  }

  private ConfigurationSection section(String path) {
    ConfigurationSection section = config.getConfigurationSection(path);
    if (section == null || section.getKeys(false).isEmpty())
      section = Objects.requireNonNull(config.getDefaults()).getConfigurationSection(path);
    return section == null ? new YamlConfiguration() : section;
  }

  // ------------------------------------------------------------------ valores

  public boolean enabled() {
    return config.getBoolean("clanes.activos", true);
  }

  public Map<String, String> palette() {
    return palette;
  }

  public String prefix(String theme) {
    return config.getString("prefijos." + theme, "");
  }

  public String text(String path) {
    return config.getString(path, "");
  }

  public int number(String path, int fallback) {
    return config.getInt(path, fallback);
  }

  public boolean flag(String path) {
    return config.getBoolean(path, false);
  }

  public int maxMembers() {
    return Math.max(1, config.getInt("clanes.miembros-maximos", 30));
  }

  public int nameMin() {
    return Math.max(1, config.getInt("clanes.nombre-minimo", 3));
  }

  public int nameMax() {
    return Math.min(32, Math.max(nameMin(), config.getInt("clanes.nombre-maximo", 24)));
  }

  public long inviteMillis() {
    return Math.max(1, config.getLong("clanes.invitacion-minutos", 5)) * 60_000L;
  }

  public long territoryRequestMillis() {
    return Math.max(1, config.getLong("clanes.solicitud-territorio-dias", 7)) * 86_400_000L;
  }

  /** Tiempo que se guarda la copia de un clan disuelto (0 = no se guardan). */
  public long backupMillis() {
    return Math.max(0, config.getLong("clanes.copia-dias", 5)) * 86_400_000L;
  }

  public long foundingMillis() {
    return Math.max(1, config.getLong("clanes.fundacion-minutos", 5)) * 60_000L;
  }

  public String rankName(ClanRank rank) {
    return rankNames.getOrDefault(rank, rank.key());
  }

  /** Colores elegibles: clave → #RRGGBB. */
  public Map<String, String> colors() {
    return Collections.unmodifiableMap(colors);
  }

  /** Color preestablecido (lila) o cualquier hexadecimal completo #RRGGBB. */
  public Optional<String> color(String value) {
    if (value == null) return Optional.empty();
    String key = value.trim().toLowerCase(Locale.ROOT);
    if (colors.containsKey(key)) return Optional.of(colors.get(key));
    return key.matches("#[0-9a-f]{6}") ? Optional.of(key.toUpperCase(Locale.ROOT)) : Optional.empty();
  }

  public String colorName(String hex) {
    for (var entry : colors.entrySet())
      if (entry.getValue().equalsIgnoreCase(hex)) {
        String key = entry.getKey();
        return Character.toUpperCase(key.charAt(0)) + key.substring(1);
      }
    return hex;
  }

  /**
   * Rangos que tienen la casilla por defecto: los de clans.yml o, para casillas de extensiones sin
   * entrada en el archivo, los que indicó el plugin.
   */
  public Set<ClanRank> defaults(String permission, Set<ClanRank> fallback) {
    String path = "permisos." + permission + ".rangos";
    if (!config.isList(path)) return fallback;
    Set<ClanRank> ranks = EnumSet.noneOf(ClanRank.class);
    for (String value : config.getStringList(path)) ClanRank.parse(value).ifPresent(ranks::add);
    ranks.remove(ClanRank.LEADER);
    return ranks;
  }

  /** El servidor fijó la casilla: ningún líder puede cambiarla. */
  public boolean locked(String permission) {
    return config.getBoolean("permisos." + permission + ".bloqueado", false);
  }

  /** Mundos sin etiqueta sobre el nick. */
  public boolean worldDisabled(String world) {
    return config.getStringList("etiqueta.mundos-desactivados").stream().anyMatch(world::equalsIgnoreCase);
  }

  public List<String> allowedFlags() {
    return config.getStringList("banderas-permitidas").stream()
        .map(flag -> flag.toLowerCase(Locale.ROOT))
        .toList();
  }

  public List<String> guideIntro() {
    return config.getStringList("guia.intro");
  }

  public List<Topic> topics() {
    return List.copyOf(topics);
  }

  /** Sustituye en la guía los valores configurados, como %preaviso%. */
  public String guideValues(String line) {
    return line.replace("%invitacion%", Long.toString(inviteMillis() / 60_000L))
        .replace("%fundacion%", Long.toString(foundingMillis() / 60_000L))
        .replace("%preaviso%", Integer.toString(warPrepHours()))
        .replace("%duracion%", Integer.toString(warMaxDays()))
        .replace("%espera%", Integer.toString(warCooldownDays()))
        .replace("%antiguedad%", Integer.toString(warSeniorityHours()))
        .replace("%novatos%", Integer.toString(newClanDays()))
        .replace("%copia%", Long.toString(backupMillis() / 86_400_000L));
  }

  // ------------------------------------------------------------------ guerras

  public boolean warsEnabled() {
    return config.getBoolean("guerras.activas", true);
  }

  public int warPrepHours() {
    return Math.max(0, config.getInt("guerras.preaviso-horas", 24));
  }

  public int warMaxDays() {
    return Math.max(1, config.getInt("guerras.duracion-maxima-dias", 14));
  }

  public int warCooldownDays() {
    return Math.max(0, config.getInt("guerras.espera-entre-guerras-dias", 7));
  }

  public int warSeniorityHours() {
    return Math.max(0, config.getInt("guerras.antiguedad-horas", 24));
  }

  public int newClanDays() {
    return Math.max(0, config.getInt("guerras.proteccion-clanes-nuevos-dias", 3));
  }

  public int maxText() {
    return Math.max(10, config.getInt("guerras.motivo-maximo", 120));
  }
}
