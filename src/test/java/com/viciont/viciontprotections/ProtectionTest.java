package com.viciont.viciontprotections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.viciont.viciontprotections.api.*;
import com.viciont.viciontprotections.models.Protection;
import com.viciont.viciontprotections.security.AccessPolicy;
import com.viciont.viciontprotections.ui.ProtectionDialogs;
import java.util.*;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class ProtectionTest {
  private final UUID creator = UUID.randomUUID(),
      owner = UUID.randomUUID(),
      member = UUID.randomUUID();

  private Protection protection() {
    return new Protection(
        UUID.randomUUID(),
        "Casa",
        "world",
        new Bounds(-16, -64, -16, 15, 319, 15),
        creator,
        Set.of(owner),
        Set.of(member),
        Map.of(),
        null,
        1);
  }

  private Player player(UUID id) {
    Player p = mock(Player.class);
    when(p.getUniqueId()).thenReturn(id);
    when(p.hasPermission(anyString()))
        .thenAnswer(i -> ((String) i.getArgument(0)).startsWith("viciontprotections.user."));
    return p;
  }

  @Test
  void exactDimensionsAndTouchingEdges() {
    Bounds b = Bounds.centered(0, 64, 0, 32, 17, 0, -64, 320);
    assertEquals(32, b.width());
    assertEquals(17, b.depth());
    assertTrue(b.contains(-16, -64, -8));
    assertFalse(b.contains(16, 64, 0));
    assertFalse(b.intersects(new Bounds(16, -64, -8, 20, 319, 8)));
    assertTrue(b.intersects(new Bounds(15, 64, 0, 20, 70, 5)));
  }

  @Test
  void rejectsInvalidOrOverflowingDimensions() {
    assertThrows(
        IllegalArgumentException.class, () -> Bounds.centered(0, 64, 0, -1, 16, 0, -64, 320));
    assertThrows(
        IllegalArgumentException.class,
        () -> Bounds.centered(29_999_999, 64, 0, 64, 64, 0, -64, 320));
  }

  @Test
  void addedOwnerCanOnlyManageMembersAndTheirBorders() {
    Player p = player(owner);
    assertTrue(AccessPolicy.allows(p, protection(), AccessPolicy.Action.MEMBERS));
    assertTrue(AccessPolicy.allows(p, protection(), AccessPolicy.Action.BORDERS));
    for (var action :
        List.of(
            AccessPolicy.Action.OWNERS,
            AccessPolicy.Action.RENAME,
            AccessPolicy.Action.DELETE,
            AccessPolicy.Action.FLAGS,
            AccessPolicy.Action.TRANSFER))
      assertFalse(AccessPolicy.allows(p, protection(), action));
  }

  @Test
  void creatorManagesRolesButNotAdministrativeFlags() {
    Player p = player(creator);
    assertTrue(AccessPolicy.allows(p, protection(), AccessPolicy.Action.OWNERS));
    assertTrue(AccessPolicy.allows(p, protection(), AccessPolicy.Action.DELETE));
    assertFalse(AccessPolicy.allows(p, protection(), AccessPolicy.Action.FLAGS));
  }

  @Test
  void membersAndStrangersCannotEscalate() {
    for (UUID id : List.of(member, UUID.randomUUID())) {
      Player p = player(id);
      for (var action :
          List.of(
              AccessPolicy.Action.MEMBERS, AccessPolicy.Action.OWNERS, AccessPolicy.Action.DELETE))
        assertFalse(AccessPolicy.allows(p, protection(), action));
    }
  }

  @Test
  void permissionsAreRequiredEvenForCreator() {
    Player p = player(creator);
    when(p.hasPermission(anyString())).thenReturn(false);
    assertFalse(AccessPolicy.allows(p, protection(), AccessPolicy.Action.OWNERS));
  }

  @Test
  void snapshotsAreImmutable() {
    assertThrows(
        UnsupportedOperationException.class, () -> protection().owners().add(UUID.randomUUID()));
    assertThrows(
        UnsupportedOperationException.class, () -> protection().flags().put("build", "ALLOW"));
  }

  @Test
  void dialogsRespectVersionBoundary() {
    assertFalse(ProtectionDialogs.supportsVersion("1.21-R0.1-SNAPSHOT"));
    assertFalse(ProtectionDialogs.supportsVersion("1.21.5-R0.1-SNAPSHOT"));
    assertTrue(ProtectionDialogs.supportsVersion("1.21.6-R0.1-SNAPSHOT"));
    assertTrue(ProtectionDialogs.supportsVersion("26.2-R0.1-SNAPSHOT"));
    assertTrue(ProtectionDialogs.supportsVersion("26.2.build.129-stable"));
  }
}
