package com.deadzone.modules.claim;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Um baú físico trancado por senha. O PIN NÃO é guardado em texto puro: salvamos só o
 * hash SHA-256 (com salt por baú). Para baú duplo, as metades compartilham hash, salt e autorizados.
 */
public class LockedChest {

    private static final SecureRandom RNG = new SecureRandom();

    private final UUID owner;
    private final String world;
    private final int x;
    private final int y;
    private final int z;
    private String pinHash;
    private String salt;
    private final Set<UUID> authorized;

    public LockedChest(UUID owner, String world, int x, int y, int z, String pinHash, String salt,
                       Set<UUID> authorized) {
        this.owner = owner;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.pinHash = pinHash;
        this.salt = salt;
        this.authorized = authorized != null ? authorized : new HashSet<>();
    }

    /** Cria uma tranca nova a partir do PIN em texto (gera salt e guarda só o hash). */
    public static LockedChest create(UUID owner, String world, int x, int y, int z, String plainPin,
                                     Set<UUID> authorized) {
        String salt = newSalt();
        return new LockedChest(owner, world, x, y, z, hash(plainPin, salt), salt, authorized);
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

    public String pinHash() {
        return pinHash;
    }

    public String salt() {
        return salt;
    }

    /** Confere o PIN digitado contra o hash guardado (comparação em tempo constante). */
    public boolean checkPin(String plainPin) {
        if (pinHash == null || salt == null || plainPin == null) {
            return false;
        }
        return MessageDigest.isEqual(
                pinHash.getBytes(StandardCharsets.UTF_8),
                hash(plainPin, salt).getBytes(StandardCharsets.UTF_8));
    }

    /** Troca o PIN: gera salt novo e regrava o hash (limpar autorizados é responsabilidade do chamador). */
    public void setPin(String plainPin) {
        this.salt = newSalt();
        this.pinHash = hash(plainPin, this.salt);
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

    private static String newSalt() {
        byte[] b = new byte[12];
        RNG.nextBytes(b);
        return Base64.getEncoder().encodeToString(b);
    }

    private static String hash(String plain, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(salt.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(md.digest(plain.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível", e);
        }
    }
}
