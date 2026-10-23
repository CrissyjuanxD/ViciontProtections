package com.viciont.viciontprotections.clans;

import com.viciont.viciontprotections.api.ProtectionException;
import com.viciont.viciontprotections.api.clan.*;
import com.viciont.viciontprotections.api.event.clan.ClanWarEvent;
import com.viciont.viciontprotections.database.ClanStore;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Guerras de rol entre clanes. Se declaran con un motivo, el PvP entre los dos clanes abre tras el
 * preaviso y terminan con una rendición (gana el otro bando), un tratado de paz, la administración
 * o el plazo máximo. No hay marcador: las bajas quedan en la crónica.
 */
public final class ClanWars {
  private static final int CHRONICLE = 60;
  private static final long HOUR = 3_600_000L, DAY = 86_400_000L;

  private final JavaPlugin plugin;
  private final ClanManager manager;
  private final ClanStore store;
  private final ClanSettings settings;
  private final ClanMessages messages;
  private final Map<UUID, ClanWar> wars = new ConcurrentHashMap<>();
  private final Set<UUID> started = new HashSet<>();

  public ClanWars(JavaPlugin plugin, ClanManager manager) throws SQLException {
    this.plugin = plugin;
    this.manager = manager;
    this.store = manager.store();
    this.settings = manager.settings();
    this.messages = manager.messages();
    long now = System.currentTimeMillis();
    for (ClanWar war : store.loadWars()) {
      wars.put(war.id(), war);
      if (war.startsAt() <= now) started.add(war.id());
    }
  }

  // ------------------------------------------------------------------ consultas

  public Optional<ClanWar> active(UUID clan) {
    return wars.values().stream().filter(w -> w.active() && w.involves(clan)).findFirst();
  }

  public List<ClanWar> history(UUID clan) {
    return wars.values().stream()
        .filter(w -> w.involves(clan))
        .sorted(Comparator.comparingLong(ClanWar::declaredAt).reversed())
        .toList();
  }

  /** La guerra que permite a {@code a} y {@code b} luchar ahora, o null. */
  public ClanWar fight(Player a, Player b) {
    if (!settings.warsEnabled()) return null;
    long now = System.currentTimeMillis();
    for (Clan first : manager.getPlayerClans(a.getUniqueId()))
      for (Clan second : manager.getPlayerClans(b.getUniqueId())) {
        if (first.id().equals(second.id())) continue;
        ClanWar war = active(first.id()).orElse(null);
        if (war == null || !war.fighting(now) || !war.enemyOf(first.id()).equals(second.id())) continue;
        if (veteran(first, a.getUniqueId(), now) && veteran(second, b.getUniqueId(), now)) return war;
      }
    return null;
  }

  /** Lleva en el clan el tiempo necesario para luchar y no es invitado. */
  boolean veteran(Clan clan, UUID player, long now) {
    ClanMember member = clan.members().get(player);
    return member != null
        && member.rank() != ClanRank.GUEST
        && now - member.joinedAt() >= settings.warSeniorityHours() * HOUR;
  }

  // ------------------------------------------------------------------ acciones

  public CompletableFuture<ClanWar> declare(Player actor, Clan attacker, Clan defender, String reason) {
    return manager.main(
        () -> {
          if (!settings.warsEnabled()) throw new ProtectionException("Las guerras están desactivadas en este servidor.");
          manager.requireRank(attacker, actor);
          manager.requirePermission(attacker, actor, "guerra");
          if (attacker.id().equals(defender.id())) throw new ProtectionException("Un clan no puede declararse la guerra a sí mismo.");
          if (active(attacker.id()).isPresent()) throw new ProtectionException("Tu clan ya está en guerra. Termínala antes de empezar otra.");
          if (active(defender.id()).isPresent()) throw new ProtectionException(defender.name() + " ya está en guerra con otro clan.");
          long now = System.currentTimeMillis();
          long protectedUntil = defender.foundedAt() + settings.newClanDays() * DAY;
          if (now < protectedUntil)
            throw new ProtectionException(defender.name() + " es un clan nuevo: podrá recibir declaraciones dentro de " + duration(protectedUntil - now) + ".");
          for (ClanWar past : history(attacker.id())) {
            if (!past.involves(defender.id()) || past.ending() == ClanWar.Ending.ADMIN || past.ending() == ClanWar.Ending.DISBANDED) continue;
            long free = past.endedAt() + settings.warCooldownDays() * DAY;
            if (now < free)
              throw new ProtectionException("Vuestros clanes se enfrentaron hace poco: podréis volver a hacerlo dentro de " + duration(free - now) + ".");
            break;
          }
          String motive = manager.cleanText(reason);
          if (motive.isEmpty()) throw new ProtectionException("Escribe el motivo de la guerra.");
          var war =
              new ClanWar(
                  UUID.randomUUID(),
                  attacker.id(),
                  defender.id(),
                  attacker.name(),
                  defender.name(),
                  motive,
                  now,
                  now + settings.warPrepHours() * HOUR,
                  0,
                  null,
                  null,
                  0,
                  0,
                  null,
                  0,
                  null,
                  null,
                  null,
                  List.of(stamp(now) + " " + attacker.name() + " declara la guerra: «" + motive + "»."));
          var event = new ClanWarEvent(war, ClanWarEvent.Type.DECLARE);
          Bukkit.getPluginManager().callEvent(event);
          if (event.isCancelled())
            throw new ProtectionException(
                event.getCancelMessage() != null ? event.getCancelMessage() : "Ahora no se puede declarar esa guerra.");
          if (war.startsAt() <= now) started.add(war.id());
          return save(war)
              .thenApply(
                  saved -> {
                    messages.broadcast(
                        ClanMessages.Theme.WAR,
                        ClanMessages.paint(attacker.name(), attacker.color())
                            + " {texto}declara la guerra a "
                            + ClanMessages.paint(defender.name(), defender.color())
                            + "{texto}: {dato}«"
                            + motive
                            + "»{texto}. "
                            + (settings.warPrepHours() > 0
                                ? "El PvP entre los dos clanes empieza en {acento}" + settings.warPrepHours() + " horas{texto}."
                                : "{acento}¡El PvP entre los dos clanes empieza ya!"));
                    if (war.startsAt() <= now) Bukkit.getPluginManager().callEvent(new ClanWarEvent(saved, ClanWarEvent.Type.START));
                    return saved;
                  });
        });
  }

  /** El clan se rinde: gana el otro bando. {@code tribute} se suma a la recompensa mínima. */
  public CompletableFuture<ClanWar> surrender(Player actor, Clan clan, int tribute, String terms) {
    return manager.main(
        () -> {
          manager.requireRank(clan, actor);
          manager.requirePermission(clan, actor, "guerra");
          ClanWar war = active(clan.id()).orElseThrow(() -> new ProtectionException("Tu clan no está en guerra."));
          if (tribute < 0) throw new ProtectionException("El tributo no puede ser negativo.");
          String clean = manager.cleanText(terms);
          UUID winner = war.enemyOf(clan.id());
          ClanWar ended =
              end(war, ClanWar.Ending.SURRENDER, winner, clean.isEmpty() ? null : clean, tribute,
                  clan.name() + " se rinde" + (clean.isEmpty() ? "." : ": «" + clean + "»."));
          return finish(
              ended,
              ClanMessages.paint(clan.name(), color(clan.id()))
                  + " {texto}se rinde ante "
                  + ClanMessages.paint(war.nameOf(winner), color(winner))
                  + "{texto}."
                  + (clean.isEmpty() ? "" : " Condiciones: {dato}«" + clean + "»{texto}.")
                  + (tribute > 0 ? " Tributo ofrecido: {dato}" + tribute + "{texto}." : ""));
        });
  }

  /**
   * Propone la paz o firma la que propuso el otro clan. Sin condiciones nuevas, firma la propuesta
   * pendiente del enemigo; con condiciones, hace una propuesta (o contrapropuesta).
   */
  public CompletableFuture<ClanWar> peace(Player actor, Clan clan, String terms) {
    return manager.main(
        () -> {
          manager.requireRank(clan, actor);
          manager.requirePermission(clan, actor, "guerra");
          ClanWar war = active(clan.id()).orElseThrow(() -> new ProtectionException("Tu clan no está en guerra."));
          String clean = manager.cleanText(terms);
          UUID enemy = war.enemyOf(clan.id());
          boolean theirs = enemy.equals(war.peaceProposedBy());
          if (theirs && (clean.isEmpty() || clean.equalsIgnoreCase(Objects.requireNonNullElse(war.peaceTerms(), "")))) {
            ClanWar ended =
                end(war, ClanWar.Ending.PEACE, null, war.peaceTerms(), 0, "Tratado de paz firmado" + (war.peaceTerms() == null ? "." : ": «" + war.peaceTerms() + "»."));
            return finish(
                ended,
                ClanMessages.paint(war.attackerName(), color(war.attacker()))
                    + " {texto}y "
                    + ClanMessages.paint(war.defenderName(), color(war.defender()))
                    + " {texto}firman la paz."
                    + (war.peaceTerms() == null ? "" : " Condiciones: {dato}«" + war.peaceTerms() + "»{texto}."));
          }
          List<String> chronicle = new ArrayList<>(war.chronicle());
          chronicle.add(stamp(System.currentTimeMillis()) + " " + clan.name() + " propone la paz" + (clean.isEmpty() ? "." : ": «" + clean + "»."));
          ClanWar proposed =
              new ClanWar(war.id(), war.attacker(), war.defender(), war.attackerName(), war.defenderName(), war.reason(),
                  war.declaredAt(), war.startsAt(), 0, null, null, war.attackerKills(), war.defenderKills(), war.terms(),
                  war.tribute(), war.reward(), clan.id(), clean.isEmpty() ? null : clean, trim(chronicle));
          return save(proposed)
              .thenApply(
                  saved -> {
                    manager.notice(clan, "{texto}Habéis propuesto la paz a " + ClanMessages.paint(war.nameOf(enemy), color(enemy)) + (clean.isEmpty() ? "{texto}." : "{texto}: {dato}«" + clean + "»{texto}."));
                    manager.getClan(enemy).ifPresent(
                        other -> {
                          manager.notice(other, ClanMessages.paint(clan.name(), clan.color()) + " {texto}propone la paz" + (clean.isEmpty() ? "." : ": {dato}«" + clean + "»{texto}."));
                          for (UUID member : other.members().keySet()) {
                            Player online = Bukkit.getPlayer(member);
                            if (online != null && manager.allows(other, member, "guerra"))
                              messages.components(online, true,
                                  messages.component("{texto}Para aceptarla: "),
                                  messages.button("{acento}[✔ Firmar la paz]", "{texto}/clan guerra paz", "/clan guerra paz", true),
                                  messages.component(" "),
                                  messages.button("{suave}[Ver guerra]", "{texto}/clan guerra", "/clan guerra", true));
                          }
                        });
                    return saved;
                  });
        });
  }

  public CompletableFuture<ClanWar> adminEnd(Clan clan) {
    return manager.main(
        () -> {
          ClanWar war = active(clan.id()).orElseThrow(() -> new ProtectionException(clan.name() + " no está en guerra."));
          return finish(
              end(war, ClanWar.Ending.ADMIN, null, null, 0, "La administración cierra la guerra."),
              "{texto}La administración cierra la guerra entre "
                  + ClanMessages.paint(war.attackerName(), color(war.attacker()))
                  + " {texto}y "
                  + ClanMessages.paint(war.defenderName(), color(war.defender()))
                  + "{texto}.");
        });
  }

  void disbanded(Clan clan) {
    active(clan.id())
        .ifPresent(
            war ->
                finish(
                    end(war, ClanWar.Ending.DISBANDED, null, null, 0, clan.name() + " se disuelve."),
                    "{texto}La guerra entre "
                        + ClanMessages.paint(war.attackerName(), color(war.attacker()))
                        + " {texto}y "
                        + ClanMessages.paint(war.defenderName(), color(war.defender()))
                        + " {texto}termina: "
                        + ClanMessages.paint(clan.name(), clan.color())
                        + " {texto}se ha disuelto."));
  }

  CompletableFuture<ClanWar> reward(UUID id, String reward) {
    ClanWar war = wars.get(id);
    if (war == null) throw new ProtectionException("Esa guerra no existe.");
    List<String> chronicle = new ArrayList<>(war.chronicle());
    chronicle.add(stamp(System.currentTimeMillis()) + " Recompensa: " + reward + ".");
    return save(
        new ClanWar(war.id(), war.attacker(), war.defender(), war.attackerName(), war.defenderName(), war.reason(),
            war.declaredAt(), war.startsAt(), war.endedAt(), war.ending(), war.winner(), war.attackerKills(),
            war.defenderKills(), war.terms(), war.tribute(), reward, war.peaceProposedBy(), war.peaceTerms(), trim(chronicle)));
  }

  /** Baja en una guerra: se anota en la crónica y se avisa a los dos clanes. */
  public void kill(Player killer, Player victim) {
    ClanWar war = fight(killer, victim);
    if (war == null) return;
    UUID side =
        manager.getPlayerClans(killer.getUniqueId()).stream()
            .map(Clan::id)
            .filter(war::involves)
            .findFirst()
            .orElse(null);
    if (side == null) return;
    boolean attacker = side.equals(war.attacker());
    List<String> chronicle = new ArrayList<>(war.chronicle());
    chronicle.add(stamp(System.currentTimeMillis()) + " " + killer.getName() + " (" + war.nameOf(side) + ") abate a " + victim.getName() + ".");
    ClanWar next =
        new ClanWar(war.id(), war.attacker(), war.defender(), war.attackerName(), war.defenderName(), war.reason(),
            war.declaredAt(), war.startsAt(), 0, null, null, war.attackerKills() + (attacker ? 1 : 0),
            war.defenderKills() + (attacker ? 0 : 1), war.terms(), war.tribute(), war.reward(), war.peaceProposedBy(),
            war.peaceTerms(), trim(chronicle));
    save(next);
    String text =
        "{guerra}⚔ {dato}" + killer.getName() + " {texto}ha abatido a {dato}" + victim.getName()
            + "{texto}. Bajas: " + ClanMessages.paint(next.attackerName(), color(next.attacker())) + " {dato}" + next.attackerKills()
            + " {suave}· " + ClanMessages.paint(next.defenderName(), color(next.defender())) + " {dato}" + next.defenderKills();
    manager.getClan(war.attacker()).ifPresent(c -> manager.notice(c, text));
    manager.getClan(war.defender()).ifPresent(c -> manager.notice(c, text));
  }

  void tick(long now) {
    for (ClanWar war : List.copyOf(wars.values())) {
      if (!war.active()) continue;
      if (now >= war.declaredAt() + settings.warMaxDays() * DAY) {
        finish(
            end(war, ClanWar.Ending.EXPIRED, null, null, 0, "Pasan " + settings.warMaxDays() + " días sin final: la guerra acaba sin vencedor."),
            "{texto}La guerra entre "
                + ClanMessages.paint(war.attackerName(), color(war.attacker()))
                + " {texto}y "
                + ClanMessages.paint(war.defenderName(), color(war.defender()))
                + " {texto}acaba sin vencedor tras {dato}" + settings.warMaxDays() + " días{texto}.");
        continue;
      }
      if (now >= war.startsAt() && started.add(war.id())) {
        messages.broadcast(
            ClanMessages.Theme.WAR,
            "{acento}¡Empieza la guerra! {texto}Desde ahora "
                + ClanMessages.paint(war.attackerName(), color(war.attacker()))
                + " {texto}y "
                + ClanMessages.paint(war.defenderName(), color(war.defender()))
                + " {texto}pueden luchar en cualquier parte.");
        Bukkit.getPluginManager().callEvent(new ClanWarEvent(war, ClanWarEvent.Type.START));
      }
    }
  }

  // ------------------------------------------------------------------ utilidades

  private ClanWar end(ClanWar war, ClanWar.Ending ending, UUID winner, String terms, int tribute, String line) {
    long now = System.currentTimeMillis();
    List<String> chronicle = new ArrayList<>(war.chronicle());
    chronicle.add(stamp(now) + " " + line);
    return new ClanWar(war.id(), war.attacker(), war.defender(), war.attackerName(), war.defenderName(), war.reason(),
        war.declaredAt(), war.startsAt(), now, ending, winner, war.attackerKills(), war.defenderKills(), terms, tribute,
        war.reward(), war.peaceProposedBy(), war.peaceTerms(), trim(chronicle));
  }

  private CompletableFuture<ClanWar> finish(ClanWar ended, String announcement) {
    return save(ended)
        .thenApply(
            saved -> {
              messages.broadcast(ClanMessages.Theme.WAR, announcement);
              Bukkit.getPluginManager().callEvent(new ClanWarEvent(saved, ClanWarEvent.Type.END));
              return saved;
            });
  }

  private CompletableFuture<ClanWar> save(ClanWar war) {
    ClanWar previous = wars.put(war.id(), war);
    CompletableFuture<ClanWar> result = new CompletableFuture<>();
    store.saveWar(war)
        .whenComplete(
            (ignored, failure) -> {
              if (!plugin.isEnabled()) {
                if (failure == null) result.complete(war);
                else result.completeExceptionally(failure);
                return;
              }
              Bukkit.getScheduler()
                  .runTask(
                      plugin,
                      () -> {
                        if (failure == null) {
                          result.complete(war);
                          return;
                        }
                        if (previous == null) wars.remove(war.id());
                        else wars.put(war.id(), previous);
                        plugin.getLogger().log(Level.SEVERE, "No se pudo guardar la guerra " + war.id() + ".", failure);
                        result.completeExceptionally(new ProtectionException("No se pudo guardar la guerra. Inténtalo de nuevo."));
                      });
            });
    return result;
  }

  private String color(UUID clan) {
    return manager.getClan(clan).map(Clan::color).orElse("#D7D7E0");
  }

  private static List<String> trim(List<String> chronicle) {
    return chronicle.size() <= CHRONICLE ? chronicle : chronicle.subList(chronicle.size() - CHRONICLE, chronicle.size());
  }

  static String stamp(long time) {
    return new java.text.SimpleDateFormat("dd/MM HH:mm").format(new Date(time));
  }

  /** «2 días y 3 horas», «5 horas», «12 minutos». */
  public static String duration(long millis) {
    long minutes = Math.max(1, (millis + 59_999L) / 60_000L), hours = minutes / 60, days = hours / 24;
    if (days > 0) return days + (days == 1 ? " día" : " días") + (hours % 24 > 0 ? " y " + hours % 24 + (hours % 24 == 1 ? " hora" : " horas") : "");
    if (hours > 0) return hours + (hours == 1 ? " hora" : " horas");
    return minutes + (minutes == 1 ? " minuto" : " minutos");
  }
}
