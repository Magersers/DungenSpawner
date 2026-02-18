package ru.dungenspawner.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.Location;
import ru.dungenspawner.manager.DungeonManager;

import java.util.List;

public class SpawnDungeonCommand implements CommandExecutor {
    private final DungeonManager dungeonManager;
    private final FileConfiguration config;

    public SpawnDungeonCommand(DungeonManager dungeonManager, FileConfiguration config) {
        this.dungeonManager = dungeonManager;
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String forcedRarity = null;
        if (args.length > 0) {
            List<String> rarityOrder = config.getStringList("rarity-order");
            if (!rarityOrder.contains(args[0].toLowerCase())) {
                sender.sendMessage("§cНеизвестная редкость. Доступно: " + String.join(", ", rarityOrder));
                return true;
            }
            forcedRarity = args[0].toLowerCase();
        }

        Location forcedLocation = null;
        if (sender instanceof Player player) {
            forcedLocation = player.getLocation();
        }

        boolean spawned = dungeonManager.spawnDungeon(forcedRarity, forcedLocation);
        sender.sendMessage(spawned ? "§aДанж успешно заспавнен." : "§cНе удалось заспавнить данж. Проверьте консоль.");
        return true;
    }
}
