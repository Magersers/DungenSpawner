package ru.dungenspawner;

import org.bukkit.plugin.java.JavaPlugin;
import ru.dungenspawner.command.SpawnDungeonCommand;
import ru.dungenspawner.listener.DungeonListener;
import ru.dungenspawner.manager.DungeonManager;
import ru.dungenspawner.service.MobsRarityBridge;

import java.io.File;

public class DungeonSpawnerPlugin extends JavaPlugin {
    private DungeonManager dungeonManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        ensureDefaultFolders();

        MobsRarityBridge mobsRarityBridge = new MobsRarityBridge(this);
        mobsRarityBridge.hook();

        dungeonManager = new DungeonManager(this, mobsRarityBridge);
        dungeonManager.scheduleRandomDailySpawns();

        getServer().getPluginManager().registerEvents(new DungeonListener(dungeonManager), this);
        if (getCommand("spawndungeon") != null) {
            getCommand("spawndungeon").setExecutor(new SpawnDungeonCommand(dungeonManager, getConfig()));
        }

        getLogger().info("DungenSpawner включен.");
    }

    private void ensureDefaultFolders() {
        File schematicsDir = new File(getDataFolder(), "schematics");
        if (!schematicsDir.exists() && !schematicsDir.mkdirs()) {
            getLogger().warning("Не удалось создать папку schematics.");
        }
    }
}
