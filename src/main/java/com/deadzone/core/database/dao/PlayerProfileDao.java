package com.deadzone.core.database.dao;

import com.deadzone.core.profile.PlayerProfile;
import com.deadzone.core.profile.ProfileSnapshot;

import java.util.Collection;
import java.util.UUID;

/** Contrato de persistência de perfis. */
public interface PlayerProfileDao {

    /** Carrega um perfil; retorna null se não existir. */
    PlayerProfile load(UUID uuid);

    /** Upsert do perfil + sincronização das skills. */
    void save(ProfileSnapshot snapshot);

    /** Salva vários perfis (usado no shutdown). */
    void saveAll(Collection<ProfileSnapshot> snapshots);

    /** Remove completamente um perfil (wipe administrativo). */
    void delete(UUID uuid);
}
