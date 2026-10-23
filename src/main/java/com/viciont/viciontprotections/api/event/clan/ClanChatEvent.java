package com.viciont.viciontprotections.api.event.clan;

import com.viciont.viciontprotections.api.clan.Clan;
import java.util.Objects;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.*;

/**
 * Mensaje del chat de un clan antes de entregarse. Puede llegar fuera del hilo principal (cuando
 * se escribe en el chat con el modo clan activo). Cancelarlo lo bloquea también en la consola.
 */
public final class ClanChatEvent extends Event implements Cancellable {
  private static final HandlerList HANDLERS = new HandlerList();
  private final Player player;
  private final Clan clan;
  private String message;
  private boolean cancelled;

  public ClanChatEvent(Player player, Clan clan, String message) {
    super(!Bukkit.isPrimaryThread());
    this.player = player;
    this.clan = clan;
    this.message = message;
  }

  public Player getPlayer() {
    return player;
  }

  public Clan getClan() {
    return clan;
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String value) {
    message = Objects.requireNonNull(value);
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
