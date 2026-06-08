package com.deadzone.core.profile;

/**
 * Estado transitório de sangramento (não persistido, limpo no wipe da morte).
 * A severidade acumula a cada golpe e encurta o intervalo entre os ticks de dano.
 */
public class BleedState {

    private int severity;
    private long nextDamageAt;

    public BleedState(int severity, long nextDamageAt) {
        this.severity = severity;
        this.nextDamageAt = nextDamageAt;
    }

    public int getSeverity() {
        return severity;
    }

    public void setSeverity(int severity) {
        this.severity = severity;
    }

    public long getNextDamageAt() {
        return nextDamageAt;
    }

    public void setNextDamageAt(long nextDamageAt) {
        this.nextDamageAt = nextDamageAt;
    }
}
