package com.viciont.viciontprotections.clans;

import com.viciont.viciontprotections.api.clan.*;
import java.util.*;
import java.util.function.BiFunction;
import org.bukkit.plugin.Plugin;

/** Lo que otros plugins añaden a los clanes: botones, estados, permisos, requisitos y guía. */
final class ClanExtensions {
  record Entry<T>(Plugin plugin, T value) {}

  private final Map<String, Entry<ClanButton>> buttons = new LinkedHashMap<>();
  private final Map<String, Entry<BiFunction<Clan, org.bukkit.entity.Player, String>>> lines =
      new LinkedHashMap<>();
  private final Map<String, Entry<ClanPermission>> permissions = new LinkedHashMap<>();
  private final List<Entry<FoundingRequirement>> requirements = new ArrayList<>();
  private final Map<String, Entry<ClanSettings.Topic>> topics = new LinkedHashMap<>();

  synchronized void button(Plugin plugin, ClanButton button) {
    buttons.put(button.key(), new Entry<>(plugin, button));
  }

  synchronized void line(
      Plugin plugin, String key, BiFunction<Clan, org.bukkit.entity.Player, String> line) {
    lines.put(key, new Entry<>(plugin, line));
  }

  synchronized void permission(Plugin plugin, ClanPermission permission) {
    if (ClanSettings.BUILT_IN.stream().anyMatch(p -> p.key().equals(permission.key())))
      throw new IllegalArgumentException("La casilla " + permission.key() + " ya es del plugin.");
    permissions.put(permission.key(), new Entry<>(plugin, permission));
  }

  synchronized void requirement(Plugin plugin, FoundingRequirement requirement) {
    requirements.add(new Entry<>(plugin, requirement));
  }

  synchronized void topic(Plugin plugin, ClanSettings.Topic topic) {
    topics.put(topic.key(), new Entry<>(plugin, topic));
  }

  synchronized void unregister(Plugin plugin) {
    buttons.values().removeIf(e -> e.plugin().equals(plugin));
    lines.values().removeIf(e -> e.plugin().equals(plugin));
    permissions.values().removeIf(e -> e.plugin().equals(plugin));
    requirements.removeIf(e -> e.plugin().equals(plugin));
    topics.values().removeIf(e -> e.plugin().equals(plugin));
  }

  synchronized List<ClanButton> buttons() {
    return buttons.values().stream().map(Entry::value).toList();
  }

  synchronized List<Entry<BiFunction<Clan, org.bukkit.entity.Player, String>>> lines() {
    return List.copyOf(lines.values());
  }

  synchronized List<ClanPermission> permissions() {
    return permissions.values().stream().map(Entry::value).toList();
  }

  synchronized Optional<ClanPermission> permission(String key) {
    var entry = permissions.get(key);
    return entry == null ? Optional.empty() : Optional.of(entry.value());
  }

  synchronized List<Entry<FoundingRequirement>> requirements() {
    return List.copyOf(requirements);
  }

  synchronized List<ClanSettings.Topic> topics() {
    return topics.values().stream().map(Entry::value).toList();
  }
}
