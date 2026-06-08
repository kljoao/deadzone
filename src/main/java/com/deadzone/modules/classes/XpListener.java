package com.deadzone.modules.classes;

import com.deadzone.DeadzonePlugin;
import com.deadzone.core.entity.EntityKeys;
import com.deadzone.core.profile.PlayerProfile;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataType;

/** Concede XP por abater zumbis (comuns e mutantes). */
public class XpListener implements Listener {

    private final DeadzonePlugin plugin;
    private final ClassManager manager;

    public XpListener(DeadzonePlugin plugin, ClassManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!plugin.getInfectionManager().isZombieType(entity)) {
            return;
        }
        Player killer = entity.getKiller();
        if (killer == null) {
            return;
        }
        PlayerProfile profile = plugin.getProfileManager().get(killer.getUniqueId());
        if (profile == null) {
            return;
        }
        String mutantType = entity.getPersistentDataContainer().get(EntityKeys.ZOMBIE_TYPE, PersistentDataType.STRING);
        long amount = (mutantType != null)
                ? manager.config().xpMutantKill(mutantType)
                : manager.config().xpZombieKill();
        manager.grantXp(killer, profile, amount, "abate");
    }
}
