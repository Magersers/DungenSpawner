package ru.dungenspawner.listener;

import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import ru.dungenspawner.manager.DungeonManager;

public class DungeonListener implements Listener {
    private final DungeonManager dungeonManager;

    public DungeonListener(DungeonManager dungeonManager) {
        this.dungeonManager = dungeonManager;
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        dungeonManager.onTrackedMobDeath(entity.getUniqueId(), entity.getKiller());
    }
}
