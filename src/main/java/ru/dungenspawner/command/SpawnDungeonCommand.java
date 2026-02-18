package ru.dungenspawner.command;

import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import ru.dungenspawner.manager.DungeonManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SpawnDungeonCommand implements CommandExecutor, TabCompleter {
    private final DungeonManager dungeonManager;
    private final FileConfiguration config;

    public SpawnDungeonCommand(DungeonManager dungeonManager, FileConfiguration config) {
        this.dungeonManager = dungeonManager;
        this.config = config;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("remove")) {
            return handleRemove(sender, args);
        }

        String forcedRarity = null;
        if (args.length > 0) {
            List<String> rarityOrder = config.getStringList("rarity-order");
            if (!rarityOrder.contains(args[0].toLowerCase())) {
                sender.sendMessage("§cНеизвестная редкость. Доступно: " + String.join(", ", rarityOrder));
                sender.sendMessage("§7Удаление: /spawndungeon remove <id|all>");
                return true;
            }
            forcedRarity = args[0].toLowerCase();
        }

        Location forcedLocation = null;
        if (sender instanceof Player player) {
            forcedLocation = player.getLocation();
        }

        boolean spawned = dungeonManager.spawnDungeon(forcedRarity, forcedLocation);
        sender.sendMessage(spawned ? "§aДанж успешно заспавнен." : "§cНе удалось заспавнить данж. Проверьте лимит/консоль.");
        return true;
    }

    private boolean handleRemove(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("§cИспользование: /spawndungeon remove <id|all>");
            return true;
        }

        if (args[1].equalsIgnoreCase("all")) {
            int removed = dungeonManager.removeAllDungeons();
            sender.sendMessage("§aУдалено данжей: " + removed);
            return true;
        }

        boolean removed = dungeonManager.removeDungeonById(args[1]);
        sender.sendMessage(removed ? "§aДанж удален: " + args[1] : "§cДанж не найден: " + args[1]);
        return true;
    }


    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> rarityOrder = config.getStringList("rarity-order");
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> variants = new ArrayList<>();
            for (String rarity : rarityOrder) {
                if (rarity.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    variants.add(rarity);
                }
            }
            if ("remove".startsWith(prefix)) {
                variants.add("remove");
            }
            return variants;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("remove")) {
            return List.of("all");
        }

        return List.of();
    }

}