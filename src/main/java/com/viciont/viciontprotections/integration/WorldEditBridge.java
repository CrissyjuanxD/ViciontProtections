package com.viciont.viciontprotections.integration;

import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.regions.CuboidRegion;
import com.viciont.viciontprotections.api.*;
import org.bukkit.entity.Player;

public final class WorldEditBridge {
  public Bounds selection(Player player) {
    var session = WorldEdit.getInstance().getSessionManager().get(BukkitAdapter.adapt(player));
    try {
      var selection = session.getSelection(BukkitAdapter.adapt(player.getWorld()));
      if (!(selection instanceof CuboidRegion))
        throw new ProtectionException(
            "Selecciona un cuboide con WorldEdit (//sel cuboid, //pos1 y //pos2).");
      var min = selection.getMinimumPoint();
      var max = selection.getMaximumPoint();
      return new Bounds(
          min.getBlockX(),
          min.getBlockY(),
          min.getBlockZ(),
          max.getBlockX(),
          max.getBlockY(),
          max.getBlockZ());
    } catch (IncompleteRegionException failure) {
      throw new ProtectionException(
          "Completa primero la selección de WorldEdit con //pos1 y //pos2.");
    }
  }
}
