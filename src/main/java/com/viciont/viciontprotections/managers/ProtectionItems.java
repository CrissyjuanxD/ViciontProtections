package com.viciont.viciontprotections.managers;

import com.viciont.viciontprotections.api.ProtectionBlock;
import com.viciont.viciontprotections.ui.Messages;
import java.util.*;
import org.bukkit.*;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class ProtectionItems {
  private final JavaPlugin plugin;

  private NamespacedKey key(String value) {
    return new NamespacedKey(plugin, value);
  }

  public ProtectionItems(JavaPlugin plugin) {
    this.plugin = plugin;
  }

  public ItemStack create(ProtectionBlock spec, int amount) {
    if (!spec.material().isBlock() || !spec.material().isItem())
      throw new IllegalArgumentException("El material debe ser un bloque colocable.");
    if (amount < 1 || amount > spec.material().getMaxStackSize())
      throw new IllegalArgumentException("Cantidad no válida para ese bloque.");
    ItemStack item = new ItemStack(spec.material(), amount);
    var meta = item.getItemMeta();
    meta.setDisplayName(Messages.color("&#C994FF۞ &#EDC7FFBloque protector"));
    meta.setLore(
        List.of(
            Messages.color("&#D9B8FFTamaño: &#F3C8E8" + spec.dimensions()),
            Messages.color("&#E7CCFFColócalo para proteger tu zona."),
            Messages.color("&#F1B6D9Guía: &#D9B8FF/pr guia")));
    var pdc = meta.getPersistentDataContainer();
    pdc.set(key("block"), PersistentDataType.BYTE, (byte) 2);
    pdc.set(key("width"), PersistentDataType.INTEGER, spec.width());
    pdc.set(key("depth"), PersistentDataType.INTEGER, spec.depth());
    pdc.set(key("height"), PersistentDataType.INTEGER, spec.height());
    item.setItemMeta(meta);
    return item;
  }

  public Optional<ProtectionBlock> read(ItemStack item) {
    if (item == null || !item.getType().isBlock() || !item.hasItemMeta()) return Optional.empty();
    var meta = item.getItemMeta();
    var pdc = meta.getPersistentDataContainer();
    if (pdc.has(key("block"), PersistentDataType.BYTE))
      try {
        return Optional.of(
            new ProtectionBlock(
                item.getType(),
                pdc.getOrDefault(key("width"), PersistentDataType.INTEGER, 0),
                pdc.getOrDefault(key("depth"), PersistentDataType.INTEGER, 0),
                pdc.getOrDefault(key("height"), PersistentDataType.INTEGER, -1)));
      } catch (IllegalArgumentException invalid) {
        return Optional.empty();
      }
    // Compatibilidad con los bloques ya entregados por la versión 1.x.
    if (item.getType() == Material.REDSTONE_BLOCK && meta.hasCustomModelData()) {
      String type =
          switch (meta.getCustomModelData()) {
            case 1001 -> "small";
            case 1002 -> "medium";
            case 1003 -> "large";
            default -> null;
          };
      if (type != null) {
        int size =
            plugin
                .getConfig()
                .getInt(
                    "protection_types." + type + ".size",
                    switch (type) {
                      case "small" -> 32;
                      case "medium" -> 64;
                      default -> 128;
                    });
        return Optional.of(ProtectionBlock.square(item.getType(), size));
      }
    }
    return Optional.empty();
  }
}
