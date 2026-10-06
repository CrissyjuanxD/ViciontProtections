package com.viciont.viciontprotections.api.event;

import com.viciont.viciontprotections.models.Protection;
import org.bukkit.entity.Player;
import org.bukkit.event.*;

/**
 * Cambio de región, incluido salir a terreno libre (current=null) y entrar desde él
 * (previous=null).
 */
public final class ProtectionCrossedEvent extends Event {
  private static final HandlerList HANDLERS = new HandlerList();
  private final Player player;
  private final Protection previous, current;

  public ProtectionCrossedEvent(Player player, Protection previous, Protection current) {
    this.player = player;
    this.previous = previous;
    this.current = current;
  }

  public Player getPlayer() {
    return player;
  }

  public Protection getPrevious() {
    return previous;
  }

  public Protection getCurrent() {
    return current;
  }

  public HandlerList getHandlers() {
    return HANDLERS;
  }

  public static HandlerList getHandlerList() {
    return HANDLERS;
  }
}
