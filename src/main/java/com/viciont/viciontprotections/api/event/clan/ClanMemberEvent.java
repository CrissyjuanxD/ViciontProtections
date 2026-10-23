package com.viciont.viciontprotections.api.event.clan;

import com.viciont.viciontprotections.api.clan.*;
import java.util.UUID;
import org.bukkit.event.*;

/**
 * Antes de que alguien entre, salga o cambie de rango en un clan. Cancelable, salvo cuando sale
 * porque el clan se disuelve.
 */
public final class ClanMemberEvent extends Event implements Cancellable {
  public enum Type {
    JOIN,
    LEAVE,
    KICK,
    RANK,
    /** El jugador pasa a ser líder; el anterior líder baja a sublíder. */
    LEADER
  }

  private static final HandlerList HANDLERS = new HandlerList();
  private final Clan clan;
  private final UUID player, actor;
  private final Type type;
  private final ClanRank previous, next;
  private boolean cancelled;

  public ClanMemberEvent(
      Clan clan, UUID player, Type type, ClanRank previous, ClanRank next, UUID actor) {
    this.clan = clan;
    this.player = player;
    this.type = type;
    this.previous = previous;
    this.next = next;
    this.actor = actor;
  }

  public Clan getClan() {
    return clan;
  }

  public UUID getPlayer() {
    return player;
  }

  public Type getType() {
    return type;
  }

  /** null al entrar. */
  public ClanRank getPreviousRank() {
    return previous;
  }

  /** null al salir. */
  public ClanRank getNewRank() {
    return next;
  }

  /** Quien hace el cambio; null si es la consola o un plugin. */
  public UUID getActor() {
    return actor;
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
