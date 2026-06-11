package com.deadzone.modules.claim;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Um baú físico trancado por senha (PIN). Para baú duplo, as duas metades compartilham o set de autorizados. */
public class LockedChest {

    private final UUID owner;
    private final String world;
    private final int x;
    private final int y;
    private final int z;
    private String pin;
    private final Set<UUID> authorized;

    public LockedChest(UUID owner, String world, int x, int y, int z, String pin, Set<UUID> authorized) {
        this.owner = owner;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.pin = pin;
        this.authorized = authorized != null ? authorized : new HashSet<>();
    }

    public UUID owner() {
        return owner;
    }

    public String world() {
        return world;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int z() {
        return z;
    }

    public String pin() {
        return pin;
    }

    public void setPin(String pin) {
        this.pin = pin;
    }

    public Set<UUID> authorized() {
        return authorized;
    }

    public boolean isAuthorized(UUID uuid) {
        return authorized.contains(uuid);
    }

    public void authorize(UUID uuid) {
        authorized.add(uuid);
    }
}
