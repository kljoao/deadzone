package com.deadzone.modules.events.mutant;

/** Tipos de zumbi mutante. */
public enum MutantType {
    RUNNER,
    TANK,
    EXPLODER;

    public static MutantType fromString(String value) {
        if (value == null) {
            return null;
        }
        try {
            return MutantType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
