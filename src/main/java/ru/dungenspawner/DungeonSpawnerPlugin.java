package ru.dungenspawner;

import org.bukkit.plugin.java.JavaPlugin;
import ru.dungenspawner.command.SpawnDungeonCommand;
import ru.dungenspawner.listener.DungeonListener;
import ru.dungenspawner.manager.DungeonManager;
import ru.dungenspawner.placeholder.DungeonPlaceholderExpansion;
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
        dungeonManager.startTimerWatcher();

        getServer().getPluginManager().registerEvents(new DungeonListener(dungeonManager), this);
        if (getCommand("spawndungeon") != null) {
            SpawnDungeonCommand spawnDungeonCommand = new SpawnDungeonCommand(dungeonManager, getConfig());
            getCommand("spawndungeon").setExecutor(spawnDungeonCommand);
            getCommand("spawndungeon").setTabCompleter(spawnDungeonCommand);
        }

        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new DungeonPlaceholderExpansion(dungeonManager).register();
            getLogger().info("PlaceholderAPI подключен: placeholders зарегистрированы.");
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
