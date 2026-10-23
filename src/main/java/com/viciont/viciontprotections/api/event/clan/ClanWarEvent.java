package com.viciont.viciontprotections.api.event.clan;

import com.viciont.viciontprotections.api.clan.ClanWar;
import org.bukkit.event.*;

/**
 * Guerra entre clanes. {@link Type#DECLARE} llega antes de declararla y se puede cancelar (por
 * ejemplo, si el clan tiene una deuda de guerra). {@link Type#START} avisa de que empieza el PvP y
 * {@link Type#END} de que terminó, con el final ya guardado en {@link #getWar()}: ahí se paga la
 * recompensa de una rendición.
 */
public final class ClanWarEvent extends Event implements Cancellable {
  public enum Type {
    DECLARE,
    START,
    END
  }

  private static final HandlerList HANDLERS = new HandlerList();
  private final ClanWar war;
  private final Type type;
  private boolean cancelled;
  private String cancelMessage;

  public ClanWarEvent(ClanWar war, Type type) {
    this.war = war;
    this.type = type;
  }

  public ClanWar getWar() {
    return war;
  }

  public Type getType() {
    return type;
  }

  public boolean isCancelled() {
    return cancelled;
  }

  /** Solo tiene efecto en {@link Type#DECLARE}. */
  public void setCancelled(boolean value) {
    cancelled = value && type == Type.DECLARE;
  }

  /** Cancela la declaración explicando el motivo al líder, por ejemplo una deuda de guerra. */
  public void cancel(String message) {
    setCancelled(true);
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
