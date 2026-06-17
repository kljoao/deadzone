package com.deadzone.modules.clan;

import java.util.UUID;

/** Um integrante do clan: UUID, cargo (mutável) e quando entrou. */
public class ClanMember {

    private final UUID uuid;
    private String lastKnownName;
    private ClanRole role;
    private final long joinedAt;

    public ClanMember(UUID uuid, String lastKnownName, ClanRole role, long joinedAt) {
        this.uuid = uuid;
        this.lastKnownName = lastKnownName;
        this.role = role;
        this.joinedAt = joinedAt;
    }

    public UUID uuid() {
        return uuid;
    }

    public String lastKnownName() {
        return lastKnownName;
    }

    public void setLastKnownName(String lastKnownName) {
        this.lastKnownName = lastKnownName;
    }

    public ClanRole role() {
        return role;
    }

    public void setRole(ClanRole role) {
        this.role = role;
    }

    public long joinedAt() {
        return joinedAt;
    }
}
