package com.viciont.viciontprotections.api.event;

import com.viciont.viciontprotections.models.Protection;
import org.bukkit.event.*;

/**
 * Se dispara en el hilo principal antes de crear, modificar o eliminar. Puede cancelar la operación
 * completa.
 */
public final class ProtectionChangingEvent extends Event implements Cancellable {
  private static final HandlerList HANDLERS = new HandlerList();
  private final Protection previous, proposed;
  private boolean cancelled;

  public ProtectionChangingEvent(Protection previous, Protection proposed) {
    this.previous = previous;
    this.proposed = proposed;
  }

  /** null al crear. */
  public Protection getPrevious() {
    return previous;
  }

  /** null al eliminar. */
  public Protection getProposed() {
    return proposed;
  }

  public boolean isCancelled() {
    return cancelled;
  }

  public void setCancelled(boolean value) {
    cancelled = value;
  }

  public HandlerList getHandlers() {
    return HANDLERS;
  }

  public static HandlerList getHandlerList() {
    return HANDLERS;
  }
}
