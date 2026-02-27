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
                clear();
                return;
            }

            service = registration.getProvider();
            spawnRarityMob = serviceClass.getMethod("spawnRarityMob", Location.class, EntityType.class, String.class);
            createOrUpdateSpawnZone = findSpawnZoneMethod(serviceClass);

            if (createOrUpdateSpawnZone == null) {
                plugin.getLogger().warning("MobsRarity подключен без createOrUpdateSpawnZone: автозоны отключены, спавн работает.");
            } else {
                plugin.getLogger().info("MobsRarity API успешно подключен через ServicesManager.");
            }
        } catch (Throwable ex) {
            clear();
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

            Class<?>[] params = createOrUpdateSpawnZone.getParameterTypes();
            createOrUpdateSpawnZone.invoke(service,
                    zoneId,
                    world,
                    adaptNumber(minX, params[2]),
                    adaptNumber(minY, params[3]),
                    adaptNumber(minZ, params[4]),
                    adaptNumber(maxX, params[5]),
                    adaptNumber(maxY, params[6]),
                    adaptNumber(maxZ, params[7]),
                    type,
                    rarity,
                    adaptNumber(amountPerCycle, params[10]),
                    adaptNumber(cycleIntervalSeconds, params[11]),
                    adaptNumber(cycles, params[12])
            );
        } catch (Throwable ex) {
            String details = ex.getCause() != null ? ex.getCause().toString() : ex.toString();
            plugin.getLogger().warning("Не удалось создать spawn-zone в MobsRarity: " + details);
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

    private Object adaptNumber(int value, Class<?> targetType) {
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

    private void clear() {
        service = null;
        spawnRarityMob = null;
        createOrUpdateSpawnZone = null;
    }
}
