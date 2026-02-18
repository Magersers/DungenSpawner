package ru.dungenspawner.model;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ActiveDungeon {
    private final String id;
    private final String rarity;
    private final String bossRarity;
    private final World world;
    private final int minX;
    private final int minY;
    private final int minZ;
    private final int maxX;
    private final int maxY;
    private final int maxZ;
    private final long expiresAtMillis;
    private final Set<UUID> mobs = new HashSet<>();
    private final Set<String> clearingPlayers = new LinkedHashSet<>();
    private final Map<String, Integer> killsByPlayer = new LinkedHashMap<>();
    private final Map<Long, SavedBlock> originalBlocks = new HashMap<>();
    private UUID timerDisplayId;

    public ActiveDungeon(String id, String rarity, String bossRarity, World world,
                         int minX, int minY, int minZ, int maxX, int maxY, int maxZ,
                         long expiresAtMillis) {
        this.id = id;
        this.rarity = rarity;
        this.bossRarity = bossRarity;
        this.world = world;
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        this.maxX = maxX;
        this.maxY = maxY;
        this.maxZ = maxZ;
        this.expiresAtMillis = expiresAtMillis;
    }

    public String getId() { return id; }
    public String getRarity() { return rarity; }
    public String getBossRarity() { return bossRarity; }
    public World getWorld() { return world; }
    public int getMinX() { return minX; }
    public int getMinY() { return minY; }
    public int getMinZ() { return minZ; }
    public int getMaxX() { return maxX; }
    public int getMaxY() { return maxY; }
    public int getMaxZ() { return maxZ; }
    public long getExpiresAtMillis() { return expiresAtMillis; }
    public Set<UUID> getMobs() { return mobs; }
    public Set<String> getClearingPlayers() { return clearingPlayers; }
    public Map<String, Integer> getKillsByPlayer() { return killsByPlayer; }
    public Map<Long, SavedBlock> getOriginalBlocks() { return originalBlocks; }
    public UUID getTimerDisplayId() { return timerDisplayId; }
    public void setTimerDisplayId(UUID timerDisplayId) { this.timerDisplayId = timerDisplayId; }

    public long getRemainingMillis() {
        return Math.max(0L, expiresAtMillis - System.currentTimeMillis());
    }

    public boolean contains(Location location) {
        if (location.getWorld() == null || !location.getWorld().equals(world)) {
            return false;
        }
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    public record SavedBlock(Material material, BlockData blockData) {
    }
}
