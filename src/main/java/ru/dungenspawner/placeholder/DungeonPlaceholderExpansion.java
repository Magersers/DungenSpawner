package ru.dungenspawner.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import ru.dungenspawner.manager.DungeonManager;
import ru.dungenspawner.model.ActiveDungeon;

import java.util.Optional;

public class DungeonPlaceholderExpansion extends PlaceholderExpansion {
    private final DungeonManager dungeonManager;

    public DungeonPlaceholderExpansion(DungeonManager dungeonManager) {
        this.dungeonManager = dungeonManager;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "dungenspawner";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Codex";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        return switch (params.toLowerCase()) {
            case "active_count" -> String.valueOf(dungeonManager.getActiveDungeonCount());
            case "all_timers" -> dungeonManager.formatAllDungeonTimers();
            case "nearest_timer" -> getNearestTimer(player);
            default -> null;
        };
    }

    private String getNearestTimer(OfflinePlayer player) {
        if (!(player instanceof Player online)) {
            return "none";
        }
        Optional<ActiveDungeon> nearest = dungeonManager.getNearestDungeon(online.getLocation());
        return nearest.map(dungeon -> dungeonManager.formatDuration(dungeon.getRemainingMillis())).orElse("none");
    }
}
