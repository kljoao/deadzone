package com.deadzone.core.profile;

/** Estado transitório "Derrubado", não persistido. */
public class DownedState {

    private final long downedAt;
    private final long expiresAt;    // morre de vez se não for revivido até aqui

    public DownedState(long downedAt, long expiresAt) {
        this.downedAt = downedAt;
        this.expiresAt = expiresAt;
    }

    public long getDownedAt() {
        return downedAt;
    }

    public long getExpiresAt() {
        return expiresAt;
    }
}
