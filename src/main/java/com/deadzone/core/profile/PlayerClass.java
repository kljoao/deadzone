package com.deadzone.core.profile;

/** Classe escolhida pelo jogador. NONE = ainda não escolheu (ou foi resetada na morte). */
public enum PlayerClass {
    NONE,
    MEDICO,
    SAQUEADOR,
    BRUTO;

    public static PlayerClass fromString(String value) {
        if (value == null) {
            return NONE;
        }
        try {
            return PlayerClass.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return NONE;
        }
    }
}
