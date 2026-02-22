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
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import ru.dungenspawner.model.ActiveDungeon;
import ru.dungenspawner.service.EconomyBridge;
import ru.dungenspawner.service.MobsRarityBridge;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class DungeonManager {
    private final JavaPlugin plugin;
    private final MobsRarityBridge mobsRarityBridge;
    private final EconomyBridge economyBridge;
    private final Map<String, ActiveDungeon> activeDungeons = new HashMap<>();
    private final Map<UUID, String> mobToDungeon = new HashMap<>();

    public DungeonManager(JavaPlugin plugin, MobsRarityBridge mobsRarityBridge, EconomyBridge economyBridge) {
        this.plugin = plugin;
        this.mobsRarityBridge = mobsRarityBridge;
        this.economyBridge = economyBridge;
    }

    public void scheduleRandomDailySpawns() {
        scheduleRandomDailySpawns(0L);
    }

    public void startTimerWatcher() {
        new BukkitRunnable() {
            @Override
            public void run() {
                List<ActiveDungeon> expired = new ArrayList<>();
                List<ActiveDungeon> cleared = new ArrayList<>();
                for (ActiveDungeon dungeon : activeDungeons.values()) {
                    recalculateAliveMobs(dungeon);
                    spawnOrUpdateTimerDisplay(dungeon);
                    if (dungeon.getRemainingMillis() <= 0L) {
                        expired.add(dungeon);
                        continue;
                    }
                    if (dungeon.getMobs().isEmpty()) {
                        cleared.add(dungeon);
                    }
                }
                for (ActiveDungeon dungeon : cleared) {
                    rewardDungeonClearers(dungeon);
                    String clearMessage = buildClearMessage(dungeon);
                    Bukkit.broadcastMessage(prefix() + clearMessage);
                    removeDungeon(dungeon, false, clearMessage);
                }
                for (ActiveDungeon dungeon : expired) {
                    expireDungeon(dungeon);
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void scheduleRandomDailySpawns(long initialDelayTicks) {
        new BukkitRunnable() {
            @Override
            public void run() {
                FileConfiguration config = plugin.getConfig();
                int minPerDay = Math.max(0, config.getInt("daily-spawns.min", 2));
                int maxPerDay = Math.max(minPerDay, config.getInt("daily-spawns.max", 3));
                int eventsCount = ThreadLocalRandom.current().nextInt(minPerDay, maxPerDay + 1);
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
        return spawnDungeon(forcedRarity, null);
    }

    public boolean spawnDungeon(String forcedRarity, Location forcedBase) {
        FileConfiguration config = plugin.getConfig();
        World world = Bukkit.getWorld(config.getString("world", "world"));
        if (world == null) {
            plugin.getLogger().warning("Мир для спавна данжа не найден.");
            return false;
        }

        int maxActive = config.getInt("max-active-dungeons", 5);
        if (activeDungeons.size() >= maxActive) {
            plugin.getLogger().warning("Достигнут лимит активных данжей: " + maxActive);
            return false;
        }

        List<String> rarities = config.getStringList("rarity-order");
        if (rarities.isEmpty()) {
            plugin.getLogger().warning("Список rarity-order пуст.");
            return false;
        }

        String rarity = forcedRarity != null
                ? forcedRarity.toLowerCase(Locale.ROOT)
                : pickRandomRarityByWeight(rarities, config);
        String bossRarity = getNextRarity(rarities, rarity);

        Location base = forcedBase != null
                ? findGroundLocationNear(forcedBase)
                : findGroundLocation(world,
                config.getInt("spawn-center-x", 0),
                config.getInt("spawn-center-z", 0),
                config.getInt("spawn-radius", 2500));

        if (base == null) {
            plugin.getLogger().warning("Не удалось подобрать локацию на земле для данжа.");
            return false;
        }

        File schematicFile = resolveSchematicFile(config.getString("schematic-path", "simple-church.schematic"));
        Clipboard clipboard = loadClipboard(schematicFile);
        if (clipboard == null) {
            plugin.getLogger().warning("Не удалось загрузить схему данжа. Проверен путь: " + schematicFile.getPath());
            return false;
        }

        BlockVector3 origin = clipboard.getOrigin();
        BlockVector3 min = clipboard.getMinimumPoint();
        BlockVector3 max = clipboard.getMaximumPoint();
        int minX = base.getBlockX() + (min.getX() - origin.getX());
        int minY = base.getBlockY() + (min.getY() - origin.getY());
        int minZ = base.getBlockZ() + (min.getZ() - origin.getZ());
        int maxX = base.getBlockX() + (max.getX() - origin.getX());
        int maxY = base.getBlockY() + (max.getY() - origin.getY());
        int maxZ = base.getBlockZ() + (max.getZ() - origin.getZ());

        long ttl = Math.max(1L, config.getLong("dungeon-lifetime-seconds", 600));
        String id = "dungeon_" + System.currentTimeMillis();
        ActiveDungeon dungeon = new ActiveDungeon(id, rarity, bossRarity, world,
                minX, minY, minZ, maxX, maxY, maxZ,
                System.currentTimeMillis() + ttl * 1000L);
        snapshotRegion(dungeon);

        pasteClipboard(clipboard, base);
        activeDungeons.put(id, dungeon);

        if (!spawnMobsForDungeon(dungeon)) {
            activeDungeons.remove(id);
            restoreOriginalTerrain(dungeon);
            return false;
        }

        spawnOrUpdateTimerDisplay(dungeon);

        Bukkit.broadcastMessage(prefix() + "Появился данж редкости §e" + rarity + "§r в мире §f" + world.getName()
                + " §rкоординаты: §bX=" + base.getBlockX() + " Y=" + base.getBlockY() + " Z=" + base.getBlockZ()
                + " §7(таймер: " + formatDuration(dungeon.getRemainingMillis()) + ")");
        return true;
    }

    public boolean removeDungeonById(String dungeonId) {
        ActiveDungeon dungeon = activeDungeons.get(dungeonId);
        if (dungeon == null) {
            return false;
        }
        removeDungeon(dungeon, true, "удален администратором");
        return true;
    }

    public int removeAllDungeons() {
        List<ActiveDungeon> all = new ArrayList<>(activeDungeons.values());
        for (ActiveDungeon dungeon : all) {
            removeDungeon(dungeon, true, "удален администратором");
        }
        return all.size();
    }

    private boolean spawnMobsForDungeon(ActiveDungeon dungeon) {
        FileConfiguration config = plugin.getConfig();
        if (!mobsRarityBridge.isAvailable()) {
            plugin.getLogger().warning("MobsRarity API недоступен, спавн данжа отменен.");
            return false;
        }

        int minMobs = config.getInt("mobs-per-dungeon-min", 10);
        int maxMobs = config.getInt("mobs-per-dungeon-max", 15);
        int mobCount = ThreadLocalRandom.current().nextInt(minMobs, maxMobs + 1);
        List<EntityType> pool = parseEntityTypes(config.getStringList("rarity-mobs." + dungeon.getRarity()));
        if (pool.isEmpty()) {
            pool = List.of(EntityType.ZOMBIE);
        }

        for (int i = 0; i < mobCount; i++) {
            Location location = randomLocationInside(dungeon);
            SpawnResult mobResult = trySpawnFromPool(location, pool, dungeon.getRarity());
            if (mobResult == null || mobResult.entity() == null) {
                plugin.getLogger().warning("Не удалось заспавнить моба через MobsRarity (rarity=" + dungeon.getRarity() + "), данж будет откатан.");
                return false;
            }
            LivingEntity mob = mobResult.entity();
            prepareDungeonMob(mob, "§6");
            dungeon.getMobs().add(mob.getUniqueId());
            mobToDungeon.put(mob.getUniqueId(), dungeon.getId());
        }

        List<EntityType> bossPool = parseEntityTypes(config.getStringList("rarity-mobs." + dungeon.getBossRarity()));
        if (bossPool.isEmpty()) {
            bossPool = List.of(EntityType.WITHER_SKELETON);
        }
        SpawnResult bossResult = trySpawnFromPool(randomLocationInside(dungeon), bossPool, dungeon.getBossRarity());
        if (bossResult == null || bossResult.entity() == null) {
            plugin.getLogger().warning("Не удалось заспавнить босса через MobsRarity (rarity=" + dungeon.getBossRarity() + "), данж будет откатан.");
            return false;
        }
        LivingEntity boss = bossResult.entity();
        prepareDungeonMob(boss, "§c");
        dungeon.getMobs().add(boss.getUniqueId());
        mobToDungeon.put(boss.getUniqueId(), dungeon.getId());

        mobsRarityBridge.createOrUpdateSpawnZone(
                dungeon.getId(), dungeon.getWorld(),
                dungeon.getMinX(), dungeon.getMinY(), dungeon.getMinZ(),
                dungeon.getMaxX(), dungeon.getMaxY(), dungeon.getMaxZ(),
                pool.isEmpty() ? EntityType.ZOMBIE : pool.get(0), dungeon.getRarity(), 3, 45, 3
        );

        plugin.getLogger().info("Заспавнен данж " + dungeon.getId() + " с " + dungeon.getMobs().size() + " мобами.");
        return true;
    }

    public void onTrackedMobDeath(UUID mobId, Player killer) {
        String dungeonId = mobToDungeon.remove(mobId);
        if (dungeonId == null) {
            return;
        }
        ActiveDungeon dungeon = activeDungeons.get(dungeonId);
        if (dungeon == null) {
            return;
        }
        dungeon.getMobs().remove(mobId);
        if (killer != null) {
            dungeon.getClearingPlayers().add(killer.getName());
            dungeon.getClearingPlayerIds().putIfAbsent(killer.getName(), killer.getUniqueId());
            dungeon.getKillsByPlayer().merge(killer.getName(), 1, Integer::sum);
        }

        recalculateAliveMobs(dungeon);
        int left = dungeon.getMobs().size();

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (dungeon.contains(player.getLocation())) {
                player.sendMessage(prefix() + "В данже осталось мобов: §c" + left);
            }
        }

        if (left == 0) {
            rewardDungeonClearers(dungeon);
            String clearMessage = buildClearMessage(dungeon);
            Bukkit.broadcastMessage(prefix() + clearMessage);
            removeDungeon(dungeon, false, clearMessage);
        }
    }

    private void expireDungeon(ActiveDungeon dungeon) {
        Bukkit.broadcastMessage(prefix() + "Таймер данжа §e" + dungeon.getId() + "§r истек. Данж разрушается.");
        removeDungeon(dungeon, true, "таймер истек");
    }

    private void removeDungeon(ActiveDungeon dungeon, boolean despawnMobs, String reason) {
        activeDungeons.remove(dungeon.getId());
        if (despawnMobs) {
            despawnDungeonMobs(dungeon);
        }
        removeTimerDisplay(dungeon);
        startDecayAndRestore(dungeon);
        plugin.getLogger().info("Данж " + dungeon.getId() + " удален: " + reason);
    }

    private void despawnDungeonMobs(ActiveDungeon dungeon) {
        Set<UUID> tracked = Set.copyOf(dungeon.getMobs());
        for (UUID mobId : tracked) {
            Entity entity = Bukkit.getEntity(mobId);
            if (entity != null && entity.isValid()) {
                entity.remove();
            }
            dungeon.getMobs().remove(mobId);
            mobToDungeon.remove(mobId);
        }
    }

    private void startDecayAndRestore(ActiveDungeon dungeon) {
        int steps = Math.max(1, plugin.getConfig().getInt("decay-steps", 20));
        long interval = Math.max(1L, plugin.getConfig().getLong("decay-interval-ticks", 40L));
        List<Long> keys = new ArrayList<>(dungeon.getOriginalBlocks().keySet());
        Collections.shuffle(keys);

        if (keys.isEmpty()) {
            return;
        }

        int blocksPerStep = Math.max(1, (int) Math.ceil(keys.size() / (double) steps));

        new BukkitRunnable() {
            int index = 0;

            @Override
            public void run() {
                if (index >= keys.size()) {
                    cancel();
                    return;
                }

                World world = dungeon.getWorld();
                int end = Math.min(index + blocksPerStep, keys.size());
                for (int i = index; i < end; i++) {
                    long key = keys.get(i);
                    int x = unpackX(key);
                    int y = unpackY(key);
                    int z = unpackZ(key);
                    ActiveDungeon.SavedBlock saved = dungeon.getOriginalBlocks().get(key);
                    if (saved == null || saved.material() == Material.BEDROCK) {
                        continue;
                    }

                    Block block = world.getBlockAt(x, y, z);
                    if (block.getType() != saved.material()) {
                        block.setBlockData(saved.blockData(), false);
                        world.playSound(block.getLocation().add(0.5, 0.5, 0.5),
                                Sound.BLOCK_STONE_BREAK, SoundCategory.BLOCKS, 0.75f, 0.85f + ThreadLocalRandom.current().nextFloat() * 0.35f);
                    }
                }

                index = end;
            }
        }.runTaskTimer(plugin, interval, interval);
    }

    private void snapshotRegion(ActiveDungeon dungeon) {
        World world = dungeon.getWorld();
        for (int x = dungeon.getMinX(); x <= dungeon.getMaxX(); x++) {
            for (int y = dungeon.getMinY(); y <= dungeon.getMaxY(); y++) {
                for (int z = dungeon.getMinZ(); z <= dungeon.getMaxZ(); z++) {
                    BlockData data = world.getBlockAt(x, y, z).getBlockData().clone();
                    dungeon.getOriginalBlocks().put(pack(x, y, z), new ActiveDungeon.SavedBlock(data.getMaterial(), data));
                }
            }
        }
    }


    private void recalculateAliveMobs(ActiveDungeon dungeon) {
        Set<UUID> alive = dungeon.getMobs().stream()
                .filter(mobId -> {
                    Entity entity = Bukkit.getEntity(mobId);
                    return entity instanceof LivingEntity living && living.isValid() && !living.isDead();
                })
                .collect(Collectors.toSet());
        dungeon.getMobs().clear();
        dungeon.getMobs().addAll(alive);
        mobToDungeon.entrySet().removeIf(entry -> entry.getValue().equals(dungeon.getId()) && !alive.contains(entry.getKey()));
    }

    private void restoreOriginalTerrain(ActiveDungeon dungeon) {
        for (Map.Entry<Long, ActiveDungeon.SavedBlock> entry : dungeon.getOriginalBlocks().entrySet()) {
            ActiveDungeon.SavedBlock saved = entry.getValue();
            if (saved.material() == Material.BEDROCK) {
                continue;
            }
            long key = entry.getKey();
            dungeon.getWorld().getBlockAt(unpackX(key), unpackY(key), unpackZ(key)).setBlockData(saved.blockData(), false);
        }
    }

    private long pack(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFFL);
    }

    private int unpackX(long packed) {
        return (int) (packed << 0 >> 38);
    }

    private int unpackY(long packed) {
        return (int) (packed & 0xFFFL);
    }

    private int unpackZ(long packed) {
        return (int) (packed << 26 >> 38);
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

    private Location findGroundLocationNear(Location source) {
        World world = source.getWorld();
        if (world == null) {
            return null;
        }

        int x = source.getBlockX();
        int z = source.getBlockZ();
        int y = world.getHighestBlockYAt(x, z) + 1;
        if (!world.getBlockAt(x, y - 1, z).getType().isSolid()) {
            return null;
        }
        return new Location(world, x, y, z);
    }

    private File resolveSchematicFile(String configuredPath) {
        List<File> candidates = new ArrayList<>();

        File configured = new File(configuredPath);
        if (configured.isAbsolute()) {
            candidates.add(configured);
        } else {
            candidates.add(configured);
            candidates.add(new File(Bukkit.getWorldContainer(), configuredPath));
        }

        String fileName = configured.getName().isBlank() ? "simple-church.schematic" : configured.getName();
        candidates.add(new File(Bukkit.getWorldContainer(), "plugins/WorldEdit/schematics/" + fileName));
        candidates.add(new File(plugin.getDataFolder(), "schematics/" + fileName));

        for (File candidate : candidates) {
            if (candidate.exists() && candidate.isFile()) {
                return candidate;
            }
        }

        return candidates.get(0);
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
        Location valid = findValidMobSpawnLocation(dungeon);
        if (valid != null) {
            return valid;
        }
        Location center = centerLocation(dungeon);
        return new Location(dungeon.getWorld(), center.getX(), dungeon.getMinY() + 1, center.getZ());
    }


    private Location findValidMobSpawnLocation(ActiveDungeon dungeon) {
        World world = dungeon.getWorld();
        int minY = dungeon.getMinY();
        int maxY = Math.max(minY + 1, dungeon.getMaxY() - 1);
        for (int attempt = 0; attempt < 80; attempt++) {
            int x = ThreadLocalRandom.current().nextInt(dungeon.getMinX(), dungeon.getMaxX() + 1);
            int z = ThreadLocalRandom.current().nextInt(dungeon.getMinZ(), dungeon.getMaxZ() + 1);
            int y = ThreadLocalRandom.current().nextInt(minY, maxY + 1);
            if (isValidSpawnSpot(world, x, y, z)) {
                return new Location(world, x + 0.5, y, z + 0.5);
            }
        }

        for (int y = minY; y <= maxY; y++) {
            for (int x = dungeon.getMinX(); x <= dungeon.getMaxX(); x++) {
                for (int z = dungeon.getMinZ(); z <= dungeon.getMaxZ(); z++) {
                    if (isValidSpawnSpot(world, x, y, z)) {
                        return new Location(world, x + 0.5, y, z + 0.5);
                    }
                }
            }
        }
        return null;
    }

    private boolean isValidSpawnSpot(World world, int x, int y, int z) {
        if (y <= world.getMinHeight() || y >= world.getMaxHeight() - 2) {
            return false;
        }
        Material feet = world.getBlockAt(x, y, z).getType();
        Material head = world.getBlockAt(x, y + 1, z).getType();
        Material below = world.getBlockAt(x, y - 1, z).getType();
        if (!feet.isAir() || !head.isAir() || !below.isSolid()) {
            return false;
        }

        return hasCeilingNearby(world, x, y, z);
    }

    private boolean hasCeilingNearby(World world, int x, int y, int z) {
        int maxScan = 6;
        int maxY = world.getMaxHeight() - 1;
        for (int offset = 2; offset <= maxScan; offset++) {
            int checkY = y + offset;
            if (checkY >= maxY) {
                break;
            }
            if (!world.getBlockAt(x, checkY, z).getType().isAir()) {
                return true;
            }
        }
        return false;
    }

    private void prepareDungeonMob(LivingEntity entity, String color) {
        entity.setCustomName(withDungeonPrefix(entity.getCustomName(), color));
        entity.setCustomNameVisible(true);
        entity.setRemoveWhenFarAway(false);
        entity.setPersistent(true);
    }

    private String withDungeonPrefix(String originalName, String color) {
        if (originalName == null || originalName.isBlank()) {
            return color + "[Данж]";
        }
        return color + "[Данж] §r" + originalName;
    }

    private String buildClearMessage(ActiveDungeon dungeon) {
        int playersCount = dungeon.getClearingPlayers().size();
        String who;
        if (playersCount == 1) {
            who = "игроком";
        } else if (playersCount > 1) {
            who = "игроками";
        } else {
            who = "неизвестным игроком";
        }

        String clearers = dungeon.getClearingPlayers().isEmpty()
                ? "неизвестно"
                : String.join(", ", dungeon.getClearingPlayers());

        String rating = dungeon.getKillsByPlayer().entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .map(entry -> entry.getKey() + " - " + entry.getValue())
                .collect(Collectors.joining(", "));
        if (rating.isBlank()) {
            rating = "нет данных";
        }

        return "Данж §e" + dungeon.getId() + " §rзачищен " + who + ": §a" + clearers + " §7| Рейтинг: §f" + rating;
    }


    private String pickRandomRarityByWeight(List<String> rarities, FileConfiguration config) {
        Map<String, Integer> weights = new HashMap<>();
        int totalWeight = 0;

        for (String rarity : rarities) {
            int weight = Math.max(0, config.getInt("rarity-spawn-weights." + rarity, 1));
            if (weight == 0) {
                continue;
            }
            weights.put(rarity, weight);
            totalWeight += weight;
        }

        if (totalWeight <= 0) {
            return rarities.get(ThreadLocalRandom.current().nextInt(rarities.size()));
        }

        int roll = ThreadLocalRandom.current().nextInt(totalWeight);
        int cumulative = 0;
        for (String rarity : rarities) {
            Integer weight = weights.get(rarity);
            if (weight == null) {
                continue;
            }
            cumulative += weight;
            if (roll < cumulative) {
                return rarity;
            }
        }

        return rarities.get(rarities.size() - 1);
    }

    private void rewardDungeonClearers(ActiveDungeon dungeon) {
        int reward = Math.max(0, plugin.getConfig().getInt("rarity-clear-rewards." + dungeon.getRarity(), 0));
        if (reward <= 0 || !economyBridge.isAvailable()) {
            return;
        }

        for (Map.Entry<String, UUID> entry : dungeon.getClearingPlayerIds().entrySet()) {
            Player player = Bukkit.getPlayer(entry.getValue());
            if (player == null || !player.isOnline()) {
                continue;
            }

            if (economyBridge.depositShards(player, reward)) {
                player.sendMessage(prefix() + "Вы получили §b" + reward + "§r шардов за зачистку данжа §e" + dungeon.getId());
            }
        }
    }

    private SpawnResult trySpawnFromPool(Location location, List<EntityType> pool, String rarity) {
        List<EntityType> order = new ArrayList<>(pool);
        Collections.shuffle(order);
        for (EntityType type : order) {
            LivingEntity entity = mobsRarityBridge.spawnRarityMob(location, type, rarity);
            if (entity != null) {
                return new SpawnResult(entity);
            }
        }
        return null;
    }

    private record SpawnResult(LivingEntity entity) {}

    public Optional<ActiveDungeon> getDungeonByMob(UUID mobId) {
        String id = mobToDungeon.get(mobId);
        return Optional.ofNullable(id).map(activeDungeons::get);
    }

    public List<ActiveDungeon> getActiveDungeons() {
        return new ArrayList<>(activeDungeons.values());
    }

    public int getActiveDungeonCount() {
        return activeDungeons.size();
    }

    public Optional<ActiveDungeon> getNearestDungeon(Location location) {
        if (location == null || location.getWorld() == null) {
            return Optional.empty();
        }
        return activeDungeons.values().stream()
                .filter(d -> d.getWorld().equals(location.getWorld()))
                .min(Comparator.comparingDouble(d -> centerLocation(d).distanceSquared(location)));
    }

    public String formatDuration(long millis) {
        long totalSeconds = Math.max(0L, millis / 1000L);
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return String.format("%02d:%02d", minutes, seconds);
    }

    public String formatAllDungeonTimers() {
        List<ActiveDungeon> sorted = getActiveDungeons();
        sorted.sort(Comparator.comparing(ActiveDungeon::getId));
        List<String> parts = new ArrayList<>();
        for (ActiveDungeon dungeon : sorted) {
            parts.add(dungeon.getId() + "=" + formatDuration(dungeon.getRemainingMillis()));
        }
        return parts.isEmpty() ? "none" : String.join(", ", parts);
    }

    private Location centerLocation(ActiveDungeon dungeon) {
        double x = (dungeon.getMinX() + dungeon.getMaxX()) / 2.0;
        double y = (dungeon.getMinY() + dungeon.getMaxY()) / 2.0;
        double z = (dungeon.getMinZ() + dungeon.getMaxZ()) / 2.0;
        return new Location(dungeon.getWorld(), x, y, z);
    }

    private void spawnOrUpdateTimerDisplay(ActiveDungeon dungeon) {
        Location center = centerLocation(dungeon).add(0.0, 1.1, 0.0);
        String text = "§6§lДАНЖ " + dungeon.getId() + " §f" + formatDuration(dungeon.getRemainingMillis());

        Entity existing = dungeon.getTimerDisplayId() == null ? null : Bukkit.getEntity(dungeon.getTimerDisplayId());
        if (existing instanceof TextDisplay display && display.isValid()) {
            display.teleport(center);
            display.setText(text);
            return;
        }

        TextDisplay display = dungeon.getWorld().spawn(center, TextDisplay.class, td -> {
            td.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
            td.setSeeThrough(true);
            td.setShadowed(true);
            td.setDefaultBackground(false);
            td.setText(text);
            td.setLineWidth(300);
            td.setViewRange(48f);
        });
        dungeon.setTimerDisplayId(display.getUniqueId());
    }

    private void removeTimerDisplay(ActiveDungeon dungeon) {
        if (dungeon.getTimerDisplayId() == null) {
            return;
        }
        Entity entity = Bukkit.getEntity(dungeon.getTimerDisplayId());
        if (entity != null && entity.isValid()) {
            entity.remove();
        }
        dungeon.setTimerDisplayId(null);
    }

    private String prefix() {
        return plugin.getConfig().getString("announce-prefix", "§6[Данжи] §r");
    }
}
