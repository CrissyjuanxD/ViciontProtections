package com.viciont.viciontprotections.api.event.clan;

import org.bukkit.entity.Player;
import org.bukkit.event.*;

/** Antes de fundar un clan desde un bloque de clan, antes de cobrar los requisitos. Cancelable. */
public final class ClanFoundEvent extends Event implements Cancellable {
  private static final HandlerList HANDLERS = new HandlerList();
  private final Player founder;
  private final String name, color;
  private boolean cancelled;
  private String cancelMessage;

  public ClanFoundEvent(Player founder, String name, String color) {
    this.founder = founder;
    this.name = name;
    this.color = color;
  }

  public Player getFounder() {
    return founder;
  }

  public String getName() {
    return name;
  }

  public String getColor() {
    return color;
  }

  public boolean isCancelled() {
    return cancelled;
  }

  public void setCancelled(boolean value) {
    cancelled = value;
  }

  /** Cancela la fundación explicando el motivo al jugador. */
  public void cancel(String message) {
    cancelled = true;
    cancelMessage = message;
  }

  public String getCancelMessage() {
    return cancelMessage;
  }

  public HandlerList getHandlers() {
    return HANDLERS;
  }

  public static HandlerList getHandlerList() {
    return HANDLERS;
  }
}
