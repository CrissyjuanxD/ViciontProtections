package com.viciont.viciontprotections.api.event.clan;

import com.viciont.viciontprotections.api.clan.Clan;
import org.bukkit.event.*;

/** Después de guardar cualquier cambio de un clan, en el hilo principal. */
public final class ClanChangedEvent extends Event {
  private static final HandlerList HANDLERS = new HandlerList();
  private final Clan previous, current;

  public ClanChangedEvent(Clan previous, Clan current) {
    this.previous = previous;
    this.current = current;
  }

  /** null si el clan se acaba de fundar. */
  public Clan getPrevious() {
    return previous;
  }

  /** null si el clan se disolvió. */
  public Clan getCurrent() {
    return current;
  }

  public HandlerList getHandlers() {
    return HANDLERS;
  }

  public static HandlerList getHandlerList() {
    return HANDLERS;
  }
}
