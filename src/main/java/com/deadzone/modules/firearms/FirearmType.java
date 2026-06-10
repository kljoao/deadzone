package com.deadzone.modules.firearms;

import java.util.List;

/** Stats de uma arma de fogo, carregados do firearms.yml. */
public record FirearmType(
        String id,
        int customModelData,
        String displayName,
        List<String> lore,
        String ammoItem,
        double damage,
        double projectileSpeed,
        double bulletDrop,
        long fireCooldownMs,
        int magazineSize,
        long reloadMs,
        long drawMs,
        double spreadBase,
        double spreadPerShot,
        double spreadMax,
        double spreadRecovery,
        double meleeDamage,
        double meleeCooldownSeconds,
        int meleeSlowSeconds,
        int meleeSlowAmplifier,
        String soundShoot,
        String soundReload,
        double noise,
        int noiseRadius) {
}
