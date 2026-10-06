package com.viciont.viciontprotections.api;

import com.viciont.viciontprotections.models.Protection;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

/**
 * Servicio registrado en Bukkit.getServicesManager().load(ViciontProtectionsApi.class). Las
 * consultas espaciales y los ItemStack se usan en el hilo principal. Las escrituras admiten
 * cualquier hilo y normalmente completan su futuro en el principal después de persistir. Si el
 * plugin ya no está disponible, el fallo puede completarse en el hilo solicitante o SQL. Nunca
 * bloquear el hilo del servidor con join()/get(). Los plugins consumidores son de confianza: deben
 * comprobar los permisos de sus jugadores antes de solicitar modificaciones.
 */
public interface ViciontProtectionsApi {
  ItemStack createProtectionBlock(ProtectionBlock block, int amount);

  Optional<ProtectionBlock> readProtectionBlock(ItemStack item);

  Optional<Protection> getProtection(UUID id);

  Optional<Protection> getProtectionAt(Location location);

  List<Protection> getProtections();

  List<Protection> getProtections(UUID player);

  boolean canAccess(UUID protection, UUID player);

  CompletableFuture<Protection> createProtection(CreateProtectionRequest request);

  CompletableFuture<Protection> renameProtection(UUID protection, String name);

  CompletableFuture<Protection> addMember(UUID protection, UUID player);

  CompletableFuture<Protection> removeMember(UUID protection, UUID player);

  CompletableFuture<Protection> addOwner(UUID protection, UUID player);

  CompletableFuture<Protection> removeOwner(UUID protection, UUID player);

  CompletableFuture<Protection> transferOwnership(UUID protection, UUID newCreator);

  CompletableFuture<Protection> setFlag(UUID protection, String flag, String value);

  CompletableFuture<Void> deleteProtection(UUID protection);
}
