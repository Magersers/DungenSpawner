package ru.dungenspawner.manager;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operation;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import ru.dungenspawner.model.ActiveDungeon;
import ru.dungenspawner.service.MobsRarityBridge;

import java.io.File;
import java.io.FileInputStream;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class DungeonManager {
    private final JavaPlugin plugin;
    private final MobsRarityBridge mobsRarityBridge;
    private final Map<String, ActiveDungeon> activeDungeons = new HashMap<>();
    private final Map<UUID, String> mobToDungeon = new HashMap<>();

    public DungeonManager(JavaPlugin plugin, MobsRarityBridge mobsRarityBridge) {
        this.plugin = plugin;
        this.mobsRarityBridge = mobsRarityBridge;
    }

    public void scheduleRandomDailySpawns() {
        scheduleRandomDailySpawns(0L);
    }

    private void scheduleRandomDailySpawns(long initialDelayTicks) {
        new BukkitRunnable() {
            @Override
            public void run() {
                int eventsCount = ThreadLocalRandom.current().nextInt(2, 4);
                for (int i = 0; i < eventsCount; i++) {
                    long delay = ThreadLocalRandom.current().nextLong(20L, 24L * 60L * 60L * 20L);
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            spawnDungeon(null);
                        }
                    }.runTaskLater(plugin, delay);
                }
                scheduleRandomDailySpawns(24L * 60L * 60L * 20L);
            }
        }.runTaskLater(plugin, initialDelayTicks);
    }

    public boolean spawnDungeon(String forcedRarity) {
        FileConfiguration config = plugin.getConfig();
        World world = Bukkit.getWorld(config.getString("world", "world"));
        if (world == null) {
            plugin.getLogger().warning("Мир для спавна данжа не найден.");
            return false;
        }

        List<String> rarities = config.getStringList("rarity-order");
        if (rarities.isEmpty()) {
            plugin.getLogger().warning("Список rarity-order пуст.");
            return false;
        }

        String rarity = forcedRarity != null ? forcedRarity : rarities.get(ThreadLocalRandom.current().nextInt(rarities.size()));
        String bossRarity = getNextRarity(rarities, rarity);

        Location base = findGroundLocation(world,
                config.getInt("spawn-center-x", 0),
                config.getInt("spawn-center-z", 0),
                config.getInt("spawn-radius", 2500));

        if (base == null) {
            plugin.getLogger().warning("Не удалось подобрать локацию на земле для данжа.");
            return false;
        }

        Clipboard clipboard = loadClipboard(new File(config.getString("schematic-path")));
        if (clipboard == null) {
            plugin.getLogger().warning("Не удалось загрузить схему данжа.");
            return false;
        }

        pasteClipboard(clipboard, base);

        BlockVector3 origin = clipboard.getOrigin();
        BlockVector3 min = clipboard.getMinimumPoint();
        BlockVector3 max = clipboard.getMaximumPoint();
        int minX = base.getBlockX() + (min.getX() - origin.getX());
        int minY = base.getBlockY() + (min.getY() - origin.getY());
        int minZ = base.getBlockZ() + (min.getZ() - origin.getZ());
        int maxX = base.getBlockX() + (max.getX() - origin.getX());
        int maxY = base.getBlockY() + (max.getY() - origin.getY());
        int maxZ = base.getBlockZ() + (max.getZ() - origin.getZ());

        String id = "dungeon_" + System.currentTimeMillis();
        ActiveDungeon dungeon = new ActiveDungeon(id, rarity, bossRarity, world, minX, minY, minZ, maxX, maxY, maxZ);
        activeDungeons.put(id, dungeon);

        spawnMobsForDungeon(dungeon, base);
        Bukkit.broadcastMessage(prefix() + "Появился данж редкости §e" + rarity + "§r в мире §f" + world.getName());
        return true;
    }

    private void spawnMobsForDungeon(ActiveDungeon dungeon, Location base) {
        FileConfiguration config = plugin.getConfig();
        int minMobs = config.getInt("mobs-per-dungeon-min", 10);
        int maxMobs = config.getInt("mobs-per-dungeon-max", 15);
        int mobCount = ThreadLocalRandom.current().nextInt(minMobs, maxMobs + 1);
        List<EntityType> pool = parseEntityTypes(config.getStringList("rarity-mobs." + dungeon.getRarity()));
        if (pool.isEmpty()) {
            pool = List.of(EntityType.ZOMBIE);
        }

        for (int i = 0; i < mobCount; i++) {
            EntityType type = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
            Location location = randomLocationInside(dungeon);
            LivingEntity mob = mobsRarityBridge.spawnRarityMob(location, type, dungeon.getRarity());
            dungeon.getMobs().add(mob.getUniqueId());
            mobToDungeon.put(mob.getUniqueId(), dungeon.getId());
        }

        List<EntityType> bossPool = parseEntityTypes(config.getStringList("rarity-mobs." + dungeon.getBossRarity()));
        EntityType bossType = bossPool.isEmpty() ? EntityType.WITHER_SKELETON : bossPool.get(ThreadLocalRandom.current().nextInt(bossPool.size()));
        LivingEntity boss = mobsRarityBridge.spawnRarityMob(randomLocationInside(dungeon), bossType, dungeon.getBossRarity());
        boss.setCustomName("§cБосс §7(" + dungeon.getBossRarity() + ")");
        boss.setCustomNameVisible(true);
        dungeon.getMobs().add(boss.getUniqueId());
        mobToDungeon.put(boss.getUniqueId(), dungeon.getId());

        mobsRarityBridge.createOrUpdateSpawnZone(
                dungeon.getId(), dungeon.getWorld(),
                dungeon.getMinX(), dungeon.getMinY(), dungeon.getMinZ(),
                dungeon.getMaxX(), dungeon.getMaxY(), dungeon.getMaxZ(),
                pool.get(0), dungeon.getRarity(), 3, 45, 3
        );

        plugin.getLogger().info("Заспавнен данж " + dungeon.getId() + " с " + dungeon.getMobs().size() + " мобами.");
    }

    public void onTrackedMobDeath(UUID mobId, Location deathLocation) {
        String dungeonId = mobToDungeon.remove(mobId);
        if (dungeonId == null) {
            return;
        }
        ActiveDungeon dungeon = activeDungeons.get(dungeonId);
        if (dungeon == null) {
            return;
        }
        dungeon.getMobs().remove(mobId);
        int left = dungeon.getMobs().size();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (dungeon.contains(player.getLocation())) {
                player.sendMessage(prefix() + "В данже осталось мобов: §c" + left);
            }
        }

        if (left == 0) {
            activeDungeons.remove(dungeon.getId());
            Bukkit.broadcastMessage(prefix() + "Данж §e" + dungeon.getId() + " §rзачищен!");
            startDecay(dungeon);
        }
    }

    private void startDecay(ActiveDungeon dungeon) {
        int steps = plugin.getConfig().getInt("decay-steps", 20);
        long interval = plugin.getConfig().getLong("decay-interval-ticks", 40L);

        new BukkitRunnable() {
            int current = 0;
            @Override
            public void run() {
                if (current++ >= steps) {
                    cancel();
                    return;
                }
                int blocksToBreak = ((dungeon.getMaxX() - dungeon.getMinX() + 1) * (dungeon.getMaxY() - dungeon.getMinY() + 1) * (dungeon.getMaxZ() - dungeon.getMinZ() + 1)) / steps;
                for (int i = 0; i < blocksToBreak; i++) {
                    int x = ThreadLocalRandom.current().nextInt(dungeon.getMinX(), dungeon.getMaxX() + 1);
                    int y = ThreadLocalRandom.current().nextInt(dungeon.getMinY(), dungeon.getMaxY() + 1);
                    int z = ThreadLocalRandom.current().nextInt(dungeon.getMinZ(), dungeon.getMaxZ() + 1);
                    Material material = dungeon.getWorld().getBlockAt(x, y, z).getType();
                    if (material != Material.AIR && material != Material.BEDROCK) {
                        dungeon.getWorld().getBlockAt(x, y, z).setType(Material.AIR, false);
                    }
                }
            }
        }.runTaskTimer(plugin, interval, interval);
    }

    private Location findGroundLocation(World world, int centerX, int centerZ, int radius) {
        for (int attempt = 0; attempt < 100; attempt++) {
            int x = centerX + ThreadLocalRandom.current().nextInt(-radius, radius + 1);
            int z = centerZ + ThreadLocalRandom.current().nextInt(-radius, radius + 1);
            int y = world.getHighestBlockYAt(x, z) + 1;
            Location location = new Location(world, x, y, z);
            if (world.getBlockAt(x, y - 1, z).getType().isSolid()) {
                return location;
            }
        }
        return null;
    }

    private void pasteClipboard(Clipboard clipboard, Location location) {
        try (EditSession editSession = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(location.getWorld()))) {
            Operation operation = new ClipboardHolder(clipboard)
                    .createPaste(editSession)
                    .to(BlockVector3.at(location.getBlockX(), location.getBlockY(), location.getBlockZ()))
                    .ignoreAirBlocks(false)
                    .build();
            Operations.complete(operation);
        } catch (Exception ex) {
            throw new IllegalStateException("Ошибка вставки schematic", ex);
        }
    }

    private Clipboard loadClipboard(File file) {
        try {
            ClipboardFormat format = ClipboardFormats.findByFile(file);
            if (format == null) {
                return null;
            }
            try (ClipboardReader reader = format.getReader(new FileInputStream(file))) {
                return reader.read();
            }
        } catch (Exception ex) {
            plugin.getLogger().warning("Ошибка чтения схемы: " + ex.getMessage());
            return null;
        }
    }

    private List<EntityType> parseEntityTypes(List<String> raw) {
        List<EntityType> result = new ArrayList<>();
        for (String item : raw) {
            try {
                result.add(EntityType.valueOf(item.toUpperCase(Locale.ROOT)));
            } catch (Exception ignored) {
            }
        }
        return result;
    }

    private String getNextRarity(List<String> order, String rarity) {
        int index = order.indexOf(rarity);
        if (index < 0 || index + 1 >= order.size()) {
            return order.get(order.size() - 1);
        }
        return order.get(index + 1);
    }

    private Location randomLocationInside(ActiveDungeon dungeon) {
        int x = ThreadLocalRandom.current().nextInt(dungeon.getMinX(), dungeon.getMaxX() + 1);
        int z = ThreadLocalRandom.current().nextInt(dungeon.getMinZ(), dungeon.getMaxZ() + 1);
        int y = ThreadLocalRandom.current().nextInt(dungeon.getMinY(), dungeon.getMaxY() + 1);
        return new Location(dungeon.getWorld(), x + 0.5, y, z + 0.5);
    }

    public Optional<ActiveDungeon> getDungeonByMob(UUID mobId) {
        String id = mobToDungeon.get(mobId);
        return Optional.ofNullable(id).map(activeDungeons::get);
    }

    private String prefix() {
        return plugin.getConfig().getString("announce-prefix", "§6[Данжи] §r");
    }
}
