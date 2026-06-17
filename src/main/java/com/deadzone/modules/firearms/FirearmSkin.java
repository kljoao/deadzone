package com.deadzone.modules.firearms;

/** Skin cosmética de uma arma: troca só o modelo (CMD), mantendo a arma base intacta. */
public record FirearmSkin(String id, String firearmId, String display, int modelData) {
}
