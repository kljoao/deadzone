package com.deadzone.modules.clan;

import java.util.Locale;

/**
 * Cargo dentro do clan. Hierarquia por {@link #weight()}: LÍDER > OFICIAL > MEMBRO > RECRUTA.
 * O recruta é a entrada (acesso limitado); promove-se a membro pleno depois.
 */
public enum ClanRole {

    RECRUIT("Recruta", "<gray>", 0),
    MEMBER("Membro", "<white>", 1),
    OFFICER("Oficial", "<yellow>", 2),
    LEADER("Líder", "<gold>", 3);

    private final String displayName;
    private final String colorTag;
    private final int weight;

    ClanRole(String displayName, String colorTag, int weight) {
        this.displayName = displayName;
        this.colorTag = colorTag;
        this.weight = weight;
    }

    public String displayName() {
        return displayName;
    }

    public String colorTag() {
        return colorTag;
    }

    public int weight() {
        return weight;
    }

    public boolean atLeast(ClanRole other) {
        return this.weight >= other.weight;
    }

    public boolean isAbove(ClanRole other) {
        return this.weight > other.weight;
    }

    /** Pode convidar/aceitar e expulsar quem tiver cargo inferior (Oficial ou acima). */
    public boolean canManageMembers() {
        return weight >= OFFICER.weight;
    }

    public static ClanRole fromString(String value) {
        if (value == null) {
            return MEMBER;
        }
        try {
            return ClanRole.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return MEMBER;
        }
    }
}
