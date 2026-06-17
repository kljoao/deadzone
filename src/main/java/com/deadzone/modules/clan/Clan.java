package com.deadzone.modules.clan;

import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Um clan: identidade, integrantes, cofre, nível e preferências (toggles do líder). */
public class Clan {

    private final String id; // UUID estável (chave no banco)
    private String name;
    private String tag;
    private String color; // nome de NamedTextColor (ex.: "RED") — usado no chat e no glow
    private UUID leader;
    private final long createdAt;

    private long bank;  // scraps no cofre
    private long level;
    private long xp;

    // toggles gerenciados pelo líder
    private boolean friendlyFire; // dano entre membros
    private boolean glow;         // glow de aliado (Fase 2)
    private boolean symbol;       // símbolo de aliado acima da cabeça (Fase 2)

    private final Map<UUID, ClanMember> members = new ConcurrentHashMap<>();

    public Clan(String id, String name, String tag, String color, UUID leader, long createdAt) {
        this.id = id;
        this.name = name;
        this.tag = tag;
        this.color = color;
        this.leader = leader;
        this.createdAt = createdAt;
        this.level = 1;
        this.glow = true;
        this.symbol = true;
        this.friendlyFire = false;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String tag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public String color() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public NamedTextColor namedColor() {
        NamedTextColor c = NamedTextColor.NAMES.value(color == null ? "" : color.toLowerCase());
        return c != null ? c : NamedTextColor.WHITE;
    }

    public UUID leader() {
        return leader;
    }

    public void setLeader(UUID leader) {
        this.leader = leader;
    }

    public long createdAt() {
        return createdAt;
    }

    public long bank() {
        return bank;
    }

    public void setBank(long bank) {
        this.bank = Math.max(0L, bank);
    }

    public void addBank(long delta) {
        setBank(this.bank + delta);
    }

    public long level() {
        return level;
    }

    public void setLevel(long level) {
        this.level = Math.max(1L, level);
    }

    public long xp() {
        return xp;
    }

    public void setXp(long xp) {
        this.xp = Math.max(0L, xp);
    }

    public boolean friendlyFire() {
        return friendlyFire;
    }

    public void setFriendlyFire(boolean friendlyFire) {
        this.friendlyFire = friendlyFire;
    }

    public boolean glow() {
        return glow;
    }

    public void setGlow(boolean glow) {
        this.glow = glow;
    }

    public boolean symbol() {
        return symbol;
    }

    public void setSymbol(boolean symbol) {
        this.symbol = symbol;
    }

    // ----- membros -----

    public Map<UUID, ClanMember> membersMap() {
        return members;
    }

    public Collection<ClanMember> members() {
        return members.values();
    }

    public ClanMember member(UUID uuid) {
        return members.get(uuid);
    }

    public boolean isMember(UUID uuid) {
        return members.containsKey(uuid);
    }

    public ClanRole roleOf(UUID uuid) {
        ClanMember m = members.get(uuid);
        return m != null ? m.role() : null;
    }

    public void putMember(ClanMember member) {
        members.put(member.uuid(), member);
    }

    public void removeMember(UUID uuid) {
        members.remove(uuid);
    }

    public int size() {
        return members.size();
    }
}
