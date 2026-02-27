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
                return;
            }

            service = registration.getProvider();
            spawnRarityMob = serviceClass.getMethod("spawnRarityMob", Location.class, EntityType.class, String.class);
            createOrUpdateSpawnZone = findSpawnZoneMethod(serviceClass);

            if (createOrUpdateSpawnZone == null) {
                plugin.getLogger().warning("MobsRarity API подключен без createOrUpdateSpawnZone: автозоны отключены, спавн мобов работает.");
            } else {
                plugin.getLogger().info("MobsRarity API успешно подключен.");
            }
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
            if (isAvailable()) {
                Object result = spawnRarityMob.invoke(service, location, type, rarity);
                if (result instanceof LivingEntity livingEntity) {
                    return livingEntity;
                }
            }
        } catch (Throwable ex) {
            String details = ex.getCause() != null ? ex.getCause().toString() : ex.toString();
            plugin.getLogger().warning("Ошибка спавна через MobsRarity: " + details);
        }
        return null;
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

            Object[] args = new Object[] {
                    adaptNumeric(minX, createOrUpdateSpawnZone.getParameterTypes()[2]),
                    adaptNumeric(minY, createOrUpdateSpawnZone.getParameterTypes()[3]),
                    adaptNumeric(minZ, createOrUpdateSpawnZone.getParameterTypes()[4]),
                    adaptNumeric(maxX, createOrUpdateSpawnZone.getParameterTypes()[5]),
                    adaptNumeric(maxY, createOrUpdateSpawnZone.getParameterTypes()[6]),
                    adaptNumeric(maxZ, createOrUpdateSpawnZone.getParameterTypes()[7]),
                    adaptNumeric(amountPerCycle, createOrUpdateSpawnZone.getParameterTypes()[10]),
                    adaptNumeric(cycleIntervalSeconds, createOrUpdateSpawnZone.getParameterTypes()[11]),
                    adaptNumeric(cycles, createOrUpdateSpawnZone.getParameterTypes()[12])
            };

            createOrUpdateSpawnZone.invoke(service, zoneId, world,
                    args[0], args[1], args[2], args[3], args[4], args[5],
                    type, rarity, args[6], args[7], args[8]);
        } catch (Throwable ex) {
            plugin.getLogger().warning("Не удалось создать spawn-zone в MobsRarity: " + ex.getMessage());
        }
    }

    private Method findSpawnZoneMethod(Class<?> serviceClass) {
        for (Method method : serviceClass.getMethods()) {
            if (!method.getName().equals("createOrUpdateSpawnZone")) {
                continue;
            }
            if (method.getParameterCount() != 13) {
                continue;
            }
            Class<?>[] params = method.getParameterTypes();
            if (params[0] != String.class || params[1] != World.class || params[8] != EntityType.class || params[9] != String.class) {
                continue;
            }
            return method;
        }
        return null;
    }

    private Object adaptNumeric(int value, Class<?> targetType) {
        if (targetType == int.class || targetType == Integer.class) {
            return value;
        }
        if (targetType == long.class || targetType == Long.class) {
            return (long) value;
        }
        if (targetType == short.class || targetType == Short.class) {
            return (short) value;
        }
        if (targetType == byte.class || targetType == Byte.class) {
            return (byte) value;
        }
        return value;
    }
}
