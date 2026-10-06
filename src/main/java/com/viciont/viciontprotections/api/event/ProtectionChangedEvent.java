package com.viciont.viciontprotections.api.event;

import com.viciont.viciontprotections.models.Protection;
import org.bukkit.event.*;

/**
 * Notificación en el hilo principal después de confirmar la escritura SQL y sincronizar WorldGuard.
 */
public final class ProtectionChangedEvent extends Event {
  private static final HandlerList HANDLERS = new HandlerList();
  private final Protection previous, current;

  public ProtectionChangedEvent(Protection previous, Protection current) {
    this.previous = previous;
    this.current = current;
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
