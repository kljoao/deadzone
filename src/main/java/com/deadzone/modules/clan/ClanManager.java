package com.deadzone.modules.clan;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.config.ConfigManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Núcleo dos clans: registro em memória + persistência write-through. Cargos: Líder > Oficial >
 * Membro > Recruta. Estado-fonte é a memória; o banco é atualizado a cada mutação (na thread FIFO).
 */
public class ClanManager {

    private static final Pattern VALID = Pattern.compile("^[A-Za-z0-9_]+$");

    private final DeadzonePlugin plugin;
    private final ClanConfig config;
    private final ClanDao dao;

    private final Map<String, Clan> byId = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerToClan = new ConcurrentHashMap<>();
    private final Map<String, String> nameToId = new ConcurrentHashMap<>(); // nome lower -> id
    private final Map<String, String> tagToId = new ConcurrentHashMap<>();  // tag  lower -> id
    private final Map<UUID, Invite> invites = new ConcurrentHashMap<>();
    private ClanInputService input;

    public ClanManager(DeadzonePlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.config = new ClanConfig(plugin, configManager);
        this.dao = new ClanDao(plugin.getDatabase());
    }

    public ClanConfig config() {
        return config;
    }

    /** Serviço de prompts de chat usado pelos menus. */
    public ClanInputService prompts() {
        return input;
    }

    public void enable() {
        try (Connection c = dao.database().getConnection()) {
            for (Clan clan : dao.loadAll(c)) {
                index(clan);
            }
            plugin.getLogger().info("Clãs carregados: " + byId.size() + ".");
        } catch (SQLException e) {
            plugin.getLogger().severe("Falha ao carregar clans: " + e.getMessage());
        }
        // Prompts de chat dos menus.
        this.input = new ClanInputService(plugin);
        plugin.getServer().getPluginManager().registerEvents(input, plugin);
    }

    /** Ao logar: se for líder de um clan e tiver a permissão de tier máximo, garante o topo. */
    public void onPlayerJoin(Player player) {
        Clan clan = getClanOf(player.getUniqueId());
        if (clan != null && clan.leader().equals(player.getUniqueId()) && applyMaxTierPerk(clan, player)) {
            runDb(c -> dao.updateClan(c, clan));
        }
    }

    // ----- tiers (progressão paga com scraps do cofre) -----

    /** Limite de integrantes do tier atual do clan. */
    public int maxMembersFor(Clan clan) {
        return config.tierMembers((int) clan.level());
    }

    public boolean isMaxTier(Clan clan) {
        return clan.level() >= config.maxTier();
    }

    /** Custo (scraps) para evoluir ao próximo tier, ou -1 se já no máximo. */
    public long nextTierCost(Clan clan) {
        int next = (int) clan.level() + 1;
        return next > config.maxTier() ? -1 : config.tierCost(next);
    }

    /** Vagas que o próximo tier concederia, ou -1 se já no máximo. */
    public int nextTierMembers(Clan clan) {
        int next = (int) clan.level() + 1;
        return next > config.maxTier() ? -1 : config.tierMembers(next);
    }

    /** Evolui o clan para o próximo tier pagando do cofre (líder). */
    public void upgradeTier(Player actor) {
        Clan clan = requireLeader(actor);
        if (clan == null) {
            return;
        }
        int next = (int) clan.level() + 1;
        if (next > config.maxTier()) {
            error(actor, "Seu clan já está no tier máximo (" + config.maxTier() + ").");
            return;
        }
        long cost = config.tierCost(next);
        if (clan.bank() < cost) {
            error(actor, "Cofre insuficiente: precisa de " + plugin.getEconomyManager().format(cost)
                    + " (tem " + plugin.getEconomyManager().format(clan.bank()) + ").");
            return;
        }
        clan.addBank(-cost);
        clan.setLevel(next);
        runDb(c -> dao.updateClan(c, clan));
        broadcast(clan, Component.text("⬆ Seu clan evoluiu para o Tier " + next + "! Vagas: "
                + maxMembersFor(clan) + " integrantes.", NamedTextColor.GOLD));
    }

    /**
     * Se o jogador tem a permissão de tier máximo (Warlord/Overlord), sobe o clan ao tier máximo.
     * Apenas muta em memória; o chamador é responsável por persistir. @return true se mudou.
     */
    public boolean applyMaxTierPerk(Clan clan, Player player) {
        if (player != null && player.hasPermission(ClanConfig.PERM_MAX_TIER)
                && clan.level() < config.maxTier()) {
            clan.setLevel(config.maxTier());
            return true;
        }
        return false;
    }

    public void reload() {
        config.load();
    }

    private void index(Clan clan) {
        byId.put(clan.id(), clan);
        nameToId.put(clan.name().toLowerCase(Locale.ROOT), clan.id());
        tagToId.put(clan.tag().toLowerCase(Locale.ROOT), clan.id());
        for (UUID member : clan.membersMap().keySet()) {
            playerToClan.put(member, clan.id());
        }
    }

    // ----- consultas -----

    public Clan getClanOf(UUID uuid) {
        String id = playerToClan.get(uuid);
        return id != null ? byId.get(id) : null;
    }

    public ClanRole roleOf(UUID uuid) {
        Clan clan = getClanOf(uuid);
        return clan != null ? clan.roleOf(uuid) : null;
    }

    public Clan getByName(String name) {
        String id = nameToId.get(name.toLowerCase(Locale.ROOT));
        return id != null ? byId.get(id) : null;
    }

    public Clan getByTag(String tag) {
        String id = tagToId.get(tag.toLowerCase(Locale.ROOT));
        return id != null ? byId.get(id) : null;
    }

    public Collection<Clan> all() {
        return byId.values();
    }

    /** Ranking de clãs: por tier, depois nº de integrantes, depois cofre. */
    public List<Clan> topClans(int limit) {
        List<Clan> list = new ArrayList<>(byId.values());
        list.sort((a, b) -> {
            int c = Long.compare(b.level(), a.level());
            if (c != 0) {
                return c;
            }
            c = Integer.compare(b.size(), a.size());
            if (c != 0) {
                return c;
            }
            return Long.compare(b.bank(), a.bank());
        });
        return list.size() > limit ? list.subList(0, limit) : list;
    }

    /** true se ambos estão no MESMO clan (base do glow/símbolo/friendly-fire). */
    public boolean areClanmates(UUID a, UUID b) {
        if (a == null || b == null || a.equals(b)) {
            return false;
        }
        String ca = playerToClan.get(a);
        return ca != null && ca.equals(playerToClan.get(b));
    }

    /** O combate deve ser cancelado por friendly-fire? (mesmo clan e dano interno desligado). */
    public boolean blocksFriendlyFire(UUID attacker, UUID victim) {
        if (!areClanmates(attacker, victim)) {
            return false;
        }
        Clan clan = getClanOf(attacker);
        return clan != null && !clan.friendlyFire();
    }

    // ----- validação (usada pelo GUI antes de criar) -----

    /** @return mensagem de erro do nome, ou null se válido. */
    public String nameError(String name) {
        if (name == null || name.isBlank()) {
            return "Digite um nome.";
        }
        name = name.trim();
        if (name.length() < config.nameMin() || name.length() > config.nameMax()) {
            return "O nome deve ter entre " + config.nameMin() + " e " + config.nameMax() + " caracteres.";
        }
        if (!VALID.matcher(name).matches()) {
            return "O nome só pode ter letras, números e _ (sem espaços ou símbolos).";
        }
        if (getByName(name) != null) {
            return "Já existe um clan com esse nome.";
        }
        return null;
    }

    /** @return mensagem de erro da tag, ou null se válida. */
    public String tagError(String tag) {
        if (tag == null || tag.isBlank()) {
            return "Digite uma tag.";
        }
        tag = tag.trim();
        if (tag.length() < config.tagMin() || tag.length() > config.tagMax()) {
            return "A tag deve ter entre " + config.tagMin() + " e " + config.tagMax() + " caracteres.";
        }
        if (!VALID.matcher(tag).matches()) {
            return "A tag só pode ter letras, números e _ (sem espaços ou símbolos).";
        }
        if (getByTag(tag) != null) {
            return "Já existe um clan com essa tag.";
        }
        return null;
    }

    // ----- criação / dissolução -----

    public void create(Player leader, String name, String tag) {
        if (getClanOf(leader.getUniqueId()) != null) {
            error(leader, "Você já está em um clan. Saia antes de criar outro.");
            return;
        }
        if (name.length() < config.nameMin() || name.length() > config.nameMax() || !VALID.matcher(name).matches()) {
            error(leader, "Nome inválido (use " + config.nameMin() + "-" + config.nameMax()
                    + " letras/números, sem espaços).");
            return;
        }
        if (tag.length() < config.tagMin() || tag.length() > config.tagMax() || !VALID.matcher(tag).matches()) {
            error(leader, "Tag inválida (use " + config.tagMin() + "-" + config.tagMax()
                    + " letras/números, sem espaços).");
            return;
        }
        if (getByName(name) != null) {
            error(leader, "Já existe um clan com esse nome.");
            return;
        }
        if (getByTag(tag) != null) {
            error(leader, "Já existe um clan com essa tag.");
            return;
        }
        long cost = config.createCost();
        if (cost > 0 && !plugin.getEconomyManager().tryDebit(leader, cost)) {
            error(leader, "Você precisa de " + plugin.getEconomyManager().format(cost) + " para fundar um clan.");
            return;
        }
        Clan clan = new Clan(UUID.randomUUID().toString(), name, tag, config.defaultColor(),
                leader.getUniqueId(), System.currentTimeMillis());
        clan.putMember(new ClanMember(leader.getUniqueId(), leader.getName(), ClanRole.LEADER,
                System.currentTimeMillis()));
        applyMaxTierPerk(clan, leader); // Warlord/Overlord já começam no tier máximo
        index(clan);
        runDb(c -> {
            dao.insertClan(c, clan);
            dao.upsertMember(c, clan.id(), clan.member(leader.getUniqueId()));
        });
        leader.playSound(leader, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1f);
        leader.sendMessage(prefix(clan).append(Component.text("Clan fundado! Use ", NamedTextColor.GREEN))
                .append(Component.text("/clan convidar <jogador>", NamedTextColor.YELLOW))
                .append(Component.text(" para recrutar.", NamedTextColor.GREEN)));
    }

    public void disband(Player actor) {
        Clan clan = getClanOf(actor.getUniqueId());
        if (clan == null) {
            error(actor, "Você não está em um clan.");
            return;
        }
        if (!clan.leader().equals(actor.getUniqueId())) {
            error(actor, "Só o líder pode dissolver o clan.");
            return;
        }
        broadcast(clan, Component.text("O clan foi dissolvido pelo líder.", NamedTextColor.RED));
        String id = clan.id();
        for (UUID member : new ArrayList<>(clan.membersMap().keySet())) {
            playerToClan.remove(member);
        }
        byId.remove(id);
        nameToId.remove(clan.name().toLowerCase(Locale.ROOT));
        tagToId.remove(clan.tag().toLowerCase(Locale.ROOT));
        runDb(c -> dao.deleteClan(c, id));
    }

    // ----- convites -----

    public void invite(Player inviter, String targetName) {
        Clan clan = getClanOf(inviter.getUniqueId());
        if (clan == null) {
            error(inviter, "Você não está em um clan.");
            return;
        }
        ClanRole role = clan.roleOf(inviter.getUniqueId());
        if (role == null || !role.canManageMembers()) {
            error(inviter, "Só Oficiais e o Líder podem convidar.");
            return;
        }
        if (clan.size() >= maxMembersFor(clan)) {
            error(inviter, "O clan está cheio (" + maxMembersFor(clan) + " integrantes).");
            return;
        }
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            error(inviter, "Esse jogador precisa estar online.");
            return;
        }
        if (getClanOf(target.getUniqueId()) != null) {
            error(inviter, target.getName() + " já está em um clan.");
            return;
        }
        invites.put(target.getUniqueId(), new Invite(clan.id(), System.currentTimeMillis() + config.inviteTtlMs()));
        inviter.sendMessage(prefix(clan).append(Component.text("Convite enviado a " + target.getName() + ".",
                NamedTextColor.GREEN)));
        target.sendMessage(prefix(clan).append(Component.text(inviter.getName() + " convidou você para o clan ",
                        NamedTextColor.GREEN))
                .append(Component.text(clan.name(), clan.namedColor()))
                .append(Component.text(". Use ", NamedTextColor.GREEN))
                .append(Component.text("/clan aceitar", NamedTextColor.YELLOW))
                .append(Component.text(" ou ", NamedTextColor.GREEN))
                .append(Component.text("/clan recusar", NamedTextColor.YELLOW))
                .append(Component.text(".", NamedTextColor.GREEN)));
        target.playSound(target, Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, 1.3f);
    }

    public void accept(Player target) {
        Invite invite = invites.get(target.getUniqueId());
        if (invite == null || System.currentTimeMillis() > invite.expiresAt()) {
            invites.remove(target.getUniqueId());
            error(target, "Você não tem um convite pendente.");
            return;
        }
        Clan clan = byId.get(invite.clanId());
        if (clan == null) {
            invites.remove(target.getUniqueId());
            error(target, "Esse clan não existe mais.");
            return;
        }
        if (getClanOf(target.getUniqueId()) != null) {
            error(target, "Você já está em um clan.");
            return;
        }
        if (clan.size() >= maxMembersFor(clan)) {
            error(target, "O clan encheu enquanto você decidia.");
            return;
        }
        invites.remove(target.getUniqueId());
        ClanMember member = new ClanMember(target.getUniqueId(), target.getName(), ClanRole.RECRUIT,
                System.currentTimeMillis());
        clan.putMember(member);
        playerToClan.put(target.getUniqueId(), clan.id());
        runDb(c -> dao.upsertMember(c, clan.id(), member));
        target.playSound(target, Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f);
        broadcast(clan, Component.text(target.getName() + " entrou no clan como Recruta!", NamedTextColor.GREEN));
    }

    public void deny(Player target) {
        if (invites.remove(target.getUniqueId()) != null) {
            target.sendMessage(Component.text("Convite recusado.", NamedTextColor.GRAY));
        } else {
            error(target, "Você não tem um convite pendente.");
        }
    }

    // ----- saída / expulsão -----

    public void leave(Player actor) {
        Clan clan = getClanOf(actor.getUniqueId());
        if (clan == null) {
            error(actor, "Você não está em um clan.");
            return;
        }
        if (clan.leader().equals(actor.getUniqueId())) {
            if (clan.size() == 1) {
                disband(actor);
                return;
            }
            error(actor, "O líder não pode sair: transfira a liderança (/clan transferir <jogador>) ou dissolva.");
            return;
        }
        removeMember(clan, actor.getUniqueId(), actor.getName());
        actor.sendMessage(Component.text("Você saiu do clan.", NamedTextColor.GRAY));
        broadcast(clan, Component.text(actor.getName() + " saiu do clan.", NamedTextColor.GRAY));
    }

    public void kick(Player actor, String targetName) {
        Clan clan = requireManager(actor);
        if (clan != null) {
            kickCore(actor, clan, findMember(clan, targetName));
        }
    }

    public void kick(Player actor, UUID targetUuid) {
        Clan clan = requireManager(actor);
        if (clan != null) {
            kickCore(actor, clan, clan.member(targetUuid));
        }
    }

    private void kickCore(Player actor, Clan clan, ClanMember target) {
        if (target == null) {
            error(actor, "Esse jogador não está no seu clan.");
            return;
        }
        if (target.uuid().equals(actor.getUniqueId())) {
            error(actor, "Use /clan sair para sair você mesmo.");
            return;
        }
        ClanRole actorRole = clan.roleOf(actor.getUniqueId());
        if (actorRole == null || !actorRole.isAbove(target.role())) {
            error(actor, "Você não pode expulsar alguém de cargo igual ou superior.");
            return;
        }
        removeMember(clan, target.uuid(), target.lastKnownName());
        broadcast(clan, Component.text(target.lastKnownName() + " foi expulso do clan.", NamedTextColor.RED));
        Player online = Bukkit.getPlayer(target.uuid());
        if (online != null) {
            online.sendMessage(Component.text("Você foi expulso do clan " + clan.name() + ".", NamedTextColor.RED));
        }
    }

    private void removeMember(Clan clan, UUID uuid, String name) {
        clan.removeMember(uuid);
        playerToClan.remove(uuid);
        runDb(c -> dao.deleteMember(c, uuid));
    }

    // ----- cargos -----

    public void promote(Player actor, String targetName) {
        Clan clan = requireLeader(actor);
        if (clan != null) {
            changeRoleCore(actor, clan, findMember(clan, targetName), true);
        }
    }

    public void demote(Player actor, String targetName) {
        Clan clan = requireLeader(actor);
        if (clan != null) {
            changeRoleCore(actor, clan, findMember(clan, targetName), false);
        }
    }

    public void promote(Player actor, UUID targetUuid) {
        Clan clan = requireLeader(actor);
        if (clan != null) {
            changeRoleCore(actor, clan, clan.member(targetUuid), true);
        }
    }

    public void demote(Player actor, UUID targetUuid) {
        Clan clan = requireLeader(actor);
        if (clan != null) {
            changeRoleCore(actor, clan, clan.member(targetUuid), false);
        }
    }

    private void changeRoleCore(Player actor, Clan clan, ClanMember target, boolean up) {
        if (target == null || target.uuid().equals(actor.getUniqueId())) {
            error(actor, "Esse jogador não está no seu clan.");
            return;
        }
        ClanRole current = target.role();
        ClanRole next = up ? nextRole(current) : prevRole(current);
        if (next == ClanRole.LEADER) {
            error(actor, "Para passar a liderança use /clan transferir.");
            return;
        }
        if (next == current) {
            error(actor, "Cargo já está no limite.");
            return;
        }
        target.setRole(next);
        runDb(c -> dao.upsertMember(c, clan.id(), target));
        broadcast(clan, Component.text(target.lastKnownName() + " agora é " + next.displayName() + ".",
                NamedTextColor.AQUA));
    }

    public void transfer(Player actor, String targetName) {
        Clan clan = requireLeader(actor);
        if (clan != null) {
            transferCore(actor, clan, findMember(clan, targetName));
        }
    }

    public void transfer(Player actor, UUID targetUuid) {
        Clan clan = requireLeader(actor);
        if (clan != null) {
            transferCore(actor, clan, clan.member(targetUuid));
        }
    }

    private void transferCore(Player actor, Clan clan, ClanMember target) {
        if (target == null || target.uuid().equals(actor.getUniqueId())) {
            error(actor, "Esse jogador não está no seu clan.");
            return;
        }
        ClanMember oldLeader = clan.member(actor.getUniqueId());
        oldLeader.setRole(ClanRole.OFFICER);
        target.setRole(ClanRole.LEADER);
        clan.setLeader(target.uuid());
        applyMaxTierPerk(clan, Bukkit.getPlayer(target.uuid())); // novo líder VIP desbloqueia o topo
        runDb(c -> {
            dao.upsertMember(c, clan.id(), oldLeader);
            dao.upsertMember(c, clan.id(), target);
            dao.updateClan(c, clan);
        });
        broadcast(clan, Component.text(target.lastKnownName() + " é o novo Líder do clan!", NamedTextColor.GOLD));
    }

    private ClanRole nextRole(ClanRole r) {
        return switch (r) {
            case RECRUIT -> ClanRole.MEMBER;
            case MEMBER -> ClanRole.OFFICER;
            default -> r;
        };
    }

    private ClanRole prevRole(ClanRole r) {
        return switch (r) {
            case OFFICER -> ClanRole.MEMBER;
            case MEMBER -> ClanRole.RECRUIT;
            default -> r;
        };
    }

    // ----- cor / toggles (líder) -----

    public void setColor(Player actor, String color) {
        Clan clan = requireLeader(actor);
        if (clan == null) {
            return;
        }
        if (!actor.hasPermission(ClanConfig.PERM_COLORED_TAG)) {
            error(actor, "Tag colorida é exclusiva de VIP+ (Warlord/Overlord).");
            return;
        }
        if (!config.isColorAllowed(color)) {
            error(actor, "Cor inválida. Opções: " + String.join(", ", config.allowedColors()));
            return;
        }
        clan.setColor(color.toUpperCase(Locale.ROOT));
        runDb(c -> dao.updateClan(c, clan));
        actor.sendMessage(prefix(clan).append(Component.text("Cor do clan atualizada.", NamedTextColor.GREEN)));
    }

    public void setFriendlyFire(Player actor, boolean value) {
        Clan clan = requireLeader(actor);
        if (clan == null) {
            return;
        }
        clan.setFriendlyFire(value);
        runDb(c -> dao.updateClan(c, clan));
        broadcast(clan, Component.text("Dano entre membros: " + (value ? "LIGADO" : "DESLIGADO") + ".",
                value ? NamedTextColor.RED : NamedTextColor.GREEN));
    }

    public void setGlow(Player actor, boolean value) {
        Clan clan = requireLeader(actor);
        if (clan == null) {
            return;
        }
        clan.setGlow(value);
        runDb(c -> dao.updateClan(c, clan));
        broadcast(clan, Component.text("Glow de aliado: " + (value ? "LIGADO" : "DESLIGADO") + ".",
                NamedTextColor.AQUA));
    }

    public void setSymbol(Player actor, boolean value) {
        Clan clan = requireLeader(actor);
        if (clan == null) {
            return;
        }
        clan.setSymbol(value);
        runDb(c -> dao.updateClan(c, clan));
        broadcast(clan, Component.text("Símbolo de aliado: " + (value ? "LIGADO" : "DESLIGADO") + ".",
                NamedTextColor.AQUA));
    }

    // ----- cofre -----

    public void bankDeposit(Player actor, long amount) {
        Clan clan = getClanOf(actor.getUniqueId());
        if (clan == null) {
            error(actor, "Você não está em um clan.");
            return;
        }
        if (amount <= 0) {
            error(actor, "Valor inválido.");
            return;
        }
        if (!plugin.getEconomyManager().tryDebit(actor, amount)) {
            error(actor, "Saldo insuficiente.");
            return;
        }
        clan.addBank(amount);
        runDb(c -> dao.updateClan(c, clan));
        broadcast(clan, Component.text(actor.getName() + " depositou " + plugin.getEconomyManager().format(amount)
                + " no cofre. Total: " + plugin.getEconomyManager().format(clan.bank()) + ".", NamedTextColor.GOLD));
    }

    public void bankWithdraw(Player actor, long amount) {
        Clan clan = getClanOf(actor.getUniqueId());
        if (clan == null) {
            error(actor, "Você não está em um clan.");
            return;
        }
        ClanRole role = clan.roleOf(actor.getUniqueId());
        if (role == null || !role.canManageMembers()) {
            error(actor, "Só Oficiais e o Líder podem sacar do cofre.");
            return;
        }
        if (amount <= 0 || amount > clan.bank()) {
            error(actor, "Valor inválido (cofre tem " + plugin.getEconomyManager().format(clan.bank()) + ").");
            return;
        }
        clan.addBank(-amount);
        plugin.getEconomyManager().reward(actor, amount);
        runDb(c -> dao.updateClan(c, clan));
        broadcast(clan, Component.text(actor.getName() + " sacou " + plugin.getEconomyManager().format(amount)
                + " do cofre. Total: " + plugin.getEconomyManager().format(clan.bank()) + ".", NamedTextColor.GOLD));
    }

    // ----- chat -----

    public void chat(Player actor, String message) {
        Clan clan = getClanOf(actor.getUniqueId());
        if (clan == null) {
            error(actor, "Você não está em um clan.");
            return;
        }
        Component line = Component.text("[Clan] ", clan.namedColor())
                .append(Component.text(actor.getName() + ": ", NamedTextColor.GRAY))
                .append(Component.text(message, NamedTextColor.WHITE));
        broadcast(clan, line);
    }

    // ----- helpers -----

    private Clan requireManager(Player actor) {
        Clan clan = getClanOf(actor.getUniqueId());
        if (clan == null) {
            error(actor, "Você não está em um clan.");
            return null;
        }
        ClanRole role = clan.roleOf(actor.getUniqueId());
        if (role == null || !role.canManageMembers()) {
            error(actor, "Só Oficiais e o Líder podem fazer isso.");
            return null;
        }
        return clan;
    }

    private Clan requireLeader(Player actor) {
        Clan clan = getClanOf(actor.getUniqueId());
        if (clan == null) {
            error(actor, "Você não está em um clan.");
            return null;
        }
        if (!clan.leader().equals(actor.getUniqueId())) {
            error(actor, "Só o líder pode fazer isso.");
            return null;
        }
        return clan;
    }

    private ClanMember findMember(Clan clan, String name) {
        for (ClanMember m : clan.members()) {
            if (m.lastKnownName() != null && m.lastKnownName().equalsIgnoreCase(name)) {
                return m;
            }
        }
        Player online = Bukkit.getPlayerExact(name);
        return online != null ? clan.member(online.getUniqueId()) : null;
    }

    /** Tag colorida do clan como prefixo de mensagem: "[TAG] ". */
    public Component prefix(Clan clan) {
        return Component.text("[" + clan.tag() + "] ", clan.namedColor());
    }

    public void broadcast(Clan clan, Component message) {
        for (UUID uuid : clan.membersMap().keySet()) {
            Player online = Bukkit.getPlayer(uuid);
            if (online != null) {
                online.sendMessage(message);
            }
        }
    }

    private void runDb(SqlConsumer op) {
        plugin.getProfileManager().submitDbTask(() -> {
            try (Connection c = dao.database().getConnection()) {
                op.accept(c);
            } catch (SQLException e) {
                plugin.getLogger().warning("Clãs (DB) falhou: " + e.getMessage());
            }
        });
    }

    private void error(CommandSender sender, String msg) {
        sender.sendMessage(Component.text(msg, NamedTextColor.RED));
    }

    @FunctionalInterface
    private interface SqlConsumer {
        void accept(Connection c) throws SQLException;
    }

    private record Invite(String clanId, long expiresAt) {
    }
}
