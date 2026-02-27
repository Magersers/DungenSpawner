package ru.dungenspawner.service;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;

public class MobsRarityBridge {
    private final JavaPlugin plugin;
    private Object service;
    private Method spawnRarityMob;
    private Method createOrUpdateSpawnZone;

    public MobsRarityBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void hook() {
        try {
            Class<?> serviceClass = Class.forName("ru.mobsrarity.api.MobsRarityService");
            RegisteredServiceProvider<?> registration = Bukkit.getServicesManager().getRegistration(serviceClass);
            if (registration == null || registration.getProvider() == null) {
                plugin.getLogger().warning("MobsRarity не найден в ServicesManager.");
                service = null;
                spawnRarityMob = null;
                createOrUpdateSpawnZone = null;
                return;
            }

            service = registration.getProvider();
            spawnRarityMob = serviceClass.getMethod("spawnRarityMob", Location.class, EntityType.class, String.class);
            createOrUpdateSpawnZone = serviceClass.getMethod("createOrUpdateSpawnZone", String.class, World.class,
                    int.class, int.class, int.class, int.class, int.class, int.class,
                    EntityType.class, String.class, int.class, int.class, int.class);
            plugin.getLogger().info("MobsRarity API успешно подключен через ServicesManager.");
        } catch (Throwable ex) {
            service = null;
            spawnRarityMob = null;
            createOrUpdateSpawnZone = null;
            plugin.getLogger().warning("Не удалось подключить MobsRarity API: " + ex.getMessage());
        }
    }

    public boolean isAvailable() {
        return service != null && spawnRarityMob != null;
    }

    public LivingEntity spawnRarityMob(Location location, EntityType type, String rarity) {
        try {
            if (!isAvailable()) {
                return null;
            }
            Object result = spawnRarityMob.invoke(service, location, type, rarity);
            return result instanceof LivingEntity livingEntity ? livingEntity : null;
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
            if (service == null || createOrUpdateSpawnZone == null) {
                return;
            }
            createOrUpdateSpawnZone.invoke(service,
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
