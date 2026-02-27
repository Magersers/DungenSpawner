package ru.dungenspawner.service;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import ru.mobsrarity.api.MobsRarityService;

public class MobsRarityBridge {
    private final JavaPlugin plugin;
    private MobsRarityService service;

    public MobsRarityBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void hook() {
        try {
            RegisteredServiceProvider<MobsRarityService> registration = Bukkit.getServicesManager()
                    .getRegistration(MobsRarityService.class);

            if (registration == null) {
                service = null;
                plugin.getLogger().warning("MobsRarity не найден в ServicesManager.");
                return;
            }

            service = registration.getProvider();
            if (service == null) {
                plugin.getLogger().warning("MobsRarity provider == null.");
                return;
            }

            plugin.getLogger().info("MobsRarity API успешно подключен: " + service.getClass().getName());
        } catch (Throwable ex) {
            service = null;
            plugin.getLogger().warning("Не удалось подключить MobsRarity API: " + ex.getMessage());
        }
    }

    public boolean isAvailable() {
        return service != null;
    }

    public LivingEntity spawnRarityMob(Location location, EntityType type, String rarity) {
        try {
            if (service == null) {
                return null;
            }
            return service.spawnRarityMob(location, type, rarity);
        } catch (Throwable ex) {
            String details = ex.getCause() != null ? ex.getCause().toString() : ex.toString();
            plugin.getLogger().warning("Ошибка спавна через MobsRarity: " + details);
            return null;
        }
    }

    public void createOrUpdateSpawnZone(String zoneId, World world,
                                        int minX, int minY, int minZ,
                                        int maxX, int maxY, int maxZ,
                                        EntityType type, String rarity,
                                        int amountPerCycle, int cycleIntervalSeconds, int cycles) {
        try {
            if (service == null) {
                return;
            }
            service.createOrUpdateSpawnZone(
                    zoneId,
                    world,
                    minX,
                    minY,
                    minZ,
                    maxX,
                    maxY,
                    maxZ,
                    type,
                    rarity,
                    amountPerCycle,
                    cycleIntervalSeconds,
                    cycles
            );
        } catch (Throwable ex) {
            String details = ex.getCause() != null ? ex.getCause().toString() : ex.toString();
            plugin.getLogger().warning("Не удалось создать spawn-zone в MobsRarity: " + details);
        }
    }
}
