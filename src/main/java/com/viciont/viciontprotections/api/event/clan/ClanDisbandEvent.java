package com.viciont.viciontprotections.api.event.clan;

import com.viciont.viciontprotections.api.clan.Clan;
import org.bukkit.event.*;

/**
 * Antes de disolver un clan. Se puede cancelar salvo cuando {@link #isForced()}: la
 * administración, otro plugin mediante la API, la pérdida del territorio principal o la falta de
 * pago. Salvo con {@link Cause#UNPAID}, se guarda una copia del clan que su líder puede recuperar
 * durante unos días colocando un bloque de clan.
 */
public final class ClanDisbandEvent extends Event implements Cancellable {
  /** Quién o qué disuelve el clan. */
  public enum Cause {
    LEADER,
    BLOCK_BROKEN,
    ADMIN,
    PLUGIN,
    HOME_LOST,
    /** No pagó el mantenimiento: no se guarda copia. */
    UNPAID
  }

  private static final HandlerList HANDLERS = new HandlerList();
  private final Clan clan;
  private final Cause cause;
  private final String reason;
  private boolean cancelled;

  public ClanDisbandEvent(Clan clan, Cause cause, String reason) {
    this.clan = clan;
    this.cause = cause;
    this.reason = reason;
  }

  public Clan getClan() {
    return clan;
  }

  public Cause getCause() {
    return cause;
  }

  /** Motivo que se anuncia a los miembros. */
  public String getReason() {
    return reason;
  }

  public boolean isForced() {
    return cause == Cause.ADMIN || cause == Cause.PLUGIN || cause == Cause.HOME_LOST || cause == Cause.UNPAID;
  }

  public boolean isCancelled() {
    return cancelled;
  }

  /** Sin efecto si {@link #isForced()}. */
  public void setCancelled(boolean value) {
    cancelled = value && !isForced();
  }

  public HandlerList getHandlers() {
    return HANDLERS;
  }

  public static HandlerList getHandlerList() {
    return HANDLERS;
  }
}
