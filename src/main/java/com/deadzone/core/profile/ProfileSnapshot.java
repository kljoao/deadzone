package com.deadzone.core.profile;

import java.util.Set;
import java.util.UUID;

/** Cópia imutável de um {@link PlayerProfile} para escrita assíncrona no banco. */
public record ProfileSnapshot(
        UUID uuid,
        String name,
        boolean infected,
        double infectionLevel,
        double sanity,
        PlayerClass playerClass,
        long xp,
        long totalXpEarned,
        long firstJoin,
        long lastSeen,
        long downedUntil,
        long balance,
        long bounty,
        Set<String> skills,
        long dailyStreak,
        long lastDailyClaim,
        long zombiesKilled,
        long playersKilled,
        long deaths,
        long revives,
        long bestSurvivalMs,
        long lifeStartedAt
) {
}
