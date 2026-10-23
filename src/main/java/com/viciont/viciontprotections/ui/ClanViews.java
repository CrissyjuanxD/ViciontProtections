package com.viciont.viciontprotections.ui;

import com.google.gson.*;
import com.viciont.viciontprotections.api.Anchor;
import com.viciont.viciontprotections.api.clan.*;
import com.viciont.viciontprotections.clans.*;
import com.viciont.viciontprotections.models.ClanBackup;
import com.viciont.viciontprotections.models.ClanRequest;
import com.viciont.viciontprotections.models.Protection;
import java.text.SimpleDateFormat;
import java.util.*;
import net.md_5.bungee.api.chat.*;
import net.md_5.bungee.chat.ComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Menús de Viciont Clans. Cada vista se construye una vez como {@link Screen} y se muestra como
 * diálogo (1.21.6+) o como chat con botones. Los botones ejecutan los mismos comandos que se
 * pueden escribir, así que los permisos se comprueban de nuevo al pulsarlos. Solo aparecen los
 * botones que el rango del jugador permite.
 */
public final class ClanViews {
  /** Marca de los comandos lanzados desde un menú: al terminar, el menú se vuelve a abrir. */
  public static final String FROM_DIALOG = ProtectionDialogs.FROM_DIALOG;

  private static final int PAGE = 10;
  private static final String D = " " + FROM_DIALOG;

  private final ClanManager manager;
  private final ClanMessages messages;
  private final ClanSettings settings;
  private final ProtectionDialogs dialogs;
  private ClanFounding founding;

  public ClanViews(ClanManager manager, ProtectionDialogs dialogs) {
    this.manager = manager;
    this.messages = manager.messages();
    this.settings = manager.settings();
    this.dialogs = dialogs;
  }

  public void connect(ClanFounding founding) {
    this.founding = founding;
  }

  // ================================================================== sin clan, guía y listas

  /** Menú del clan activo, o la portada de clanes para quien no tiene ninguno. */
  public void open(Player player, String notice) {
    var clan = manager.getPlayerClan(player.getUniqueId());
    if (clan.isPresent()) menu(player, clan.get(), notice);
    else noClan(player, notice);
  }

  public void noClan(Player player, String notice) {
    Screen screen = new Screen("Clanes").notice(notice);
    screen.lines(settings.guideIntro());
    List<ClanRequest> invites = manager.invitations(player.getUniqueId());
    if (founding != null && founding.draft(player.getUniqueId()).filter(d -> d.clan() == null).isPresent())
      screen.button("{acento}⚑ {texto}Fundar mi clan", "{suave}Elige nombre y color o recupera uno anterior", "/clan fundar");
    for (ClanBackup copy : manager.backupsOf(player.getUniqueId()))
      screen.line("{titulo}↺ {texto}Puedes recuperar " + paint(copy.clan()) + " {texto}colocando un bloque de clan durante {dato}"
          + ClanWars.duration(copy.dissolvedAt() + settings.backupMillis() - System.currentTimeMillis()) + "{texto}.");
    if (invites.isEmpty())
      screen.line("{suave}No perteneces a ningún clan. Funda uno con un {dato}bloque de clan {suave}o pide que te inviten.");
    else screen.line("{texto}Tienes {dato}" + invites.size() + (invites.size() == 1 ? " invitación" : " invitaciones") + "{texto}:");
    for (ClanRequest invite : invites)
      manager.getClan(invite.clan())
          .ifPresent(
              clan ->
                  screen.button(
                      "{acento}✉ " + paint(clan),
                      "{texto}" + ClanManager.name(invite.createdBy() == null ? clan.leader() : invite.createdBy())
                          + " te invita como " + settings.rankName(invite.rank()),
                      "/clan invitacion @" + clan.id()));
    screen.button("{titulo}☰ {texto}Clanes del servidor", "{suave}Lista de clanes", "/clan lista");
    screen.button("{titulo}۞ {texto}Guía de protecciones", "{suave}Tus protecciones personales", "/pr guia");
    if (hasAdmin(player)) screen.button("{admin}✦ {texto}Administración", "{suave}Gestionar cualquier clan", "/clan admin");
    screen.exit("{titulo}۞ {texto}Guía de clanes", "/clan guia");
    show(player, screen);
  }

  /** Portada de la guía con un botón por tema, o la página de un tema. */
  public void guide(CommandSender sender, String topic) {
    List<ClanSettings.Topic> topics = new ArrayList<>(settings.topics());
    topics.addAll(manager.extensionTopics());
    if (topic == null || topic.isBlank()) {
      Screen screen = new Screen("Guía de clanes");
      for (String line : settings.guideIntro()) screen.line(settings.guideValues(line));
      for (ClanSettings.Topic t : topics)
        screen.button("{titulo}" + t.icon() + " {texto}" + t.title(), null, "/clan guia " + t.key());
      screen.button("{titulo}☰ {texto}Comandos", "{suave}Todo lo que puedes escribir", "/clan guia comandos");
      screen.button("{titulo}۞ {texto}Protecciones", "{suave}Guía de las protecciones personales", "/pr guia");
      screen.exit("{titulo}◀ {texto}Menú del clan", "/clan");
      show(sender, screen);
      return;
    }
    if (topic.equalsIgnoreCase("comandos")) {
      Screen screen = new Screen("Guía · Comandos");
      for (String[] command : commands(sender)) screen.line("{dato}" + command[0] + " {suave}» {texto}" + command[1]);
      screen.button("{titulo}◀ {texto}Temas de la guía", null, "/clan guia");
      screen.exit("{titulo}۞ {texto}Menú del clan", "/clan");
      show(sender, screen);
      return;
    }
    int index = -1;
    for (int i = 0; i < topics.size(); i++) if (topics.get(i).key().equalsIgnoreCase(topic)) index = i;
    if (index < 0) {
      guide(sender, null);
      return;
    }
    ClanSettings.Topic page = topics.get(index);
    Screen screen = new Screen("Guía · " + page.title());
    for (String line : page.lines()) screen.line(settings.guideValues(line));
    if (index > 0) screen.button("{titulo}◀ {texto}" + topics.get(index - 1).title(), null, "/clan guia " + topics.get(index - 1).key());
    if (index + 1 < topics.size())
      screen.button("{texto}" + topics.get(index + 1).title() + " {titulo}▶", null, "/clan guia " + topics.get(index + 1).key());
    screen.button("{titulo}☰ {texto}Temas de la guía", null, "/clan guia");
    screen.exit("{titulo}۞ {texto}Menú del clan", "/clan");
    show(sender, screen);
  }

  /** Comandos con su descripción, solo los que el jugador puede usar. */
  public List<String[]> commands(CommandSender sender) {
    List<String[]> list = new ArrayList<>();
    list.add(new String[] {"/clan", "menú de tu clan"});
    list.add(new String[] {"/clan guia", "esta guía"});
    list.add(new String[] {"/clan lista · /clan info [clan]", "clanes del servidor y su ficha"});
    list.add(new String[] {"/clan aceptar <clan> · rechazar <clan>", "responder a una invitación"});
    list.add(new String[] {"/cc [mensaje]", "chat del clan; sin texto activa el modo clan"});
    list.add(new String[] {"/clan miembros", "miembros por rango"});
    list.add(new String[] {"/clan invitar <jugador> [rango]", "invitar (oficial o superior)"});
    list.add(new String[] {"/clan expulsar <jugador>", "expulsar a un rango inferior"});
    list.add(new String[] {"/clan rango <jugador> <rango>", "ascender o degradar"});
    list.add(new String[] {"/clan territorio aportar", "pasar al clan la protección donde estás"});
    list.add(new String[] {"/clan solicitudes · territorios", "ofertas de territorio y zonas del clan"});
    list.add(new String[] {"/clan limites", "mostrar el borde del territorio"});
    list.add(new String[] {"/clan permisos", "casillas de cada rango (líder)"});
    list.add(new String[] {"/clan nombre · color · bandera", "ajustes del clan"});
    list.add(new String[] {"/clan guerra declarar <clan> <motivo>", "declarar la guerra (líder)"});
    list.add(new String[] {"/clan guerra rendirse [tributo] [condiciones]", "rendirse; el otro bando gana"});
    list.add(new String[] {"/clan guerra paz [condiciones]", "proponer o firmar la paz"});
    list.add(new String[] {"/clan activo <clan>", "clan que muestras si estás en varios"});
    list.add(new String[] {"/clan transferir <jugador> · disolver", "liderazgo y disolución (líder)"});
    list.add(new String[] {"/clan salir", "dejar el clan"});
    if (sender.hasPermission("viciontprotections.admin.clan.spy"))
      list.add(new String[] {"/clan espiar", "leer todos los chats de clan"});
    if (sender.hasPermission("viciontprotections.admin.clan.manage"))
      list.add(new String[] {"/clan admin <clan> …", "gestionar cualquier clan"});
    if (sender.hasPermission("viciontprotections.admin.clan.war"))
      list.add(new String[] {"/clan admin guerra terminar <clan>", "cerrar una guerra"});
    return list;
  }

  public void list(CommandSender sender, int page) {
    List<Clan> clans = manager.getClans();
    int pages = Math.max(1, (clans.size() + PAGE - 1) / PAGE);
    page = Math.max(1, Math.min(page, pages));
    Screen screen = new Screen("Clanes del servidor" + (pages > 1 ? " · " + page + "/" + pages : ""));
    if (clans.isEmpty()) screen.line("{suave}Todavía no hay clanes. Funda el primero con un bloque de clan.");
    else
      screen.line("{texto}Hay {dato}" + clans.size() + (clans.size() == 1 ? " {texto}clan." : " {texto}clanes.") + " {suave}Pulsa uno para ver su ficha.");
    for (Clan clan : clans.subList((page - 1) * PAGE, Math.min(page * PAGE, clans.size())))
      screen.button(
          paint(clan),
          "{texto}Líder: {dato}" + ClanManager.name(clan.leader()) + "\n{texto}Miembros: {dato}" + clan.members().size()
              + "\n{texto}Territorios: {dato}" + clan.territories().size()
              + manager.wars().active(clan.id()).map(w -> "\n{guerra}⚔ En guerra con " + w.nameOf(w.enemyOf(clan.id()))).orElse(""),
          "/clan info @" + clan.id());
    if (page > 1) screen.button("{titulo}◀ {texto}Anterior", null, "/clan lista " + (page - 1));
    if (page < pages) screen.button("{texto}Siguiente {titulo}▶", null, "/clan lista " + (page + 1));
    screen.exit("{titulo}۞ {texto}Guía de clanes", "/clan guia");
    show(sender, screen);
  }

  /** Ficha pública de un clan con su crónica de guerras. */
  public void info(CommandSender sender, Clan clan) {
    Screen screen = new Screen("&" + clan.color() + clan.name()).plainTitle();
    screen.line("{texto}Líder: {dato}" + ClanManager.name(clan.leader()) + " {suave}· Color: " + paint(settings.colorName(clan.color()), clan.color()));
    screen.line("{texto}Miembros: {dato}" + clan.members().size() + " {suave}(" + rankCounts(clan) + ")");
    screen.line("{texto}Territorios: {dato}" + clan.territories().size() + " {suave}· Fundado el {dato}" + date(clan.foundedAt()));
    long now = System.currentTimeMillis();
    manager.wars().active(clan.id()).ifPresent(war -> screen.line(warStatus(clan, war, now)));
    List<ClanWar> history = manager.wars().history(clan.id()).stream().filter(w -> !w.active()).limit(5).toList();
    if (!history.isEmpty()) {
      screen.line("{titulo}Crónica de guerras:");
      for (ClanWar war : history) screen.line(" {suave}• " + summary(clan, war));
    }
    if (sender instanceof Player player && clan.isMember(player.getUniqueId()))
      screen.button("{titulo}۞ {texto}Menú del clan", null, "/clan");
    screen.button("{titulo}☰ {texto}Clanes del servidor", null, "/clan lista");
    screen.exit("{texto}Cerrar", null);
    show(sender, screen);
  }

  // ================================================================== menú del clan

  public void menu(Player player, Clan clan, String notice) {
    UUID id = player.getUniqueId();
    ClanRank rank = clan.rank(id).orElse(null);
    if (rank == null) {
      info(player, clan);
      return;
    }
    Screen screen = new Screen("&" + clan.color() + "۞ " + clan.name()).plainTitle().notice(notice);
    screen.line("{texto}Líder: {dato}" + ClanManager.name(clan.leader()) + " {suave}· Tu rango: {dato}" + settings.rankName(rank));
    screen.line("{texto}Miembros: {dato}" + clan.members().size() + " {suave}(" + rankCounts(clan) + ") {suave}· Territorios: {dato}" + clan.territories().size());
    long now = System.currentTimeMillis();
    screen.line(manager.wars().active(clan.id()).map(war -> warStatus(clan, war, now)).orElse("{suave}Sin guerras en curso."));
    for (String line : manager.statusLines(clan, player)) screen.line(line);
    List<Clan> all = manager.getPlayerClans(id);
    if (all.size() > 1)
      screen.line("{suave}Perteneces a " + all.size() + " clanes; este es el que muestras. Cambia con {dato}/clan activo <clan>{suave}.");
    screen.button("{titulo}✎ {texto}Chat del clan", "{suave}Últimos mensajes y escribir", "/clan chat");
    screen.button("{titulo}☰ {texto}Miembros", "{suave}Por rango; pulsa uno para gestionarlo", "/clan miembros");
    if (manager.allows(clan, rank, "invitar"))
      screen.button("{acento}✉ {texto}Invitar", "{suave}Con un rango inferior al tuyo", "/clan formulario invitar");
    screen.button("{titulo}⌂ {texto}Territorios", "{suave}Zonas del clan", "/clan territorios");
    if (manager.allows(clan, rank, "aportar"))
      screen.button("{acento}⌂ {texto}Aportar protección", "{suave}La protección donde estás pasa al clan", "/clan territorio aportar");
    if (manager.allows(clan, rank, "territorios")) {
      int pending = manager.territoryRequests(clan.id()).size();
      screen.button("{titulo}✉ {texto}Solicitudes" + (pending > 0 ? " {acento}(" + pending + ")" : ""), "{suave}Protecciones ofrecidas al clan", "/clan solicitudes");
    }
    if (manager.allows(clan, rank, "limites"))
      screen.button("{titulo}◈ {texto}Mostrar límites", "{suave}Borde del territorio con partículas", "/clan limites" + D);
    screen.button("{guerra}⚔ {texto}Guerras", "{suave}Guerra en curso y crónica", "/clan guerra");
    if (rank == ClanRank.LEADER)
      screen.button("{titulo}✔ {texto}Permisos", "{suave}Lo que puede hacer cada rango", "/clan permisos");
    if (rank == ClanRank.LEADER || manager.allows(clan, rank, "ajustes") || manager.allows(clan, rank, "banderas"))
      screen.button("{titulo}⚙ {texto}Ajustes", "{suave}Nombre, color y banderas", "/clan ajustes");
    for (ClanButton button : manager.buttons(clan, player))
      screen.button(button.label(), button.description().isEmpty() ? null : "{suave}" + button.description(), command(button, clan, player));
    if (rank != ClanRank.LEADER) screen.button("{error}↩ {texto}Salir del clan", "{suave}Pide confirmación", "/clan salir");
    screen.button("{titulo}☰ {texto}Clanes del servidor", null, "/clan lista");
    if (hasAdmin(player)) screen.button("{admin}✦ {texto}Administración", "{suave}Gestionar cualquier clan", "/clan admin");
    screen.exit("{titulo}۞ {texto}Guía de clanes", "/clan guia");
    show(player, screen);
  }

  private static String command(ClanButton button, Clan clan, Player player) {
    return button.command()
        .replace("%clan%", clan.id().toString())
        .replace("%clan_nombre%", clan.name())
        .replace("%jugador%", player.getName());
  }

  public void chat(Player player, Clan clan, String notice) {
    Screen screen = new Screen("Chat · " + clan.name()).notice(notice);
    List<String> history = manager.chat().history(clan.id());
    if (history.isEmpty()) screen.line("{suave}Todavía no hay mensajes desde el último reinicio.");
    else history.forEach(screen::raw);
    screen.text("mensaje", "Mensaje", "", 256);
    screen.submit("{acento}✉ {texto}Enviar", "/clan chat $(mensaje)" + D, "/clan chat ");
    boolean mode = manager.chat().inClanMode(player);
    screen.button(
        mode ? "{acento}● {texto}Modo clan: activado" : "{suave}○ {texto}Modo clan: desactivado",
        "{suave}Con el modo clan, todo lo que escribes va al clan",
        "/clan modo" + D);
    screen.exit("{titulo}◀ {texto}Volver", "/clan");
    show(player, screen);
  }

  public void members(Player player, Clan clan, String notice) {
    Screen screen = new Screen("Miembros · " + clan.name()).notice(notice);
    UUID id = player.getUniqueId();
    for (ClanRank rank : reversed()) {
      List<ClanMember> list = clan.members(rank);
      if (list.isEmpty()) continue;
      screen.line("{titulo}" + settings.rankName(rank) + " {suave}(" + list.size() + "){texto}: {dato}"
          + String.join("{suave}, {dato}", list.stream().map(m -> ClanManager.name(m.player())).toList()));
    }
    screen.line("{suave}Pulsa un miembro para ver sus acciones.");
    int shown = 0;
    for (ClanRank rank : reversed())
      for (ClanMember member : clan.members(rank)) {
        if (shown++ >= 40) break;
        boolean online = Bukkit.getPlayer(member.player()) != null;
        screen.button(
            (online ? "{acento}● " : "{suave}○ ") + "{dato}" + ClanManager.name(member.player()) + " {suave}· " + settings.rankName(member.rank()),
            "{texto}En el clan desde el {dato}" + date(member.joinedAt()) + (online ? "\n{acento}Conectado" : ""),
            member.player().equals(id) ? "/clan miembros" : "/clan miembro " + member.player());
      }
    if (manager.allows(clan, id, "invitar")) screen.button("{acento}✉ {texto}Invitar", null, "/clan formulario invitar");
    screen.exit("{titulo}◀ {texto}Volver", "/clan");
    show(player, screen);
  }

  public void member(Player player, Clan clan, UUID target, String notice) {
    ClanMember member = clan.members().get(target);
    if (member == null) {
      members(player, clan, notice);
      return;
    }
    ClanRank own = clan.rank(player.getUniqueId()).orElse(ClanRank.GUEST);
    Screen screen = new Screen(ClanManager.name(target) + " · " + settings.rankName(member.rank())).notice(notice);
    screen.line("{texto}Rango: {dato}" + settings.rankName(member.rank()) + " {suave}· En el clan desde el {dato}" + date(member.joinedAt()));
    screen.line(Bukkit.getPlayer(target) != null ? "{acento}● Conectado" : "{suave}○ Desconectado");
    boolean above = own.isAbove(member.rank());
    if (above && manager.allows(clan, own, "rangos")) {
      member.rank().next().filter(own::isAbove)
          .ifPresent(next -> screen.button("{acento}▲ {texto}Ascender a " + settings.rankName(next), null, "/clan rango " + target + " " + next.key() + D));
      member.rank().previous()
          .ifPresent(previous -> screen.button("{suave}▼ {texto}Degradar a " + settings.rankName(previous), null, "/clan rango " + target + " " + previous.key() + D));
    }
    if (above && manager.allows(clan, own, "expulsar"))
      screen.button("{error}✖ {texto}Expulsar", "{suave}Sale del clan al momento", "/clan expulsar " + target + D);
    if (own == ClanRank.LEADER)
      screen.button("{admin}⇄ {texto}Hacer líder", "{suave}Pide confirmación; tú pasas a sublíder", "/clan transferir " + target);
    if (!screen.hasButtons()) screen.line("{suave}Tu rango no permite gestionar a este miembro.");
    screen.exit("{titulo}◀ {texto}Miembros", "/clan miembros");
    show(player, screen);
  }

  public void invite(Player player, Clan clan, String notice) {
    ClanRank own = clan.rank(player.getUniqueId()).orElse(ClanRank.GUEST);
    Screen screen = new Screen("Invitar · " + clan.name()).notice(notice);
    screen.line("{texto}La invitación caduca en {dato}" + settings.inviteMillis() / 60_000 + " minutos{texto}. Solo puedes invitar con un rango inferior al tuyo.");
    screen.text("jugador", "Jugador", "", 16);
    List<String[]> ranks = new ArrayList<>();
    ClanRank preferred = ClanRank.APPRENTICE;
    for (ClanRank rank : reversed()) if (own.isAbove(rank)) ranks.add(new String[] {rank.key(), settings.rankName(rank)});
    if (!own.isAbove(preferred)) preferred = ClanRank.GUEST;
    final ClanRank initial = preferred;
    ranks.sort(Comparator.comparing(r -> !r[0].equals(initial.key())));
    screen.options("rango", "Rango", ranks);
    screen.submit("{acento}✉ {texto}Invitar", "/clan invitar $(jugador) $(rango)" + D, "/clan invitar ");
    int quick = 0;
    for (Player online : Bukkit.getOnlinePlayers()) {
      if (quick >= 12 || !manager.getPlayerClans(online.getUniqueId()).isEmpty()) continue;
      quick++;
      screen.button("{acento}✉ {dato}" + online.getName(), "{texto}Invitar como " + settings.rankName(initial), "/clan invitar " + online.getName() + " " + initial.key() + D);
    }
    screen.exit("{titulo}◀ {texto}Volver", "/clan");
    show(player, screen);
  }

  /** Invitación recibida: diálogo de sí o no. */
  public void invitation(Player player, Clan clan, ClanRequest invite) {
    Screen screen = new Screen("Invitación al clan");
    screen.line("{dato}" + ClanManager.name(invite.createdBy() == null ? clan.leader() : invite.createdBy())
        + " {texto}te invita al clan " + paint(clan) + " {texto}como {dato}" + settings.rankName(invite.rank()) + "{texto}.");
    screen.line("{texto}Líder: {dato}" + ClanManager.name(clan.leader()) + " {suave}· Miembros: {dato}" + clan.members().size() + " {suave}· Territorios: {dato}" + clan.territories().size());
    screen.line("{suave}Caduca en " + ClanWars.duration(invite.expiresAt() - System.currentTimeMillis()) + ". Tus protecciones seguirán siendo privadas.");
    screen.confirm("{acento}✔ Aceptar", "/clan aceptar @" + clan.id(), "{error}✖ Rechazar", "/clan rechazar @" + clan.id());
    show(player, screen);
  }

  /** Aviso de chat al recibir una invitación (no abre ventanas en mitad del juego). */
  public void invitationNotice(Player player, Clan clan, ClanRequest invite) {
    messages.send(player, "{dato}" + ClanManager.name(invite.createdBy() == null ? clan.leader() : invite.createdBy())
        + " {texto}te invita al clan " + paint(clan) + " {texto}como {dato}" + settings.rankName(invite.rank()) + "{texto}.");
    messages.components(
        player,
        false,
        messages.button("{acento}[✔ Aceptar] ", "{texto}/clan aceptar " + clan.name(), "/clan aceptar @" + clan.id(), true),
        messages.button("{error}[✖ Rechazar] ", "{texto}/clan rechazar " + clan.name(), "/clan rechazar @" + clan.id(), true),
        messages.button("{titulo}[Ver invitación]", "{texto}Detalles del clan", "/clan invitacion @" + clan.id(), true));
  }

  // ================================================================== territorios

  public void territories(Player player, Clan clan, String notice) {
    Screen screen = new Screen("Territorios · " + clan.name()).notice(notice);
    boolean manage = manager.allows(clan, player.getUniqueId(), "territorios");
    List<ClanTerritory> list = new ArrayList<>(clan.territories().values());
    list.sort(Comparator.comparing((ClanTerritory t) -> !t.home()).thenComparingLong(ClanTerritory::contributedAt));
    for (ClanTerritory territory : list) {
      Protection p = manager.protections().getProtection(territory.protection()).orElse(null);
      if (p == null) continue;
      screen.line((territory.home() ? "{titulo}⚑ " : "{titulo}⌂ ") + "{dato}" + p.name()
          + (territory.home() ? " {suave}(principal)" : " {suave}· aportada por {dato}" + ClanManager.name(territory.contributor()))
          + " {suave}· " + ProtectionDialogs.size(p) + " · " + ProtectionDialogs.place(p));
      if (manage && !territory.home())
        screen.button("{error}↩ {texto}Devolver " + p.name(), "{suave}Vuelve a " + ClanManager.name(territory.contributor()) + " con su acceso anterior",
            "/clan territorio quitar @" + p.id() + D);
    }
    if (manager.allows(clan, player.getUniqueId(), "aportar"))
      screen.button("{acento}⌂ {texto}Aportar protección", "{suave}La protección donde estás", "/clan territorio aportar");
    if (manager.allows(clan, player.getUniqueId(), "limites"))
      screen.button("{titulo}◈ {texto}Mostrar límites", "{suave}Del territorio donde estás o del principal", "/clan limites" + D);
    screen.exit("{titulo}◀ {texto}Volver", "/clan");
    show(player, screen);
  }

  /** Confirmación antes de aportar: qué pasa al clan y quién pierde el acceso. */
  public void contribute(Player player, Clan clan, Protection protection) {
    Screen screen = new Screen("Aportar al clan");
    screen.line("{texto}Vas a aportar {dato}" + protection.name() + " {suave}(" + ProtectionDialogs.size(protection) + ") {texto}al clan " + paint(clan) + "{texto}.");
    screen.line("{texto}Será territorio del clan: sus rangos decidirán quién construye, abre cofres o usa puertas.");
    List<UUID> outsiders = manager.outsiders(clan, protection);
    if (outsiders.isEmpty()) screen.line("{suave}Nadie pierde el acceso: todos los que lo tenían son del clan.");
    else
      screen.line("{error}Perderán el acceso: {dato}" + String.join("{suave}, {dato}", outsiders.stream().map(ClanManager::name).toList())
          + "{error}. {suave}Invítalos al clan antes si quieres que sigan entrando.");
    boolean direct = manager.allows(clan, player.getUniqueId(), "territorios");
    screen.line(direct ? "{suave}Pasa al clan en cuanto confirmes." : "{suave}Tu rango envía una solicitud; la acepta el líder o un sublíder.");
    screen.line("{suave}Si el clan se disuelve o te la devuelve, vuelve a ser tuya con su acceso anterior.");
    screen.confirm(direct ? "{acento}✔ Aportar" : "{acento}✉ Enviar solicitud", "/clan territorio aportar confirmar" + D, "{texto}Cancelar", "/clan");
    show(player, screen);
  }

  public void requests(Player player, Clan clan, String notice) {
    Screen screen = new Screen("Solicitudes · " + clan.name()).notice(notice);
    List<ClanRequest> list = manager.territoryRequests(clan.id());
    if (list.isEmpty()) screen.line("{suave}No hay protecciones ofrecidas al clan.");
    long now = System.currentTimeMillis();
    for (ClanRequest request : list) {
      Protection p = manager.protections().getProtection(request.protection()).orElse(null);
      if (p == null) continue;
      screen.line("{dato}" + ClanManager.name(request.player()) + " {texto}ofrece {dato}" + p.name() + " {suave}· " + ProtectionDialogs.size(p) + " · "
          + ProtectionDialogs.place(p) + " {suave}· caduca en " + ClanWars.duration(request.expiresAt() - now));
      screen.button("{acento}✔ {texto}Aceptar " + p.name(), "{suave}De " + ClanManager.name(request.player()), "/clan territorio aceptar " + request.id() + D);
      screen.button("{error}✖ {texto}Rechazar " + p.name(), null, "/clan territorio rechazar " + request.id() + D);
    }
    screen.exit("{titulo}◀ {texto}Volver", "/clan");
    show(player, screen);
  }

  // ================================================================== permisos y ajustes

  public void permissions(Player player, Clan clan, ClanRank rank, String notice) {
    if (rank == null) {
      Screen screen = new Screen("Permisos · " + clan.name()).notice(notice);
      screen.line("{texto}Elige un rango para ver y cambiar sus casillas. El líder siempre las tiene todas.");
      for (ClanRank r : reversed()) {
        if (r == ClanRank.LEADER) continue;
        long allowed = manager.getPermissions().stream().filter(p -> manager.allows(clan, r, p.key())).count();
        screen.button("{titulo}♛ {texto}" + settings.rankName(r) + " {suave}(" + allowed + "/" + manager.getPermissions().size() + ")", null, "/clan permisos " + r.key());
      }
      screen.exit("{titulo}◀ {texto}Volver", "/clan");
      show(player, screen);
      return;
    }
    Screen screen = new Screen("Permisos · " + settings.rankName(rank)).notice(notice);
    screen.line("{texto}Pulsa una casilla para activarla {acento}✔ {texto}o quitarla {error}✖{texto}.");
    List<String> fixed = new ArrayList<>();
    for (ClanPermission permission : manager.getPermissions()) {
      boolean on = manager.allows(clan, rank, permission.key());
      if (settings.locked(permission.key())) {
        fixed.add((on ? "✔ " : "✖ ") + permission.name());
        continue;
      }
      screen.button((on ? "{acento}✔ {texto}" : "{error}✖ {suave}") + permission.name(),
          "{suave}" + permission.description(), "/clan permisos " + rank.key() + " " + permission.key() + D);
    }
    if (!fixed.isEmpty()) screen.line("{suave}Fijadas por el servidor: {dato}" + String.join("{suave}, {dato}", fixed));
    screen.exit("{titulo}◀ {texto}Rangos", "/clan permisos");
    show(player, screen);
  }

  public void settings(Player player, Clan clan, String notice) {
    UUID id = player.getUniqueId();
    Screen screen = new Screen("Ajustes · " + clan.name()).notice(notice);
    screen.line("{texto}Nombre: " + paint(clan) + " {suave}· Color: " + paint(settings.colorName(clan.color()), clan.color()));
    screen.line(clan.flags().isEmpty()
        ? "{suave}Banderas: las de cada territorio."
        : "{texto}Banderas: {dato}" + String.join("{suave}, {dato}", clan.flags().entrySet().stream().sorted(Map.Entry.comparingByKey())
            .map(e -> e.getKey() + "=" + (e.getValue().equals("ALLOW") ? "permitir" : "denegar")).toList()));
    if (manager.allows(clan, id, "ajustes")) {
      screen.button("{titulo}✎ {texto}Cambiar nombre", "{suave}De " + settings.nameMin() + " a " + settings.nameMax() + " caracteres", "/clan formulario nombre");
      screen.button("{titulo}◐ {texto}Cambiar color", "{suave}Para el chat, el nick y los avisos", "/clan formulario color");
    }
    if (manager.allows(clan, id, "banderas"))
      screen.button("{titulo}⚑ {texto}Banderas", "{suave}PvP, mobs, fuego… en todos los territorios", "/clan formulario bandera");
    if (clan.leader().equals(id)) {
      screen.button("{titulo}⚑ {texto}Cambiar bloque de clan", "{suave}Por uno más grande, sin disolver el clan", "/clan bloque");
      screen.button("{admin}⇄ {texto}Transferir liderazgo", "{suave}Tú pasas a sublíder", "/clan formulario transferir");
      screen.button("{error}✘ {texto}Disolver el clan", "{suave}Pide confirmación", "/clan disolver");
    }
    screen.exit("{titulo}◀ {texto}Volver", "/clan");
    show(player, screen);
  }

  /** Formularios: nombre, color, bandera, transferir, declarar, rendirse y paz. */
  public boolean form(Player player, Clan clan, String type, String notice) {
    Screen screen;
    String back = "/clan";
    switch (type) {
      case "invitar" -> {
        invite(player, clan, notice);
        return true;
      }
      case "nombre" -> {
        screen = new Screen("Cambiar nombre").notice(notice);
        screen.line("{texto}Nombre actual: " + paint(clan));
        screen.line("{suave}De " + settings.nameMin() + " a " + settings.nameMax() + " letras, números, espacios, puntos o guiones.");
        screen.text("nombre", "Nombre", clan.name(), settings.nameMax());
        screen.submit("{acento}✔ {texto}Guardar", "/clan nombre $(nombre)" + D, "/clan nombre ");
        back = "/clan ajustes";
      }
      case "color" -> {
        screen = new Screen("Cambiar color").notice(notice);
        screen.line("{texto}Color actual: " + paint(settings.colorName(clan.color()), clan.color()));
        screen.options("color", "Color", colorOptions(clan.color()));
        screen.submit("{acento}✔ {texto}Guardar", "/clan color $(color)" + D, "/clan color ");
        for (var entry : settings.colors().entrySet())
          screen.button(paint(settings.colorName(entry.getValue()), entry.getValue()), null, "/clan color " + entry.getKey() + D);
        back = "/clan ajustes";
      }
      case "bandera" -> {
        screen = new Screen("Banderas del clan").notice(notice);
        screen.line("{texto}Se aplican a todos los territorios del clan.");
        List<String> flags = settings.allowedFlags();
        if (flags.isEmpty()) {
          screen.line("{suave}El servidor no permite cambiar banderas.");
        } else {
          screen.options("bandera", "Bandera", flags.stream().map(f -> new String[] {f, f + (clan.flags().containsKey(f) ? " (" + (clan.flags().get(f).equals("ALLOW") ? "permitir" : "denegar") + ")" : "")}).toList());
          screen.options("valor", "Valor", List.of(new String[] {"permitir", "Permitir"}, new String[] {"denegar", "Denegar"}, new String[] {"restablecer", "Restablecer"}));
          screen.submit("{acento}⚑ {texto}Aplicar", "/clan bandera $(bandera) $(valor)" + D, "/clan bandera ");
        }
        back = "/clan ajustes";
      }
      case "transferir" -> {
        screen = new Screen("Transferir liderazgo").notice(notice);
        screen.line("{texto}El nuevo líder tendrá el control del clan; tú pasarás a {dato}" + settings.rankName(ClanRank.CO_LEADER) + "{texto}.");
        List<String[]> members = new ArrayList<>();
        for (ClanRank rank : reversed())
          for (ClanMember member : clan.members(rank))
            if (rank != ClanRank.LEADER) members.add(new String[] {member.player().toString(), ClanManager.name(member.player()) + " · " + settings.rankName(rank)});
        if (members.isEmpty()) screen.line("{suave}No hay más miembros en el clan.");
        else {
          screen.options("jugador", "Nuevo líder", members);
          screen.submit("{admin}⇄ {texto}Transferir", "/clan transferir $(jugador)", "/clan transferir ");
        }
        back = "/clan ajustes";
      }
      case "declarar" -> {
        screen = new Screen("Declarar la guerra").notice(notice);
        screen.line("{texto}El PvP entre los dos clanes empezará en {dato}" + settings.warPrepHours() + " horas{texto}. Termina cuando un líder se rinde o firmáis la paz.");
        List<String[]> rivals = new ArrayList<>();
        for (Clan other : manager.getClans())
          if (!other.id().equals(clan.id()) && manager.wars().active(other.id()).isEmpty())
            rivals.add(new String[] {"@" + other.id(), other.name()});
        if (rivals.isEmpty()) screen.line("{suave}No hay clanes disponibles para declarar la guerra.");
        else {
          screen.options("clan", "Clan enemigo", rivals);
          screen.text("motivo", "Motivo (se anuncia a todo el servidor)", "", settings.maxText());
          screen.submit("{guerra}⚔ {texto}Declarar", "/clan guerra declarar $(clan) $(motivo)" + D, "/clan guerra declarar ");
        }
        back = "/clan guerra";
      }
      case "rendirse" -> {
        screen = new Screen("Rendirse").notice(notice);
        screen.line("{texto}El otro bando gana la guerra. Tu clan paga la recompensa mínima del servidor más el tributo que ofrezcas.");
        screen.text("tributo", "Tributo adicional", "0", 6);
        screen.text("condiciones", "Condiciones (opcional)", "", settings.maxText());
        screen.submit("{error}⚐ {texto}Rendirse", "/clan guerra rendirse $(tributo) $(condiciones)" + D, "/clan guerra rendirse ");
        back = "/clan guerra";
      }
      case "paz" -> {
        screen = new Screen("Proponer la paz").notice(notice);
        screen.line("{texto}El otro líder puede firmarla tal cual o proponer otras condiciones. Un tratado de paz no obliga a pagar.");
        screen.text("condiciones", "Condiciones (opcional)", "", settings.maxText());
        screen.submit("{acento}✉ {texto}Proponer", "/clan guerra paz $(condiciones)" + D, "/clan guerra paz ");
        back = "/clan guerra";
      }
      default -> {
        return false;
      }
    }
    screen.exit("{titulo}◀ {texto}Volver", back);
    show(player, screen);
    return true;
  }

  private List<String[]> colorOptions(String current) {
    List<String[]> options = new ArrayList<>();
    for (var entry : settings.colors().entrySet())
      options.add(new String[] {entry.getKey(), "&" + entry.getValue() + settings.colorName(entry.getValue())});
    options.sort(Comparator.comparing(o -> !settings.colors().get(o[0]).equalsIgnoreCase(current)));
    return options;
  }

  // ================================================================== fundar

  /** Tras colocar un bloque de clan quien tiene clanes disueltos con copia: fundar o recuperar. */
  public void foundChoice(Player player, String notice) {
    Screen screen = new Screen("Bloque de clan").notice(notice);
    screen.line("{texto}¿Quieres {dato}fundar un clan nuevo {texto}o {dato}recuperar uno que disolviste{texto}?");
    long now = System.currentTimeMillis();
    for (ClanBackup copy : manager.backupsOf(player.getUniqueId())) {
      Clan clan = copy.clan();
      String left = ClanWars.duration(copy.dissolvedAt() + settings.backupMillis() - now);
      screen.line("{titulo}↺ " + paint(clan) + " {suave}· disuelto el {dato}" + date(copy.dissolvedAt())
          + (copy.reason().isBlank() ? "" : " {suave}(" + copy.reason() + ")") + " {suave}· " + clan.members().size()
          + " miembros · la copia dura " + left);
      screen.button("{acento}↺ {texto}Recuperar " + paint(clan),
          "{texto}Vuelven el nombre, el color, los rangos, los permisos y la historia de guerras.\n{texto}El territorio principal será el de este bloque"
              + (copy.homeSize().isBlank() ? "" : " {suave}(antes " + copy.homeSize() + ")") + "{texto}.",
          "/clan fundar recuperar @" + clan.id() + D);
    }
    screen.line("{suave}Al recuperarlo vuelven los miembros que no se hayan unido a otro clan. Las protecciones aportadas ya volvieron a sus dueños: pueden aportarlas otra vez.");
    screen.button("{titulo}⚑ {texto}Fundar un clan nuevo", "{suave}Con otro nombre y color", "/clan fundar nuevo");
    screen.button("{error}✖ {texto}Recuperar el bloque", "{suave}Lo devuelve a tu inventario", "/clan fundar cancelar");
    screen.exit("{suave}Más tarde", null);
    show(player, screen);
  }

  public void found(Player player, String notice) {
    var draft = founding.draft(player.getUniqueId()).orElse(null);
    if (draft == null) {
      noClan(player, notice);
      return;
    }
    Screen screen = new Screen("Fundar un clan").notice(notice);
    screen.line("{texto}Elige el nombre y el color de tu clan. Serás su {dato}líder {texto}y la zona del bloque será su {dato}territorio principal{texto}.");
    screen.line("{suave}Territorio: {dato}" + draft.spec().dimensions());
    for (String line : founding.requirements(player)) screen.line("{acento}✦ {texto}" + line);
    screen.line("{suave}Tienes " + ClanWars.duration(draft.expiresAt() - System.currentTimeMillis()) + "; si no, el bloque vuelve a tu inventario.");
    screen.text("nombre", "Nombre del clan (" + settings.nameMin() + "-" + settings.nameMax() + ")", "", settings.nameMax());
    screen.options("color", "Color", colorOptions(""));
    screen.submit("{acento}⚑ {texto}Fundar clan", "/clan fundar $(color) $(nombre)" + D, "/clan fundar " + settings.colors().keySet().iterator().next() + " ");
    screen.button("{error}✖ {texto}Recuperar el bloque", "{suave}Cancela la fundación", "/clan fundar cancelar");
    screen.exit("{suave}Más tarde", null);
    show(player, screen);
  }

  /** Cómo cambiar el bloque de clan, para el líder. */
  public void blockInfo(Player player, Clan clan) {
    Protection home = clan.home() == null ? null : manager.protections().getProtection(clan.home()).orElse(null);
    Screen screen = new Screen("Cambiar bloque de clan");
    if (home != null)
      screen.line("{texto}Bloque actual: {dato}" + (home.anchor() == null ? "sin bloque" : ProtectionDialogs.pretty(home.anchor().block().material()))
          + " {suave}· " + ProtectionDialogs.size(home) + " · " + ProtectionDialogs.place(home));
    screen.line("{acento}1. {texto}Consigue el nuevo bloque de clan, por ejemplo uno de {dato}300 × 300{texto}.");
    screen.line("{acento}2. {texto}Colócalo en el mismo mundo, por ejemplo al lado del actual. Puede estar dentro del territorio.");
    screen.line("{acento}3. {texto}Elige el centro: {dato}en el mismo sitio {texto}(el bloque nuevo ocupa el lugar del actual y la zona crece a su alrededor) o {dato}donde pusiste el nuevo{texto}.");
    screen.line("{texto}El territorio pasa a medir lo del bloque nuevo y el bloque actual vuelve a tu inventario.");
    screen.line("{suave}El clan no se disuelve: rangos, chat, guerras, banco y protecciones aportadas siguen igual.");
    screen.line("{error}No rompas el bloque actual: romperlo disuelve el clan.");
    screen.exit("{titulo}◀ {texto}Ajustes", "/clan ajustes");
    show(player, screen);
  }

  /** Confirmación tras colocar el nuevo bloque de clan: centro en el sitio del actual o en el nuevo. */
  public void confirmReplace(Player player, Clan clan, ClanFounding.Draft draft) {
    confirmReplace(player, clan, draft, null);
  }

  public void confirmReplace(Player player, Clan clan, ClanFounding.Draft draft, String notice) {
    Protection home = clan.home() == null ? null : manager.protections().getProtection(clan.home()).orElse(null);
    Anchor current = home == null ? null : home.anchor();
    Screen screen = new Screen("Cambiar bloque de clan").notice(notice);
    screen.line("{texto}¿Cambiar el bloque de clan de " + paint(clan) + "{texto}?");
    if (home != null)
      screen.line("{suave}Ahora: {dato}" + ProtectionDialogs.size(home) + " {suave}· " + ProtectionDialogs.place(home));
    screen.line("{suave}Nuevo: {dato}" + draft.spec().dimensions());
    screen.line("{texto}Elige dónde queda el {dato}centro {texto}del territorio principal. El bloque actual vuelve a tu inventario; el clan, sus rangos y su banco no cambian.");
    if (current != null)
      screen.button(
          "{acento}✔ {texto}En el mismo sitio {suave}(" + current.x() + ", " + current.y() + ", " + current.z() + ")",
          "{texto}El bloque nuevo ocupa el lugar del actual y la zona crece a su alrededor.\n{suave}El bloque que acabas de colocar se retira.",
          "/clan bloque mantener" + D);
    screen.button(
        "{acento}✔ {texto}Donde está el bloque nuevo {suave}(" + draft.block().getBlockX() + ", " + draft.block().getBlockY() + ", "
            + draft.block().getBlockZ() + ")",
        "{texto}La zona se centra en el bloque nuevo.\n{error}Lo que quede fuera deja de estar protegido.",
        "/clan bloque confirmar" + D);
    screen.exit("{texto}Cancelar", "/clan bloque cancelar");
    show(player, screen);
  }

  // ================================================================== guerras

  public void war(Player player, Clan clan, String notice) {
    Screen screen = new Screen("Guerras · " + clan.name()).notice(notice);
    boolean leads = manager.allows(clan, player.getUniqueId(), "guerra");
    long now = System.currentTimeMillis();
    ClanWar war = manager.wars().active(clan.id()).orElse(null);
    if (war != null) {
      UUID enemy = war.enemyOf(clan.id());
      screen.line(warStatus(clan, war, now));
      screen.line("{texto}Motivo: {dato}«" + war.reason() + "» {suave}· declarada por {dato}" + war.attackerName() + " {suave}el " + date(war.declaredAt()));
      screen.line("{texto}Bajas: {dato}" + clan.name() + " " + war.killsOf(clan.id()) + " {suave}· {dato}" + war.nameOf(enemy) + " " + war.killsOf(enemy));
      screen.line("{suave}Sin final, acaba sin vencedor en " + ClanWars.duration(war.declaredAt() + settings.warMaxDays() * 86_400_000L - now) + ".");
      if (war.peaceProposedBy() != null)
        screen.line((war.peaceProposedBy().equals(clan.id()) ? "{texto}Habéis propuesto la paz" : "{acento}" + war.nameOf(enemy) + " propone la paz")
            + (war.peaceTerms() == null ? "{texto}." : "{texto}: {dato}«" + war.peaceTerms() + "»"));
      List<String> chronicle = war.chronicle();
      for (String line : chronicle.subList(Math.max(0, chronicle.size() - 5), chronicle.size())) screen.line(" {suave}• " + line);
      if (leads) {
        if (enemy.equals(war.peaceProposedBy()))
          screen.button("{acento}✔ {texto}Firmar la paz", "{suave}Con sus condiciones", "/clan guerra paz" + D);
        screen.button("{titulo}✉ {texto}Proponer la paz", null, "/clan formulario paz");
        screen.button("{error}⚐ {texto}Rendirse", "{suave}El otro bando gana y cobra la recompensa", "/clan formulario rendirse");
      }
    } else {
      screen.line("{suave}Tu clan no está en guerra.");
      screen.line("{texto}Las guerras son de rol: se declaran con un motivo, el PvP abre a las {dato}" + settings.warPrepHours()
          + " horas {texto}y terminan con una rendición o la paz.");
      if (leads && settings.warsEnabled()) screen.button("{guerra}⚔ {texto}Declarar la guerra", "{suave}Elige clan y motivo", "/clan formulario declarar");
    }
    List<ClanWar> history = manager.wars().history(clan.id()).stream().filter(w -> !w.active()).limit(5).toList();
    if (!history.isEmpty()) {
      screen.line("{titulo}Crónica:");
      for (ClanWar past : history) screen.line(" {suave}• " + summary(clan, past));
    }
    screen.exit("{titulo}◀ {texto}Volver", "/clan");
    show(player, screen);
  }

  private String warStatus(Clan clan, ClanWar war, long now) {
    UUID enemy = war.enemyOf(clan.id());
    String name = manager.getClan(enemy).map(ClanViews::paint).orElse("{dato}" + war.nameOf(enemy));
    return "{guerra}⚔ {texto}En guerra con " + name + "{texto}: "
        + (war.fighting(now) ? "{acento}PvP abierto" : "el PvP empieza en {dato}" + ClanWars.duration(war.startsAt() - now)) + "{texto}.";
  }

  private String summary(Clan clan, ClanWar war) {
    UUID enemy = war.enemyOf(clan.id());
    String result =
        switch (war.ending()) {
          case SURRENDER -> clan.id().equals(war.winner()) ? "{acento}victoria: " + war.nameOf(enemy) + " se rindió" : "{error}rendición";
          case PEACE -> "{texto}tratado de paz";
          case ADMIN -> "{suave}cerrada por la administración";
          case EXPIRED -> "{suave}sin vencedor";
          case DISBANDED -> "{suave}un clan se disolvió";
        };
    return "{texto}vs {dato}" + war.nameOf(enemy) + " {suave}(" + date(war.declaredAt()) + ", " + ClanWars.duration(war.endedAt() - war.declaredAt())
        + ") · bajas " + war.killsOf(clan.id()) + "-" + war.killsOf(enemy) + " · " + result
        + (war.reward() != null ? " {suave}· " + war.reward() : "");
  }

  // ================================================================== confirmaciones

  public void confirmDissolve(Player player, Clan clan) {
    Screen screen = new Screen("Disolver el clan");
    screen.line("{texto}¿Disolver " + paint(clan) + "{texto}?");
    screen.line("{error}El territorio principal deja de estar protegido. {texto}El bloque de clan vuelve a tu inventario.");
    screen.line("{texto}Las protecciones aportadas vuelven a quien las aportó. Todos los miembros quedan sin clan.");
    screen.line("{suave}Lo guardado por plugins conectados, como el saldo del banco, se pierde.");
    screen.confirm("{error}✘ Sí, disolver", "/clan disolver confirmar", "{texto}Cancelar", "/clan");
    show(player, screen);
  }

  public void confirmLeave(Player player, Clan clan) {
    Screen screen = new Screen("Salir del clan");
    screen.line("{texto}¿Salir de " + paint(clan) + "{texto}? Perderás el acceso a sus territorios.");
    screen.line("{suave}Tus protecciones personales siguen siendo tuyas; las que aportaste se quedan en el clan.");
    screen.confirm("{error}↩ Sí, salir", "/clan salir confirmar", "{texto}Cancelar", "/clan");
    show(player, screen);
  }

  public void confirmTransfer(Player player, Clan clan, UUID target) {
    Screen screen = new Screen("Transferir liderazgo");
    screen.line("{texto}¿Hacer líder de " + paint(clan) + " {texto}a {dato}" + ClanManager.name(target) + "{texto}?");
    screen.line("{texto}Tú pasarás a {dato}" + settings.rankName(ClanRank.CO_LEADER) + "{texto}. Solo el nuevo líder podrá devolvértelo.");
    screen.confirm("{admin}⇄ Sí, transferir", "/clan transferir " + target + " confirmar", "{texto}Cancelar", "/clan miembros");
    show(player, screen);
  }

  // ================================================================== administración

  public void admin(Player player, Clan clan, String notice) {
    if (clan == null) {
      Screen screen = new Screen("Administración de clanes").notice(notice);
      screen.line("{texto}Elige un clan para gestionarlo. Todo queda registrado en la consola.");
      for (Clan other : manager.getClans().stream().limit(40).toList())
        screen.button(paint(other), "{texto}" + other.members().size() + " miembros", "/clan admin @" + other.id());
      if (player.hasPermission("viciontprotections.admin.clan.spy"))
        screen.button("{admin}◉ {texto}Espiar chats de clan", "{suave}Activa o desactiva", "/clan espiar" + D);
      screen.exit("{titulo}۞ {texto}Guía de clanes", "/clan guia");
      show(player, screen);
      return;
    }
    String root = "/clan admin @" + clan.id() + " ";
    Screen screen = new Screen("Admin · " + clan.name()).notice(notice);
    screen.line("{texto}Miembros: {dato}" + clan.members().size() + " {suave}· Territorios: {dato}" + clan.territories().size()
        + " {suave}· Fundado el {dato}" + date(clan.foundedAt()));
    for (ClanRank rank : reversed()) {
      List<ClanMember> list = clan.members(rank);
      if (!list.isEmpty())
        screen.line("{suave}" + settings.rankName(rank) + ": {dato}" + String.join("{suave}, {dato}", list.stream().map(m -> ClanManager.name(m.player())).toList()));
    }
    List<String[]> ranks = new ArrayList<>();
    for (ClanRank rank : reversed()) if (rank != ClanRank.LEADER) ranks.add(new String[] {rank.key(), settings.rankName(rank)});
    Collections.reverse(ranks);
    screen.text("jugador", "Jugador", "", 16);
    screen.options("rango", "Rango", ranks);
    screen.submit("{admin}＋ {texto}Añadir (aunque esté en otro clan)", root + "añadir $(jugador) $(rango)" + D, root + "añadir ");
    screen.submit("{admin}♛ {texto}Cambiar rango", root + "rango $(jugador) $(rango)" + D, root + "rango ");
    screen.submit("{admin}⇄ {texto}Nombrar líder", root + "lider $(jugador)" + D, root + "lider ");
    screen.submit("{error}✖ {texto}Quitar del clan", root + "quitar $(jugador)" + D, root + "quitar ");
    if (manager.wars().active(clan.id()).isPresent() && player.hasPermission("viciontprotections.admin.clan.war"))
      screen.button("{guerra}⚔ {texto}Terminar su guerra", "{suave}Sin vencedor", "/clan admin guerra terminar @" + clan.id() + D);
    screen.button("{error}✘ {texto}Disolver", "{suave}Pide confirmación", root + "disolver");
    screen.exit("{titulo}◀ {texto}Clanes", "/clan admin");
    show(player, screen);
  }

  public void confirmAdminDissolve(Player player, Clan clan) {
    Screen screen = new Screen("Disolver (administración)");
    screen.line("{texto}¿Disolver " + paint(clan) + "{texto}? El bloque de clan no se devuelve a nadie.");
    screen.line("{texto}Las protecciones aportadas vuelven a sus dueños y los miembros quedan sin clan.");
    screen.confirm("{error}✘ Sí, disolver", "/clan admin @" + clan.id() + " disolver confirmar", "{texto}Cancelar", "/clan admin @" + clan.id());
    show(player, screen);
  }

  // ================================================================== render

  /** Muestra la pantalla como diálogo si el jugador puede verlos, o en el chat. */
  public void show(CommandSender sender, Screen screen) {
    if (sender instanceof Player player && dialogs.supported(player) && dialogs.showJson(player, dialog(screen))) return;
    chat(sender, screen);
  }

  private JsonObject dialog(Screen screen) {
    var dialog = new JsonObject();
    boolean confirmation = screen.yes != null;
    dialog.addProperty("type", confirmation ? "minecraft:confirmation" : "minecraft:multi_action");
    dialog.add(
        "title",
        component(screen.plainTitle ? screen.title : "{titulo}۞ <gradient:#FFE08A:#FFA94D>" + screen.title + "</gradient>"));
    dialog.addProperty("can_close_with_escape", true);
    var body = new JsonArray();
    for (Screen.Line line : screen.body) {
      var item = new JsonObject();
      item.addProperty("type", "minecraft:plain_message");
      item.add("contents", line.raw() ? legacy(line.text()) : component(line.text()));
      item.addProperty("width", 320);
      body.add(item);
    }
    dialog.add("body", body);
    if (!screen.inputs.isEmpty()) {
      var inputs = new JsonArray();
      for (Screen.Input input : screen.inputs) {
        var json = new JsonObject();
        json.addProperty("key", input.key());
        json.add("label", component("{texto}" + input.label()));
        json.addProperty("width", 260);
        if (input.options() == null) {
          json.addProperty("type", "minecraft:text");
          json.addProperty("initial", input.initial());
          json.addProperty("max_length", Math.max(1, input.max()));
        } else {
          json.addProperty("type", "minecraft:single_option");
          var options = new JsonArray();
          boolean first = true;
          for (String[] value : input.options()) {
            var option = new JsonObject();
            option.addProperty("id", value[0]);
            option.add("display", component("{dato}" + value[1]));
            if (first) option.addProperty("initial", true);
            first = false;
            options.add(option);
          }
          json.add("options", options);
        }
        inputs.add(json);
      }
      dialog.add("inputs", inputs);
    }
    if (confirmation) {
      dialog.add("yes", button(screen.yes));
      dialog.add("no", button(screen.no));
      return dialog;
    }
    dialog.addProperty("columns", screen.columns);
    var actions = new JsonArray();
    for (Screen.Button button : screen.buttons) actions.add(button(button));
    if (actions.isEmpty()) actions.add(button(new Screen.Button("{texto}Cerrar", null, null, false, null)));
    dialog.add("actions", actions);
    if (screen.exit != null) dialog.add("exit_action", button(screen.exit));
    return dialog;
  }

  private JsonObject button(Screen.Button button) {
    var json = new JsonObject();
    json.add("label", component(button.label()));
    if (button.tooltip() != null) json.add("tooltip", component(button.tooltip()));
    json.addProperty("width", 150);
    if (button.command() != null) {
      var action = new JsonObject();
      if (button.template()) {
        action.addProperty("type", "minecraft:dynamic/run_command");
        action.addProperty("template", button.command());
      } else {
        action.addProperty("type", "minecraft:run_command");
        action.addProperty("command", button.command());
      }
      json.add("action", action);
    }
    return json;
  }

  private JsonElement component(String value) {
    return legacy(messages.color(value));
  }

  private static JsonElement legacy(String colored) {
    return JsonParser.parseString(ComponentSerializer.toString(TextComponent.fromLegacyText(colored)));
  }

  /** Versión de chat: cabecera, líneas y botones pulsables en filas. */
  private void chat(CommandSender sender, Screen screen) {
    messages.header(sender, ClanMessages.Theme.PUBLIC, screen.title);
    for (Screen.Line line : screen.body)
      if (line.raw()) sender.sendMessage(" " + line.text());
      else messages.line(sender, line.text());
    for (Screen.Input input : screen.inputs)
      if (input.options() != null)
        messages.line(sender, "{suave}" + input.label() + ": {dato}"
            + String.join("{suave}, {dato}", input.options().stream().map(ClanViews::typed).toList()));
    List<Screen.Button> buttons = new ArrayList<>(screen.buttons);
    if (screen.yes != null) {
      buttons.add(screen.yes);
      buttons.add(screen.no);
    }
    if (screen.exit != null && screen.exit.command() != null) buttons.add(screen.exit);
    List<BaseComponent> row = new ArrayList<>();
    int count = 0;
    for (Screen.Button button : buttons) {
      if (button.command() == null) continue;
      String hover = button.tooltip() == null ? "" : button.tooltip();
      TextComponent part;
      if (button.template()) {
        String suggestion = button.suggestion() == null ? "" : button.suggestion();
        part = messages.button("{suave}[" + button.label() + "{suave}]", (hover.isEmpty() ? "" : hover + "\n") + "{texto}Escribe: {dato}" + suggestion + "…", suggestion, false);
      } else {
        String command = button.command().endsWith(D) ? button.command().substring(0, button.command().length() - D.length()) : button.command();
        part = messages.button("{suave}[" + button.label() + "{suave}]", hover.isEmpty() ? "{suave}" + command : hover, command, true);
      }
      row.add(part);
      row.add(messages.component(" "));
      if (++count % 3 == 0) {
        messages.components(sender, false, row.toArray(BaseComponent[]::new));
        row.clear();
      }
    }
    if (!row.isEmpty()) messages.components(sender, false, row.toArray(BaseComponent[]::new));
  }

  // ================================================================== utilidades

  /** Lo que se escribe en el chat para una opción: su nombre si el valor es un identificador. */
  private static String typed(String[] option) {
    boolean id = option[0].startsWith("@") || option[0].matches("[0-9a-f]{8}-[0-9a-f-]{27}");
    return id ? net.md_5.bungee.api.ChatColor.stripColor(Messages.color(option[1])).replaceAll(" · .*", "") : option[0];
  }

  public static String paint(Clan clan) {
    return paint(clan.name(), clan.color());
  }

  public static String paint(String text, String hex) {
    return "&" + hex + text;
  }

  private String rankCounts(Clan clan) {
    List<String> parts = new ArrayList<>();
    for (ClanRank rank : reversed()) {
      int count = clan.members(rank).size();
      if (count > 0) parts.add(settings.rankName(rank) + " " + count);
    }
    return String.join(" · ", parts);
  }

  private static List<ClanRank> reversed() {
    List<ClanRank> ranks = new ArrayList<>(List.of(ClanRank.values()));
    Collections.reverse(ranks);
    return ranks;
  }

  static String date(long time) {
    return new SimpleDateFormat("dd/MM/yyyy").format(new Date(time));
  }

  private static boolean hasAdmin(Player player) {
    return player.hasPermission("viciontprotections.admin.clan.manage")
        || player.hasPermission("viciontprotections.admin.clan.spy")
        || player.hasPermission("viciontprotections.admin.clan.war");
  }
}
