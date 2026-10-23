package com.viciont.viciontprotections.api.clan;

import java.util.concurrent.CompletableFuture;
import org.bukkit.entity.Player;

/**
 * Requisito que otro plugin exige para fundar un clan, como cobrar un depósito inicial. Se piden
 * en orden de registro, en el hilo principal; si uno falla, a los anteriores se les llama {@link
 * #refund} y el jugador conserva el bloque de clan.
 */
public interface FoundingRequirement {
  /** Línea para el diálogo de fundación, por ejemplo «Depósito inicial: 10 DinoCoins». */
  default String describe(Player founder) {
    return null;
  }

  /**
   * Comprueba y cobra. El futuro termina con normalidad para continuar, o con una excepción cuyo
   * mensaje se muestra al jugador.
   */
  CompletableFuture<Void> charge(Player founder, String clanName);

  /** Devuelve lo cobrado cuando la fundación falla después de {@link #charge}. */
  default void refund(Player founder, String clanName) {}
}
