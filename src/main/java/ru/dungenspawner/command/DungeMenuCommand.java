package ru.dungenspawner.command;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.dungenspawner.manager.DungeonManager;
import ru.dungenspawner.model.ActiveDungeon;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class DungeMenuCommand implements CommandExecutor {
    private final DungeonManager dungeonManager;

    public DungeMenuCommand(DungeonManager dungeonManager) {
        this.dungeonManager = dungeonManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cКоманда доступна только игроку.");
            return true;
        }

        openMainMenu(player);
        return true;
    }

    public void openMainMenu(Player player) {
        List<ActiveDungeon> dungeons = dungeonManager.getActiveDungeons();
        dungeons.sort(Comparator.comparing(ActiveDungeon::getId));

        int rows = Math.max(1, Math.min(6, ((dungeons.size() + 1) / 9) + 1));
        int size = rows * 9;
        MainMenuHolder holder = new MainMenuHolder();
        Inventory inventory = Bukkit.createInventory(holder, size, "§6Активные данжи");
        holder.setInventory(inventory);

        int slot = 0;
        for (ActiveDungeon dungeon : dungeons) {
            if (slot >= size - 1) {
                break;
            }
            inventory.setItem(slot++, buildDungeonItem(dungeon));
        }

        inventory.setItem(8, buildButton(Material.BARRIER, "§cВыход", List.of("§7Закрыть меню")));
        inventory.setItem(size - 1, buildButton(Material.BARRIER, "§cВыход", List.of("§7Закрыть меню")));
        player.openInventory(inventory);
    }

    private ItemStack buildDungeonItem(ActiveDungeon dungeon) {
        ItemStack item = new ItemStack(Material.ENDER_PEARL);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        meta.setDisplayName("§e" + dungeon.getId());
        List<String> lore = new ArrayList<>();
        lore.add("§7Редкость: §f" + dungeon.getRarity());
        lore.add("§7Таймер: §f" + dungeonManager.formatDuration(dungeon.getRemainingMillis()));
        lore.add("§7Мобов осталось: §f" + dungeon.getMobs().size());
        lore.add("§8Нажмите для телепорта меню");
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    public Inventory buildConfirmMenu(String dungeonId) {
        ConfirmMenuHolder holder = new ConfirmMenuHolder(dungeonId);
        Inventory inventory = Bukkit.createInventory(holder, 27, "§6Телепорт к данжу");
        holder.setInventory(inventory);
        inventory.setItem(11, buildButton(Material.LIME_CONCRETE, "§aДа", List.of("§7Телепортироваться рядом с данжем")));
        inventory.setItem(13, buildButton(Material.RED_CONCRETE, "§cНет", List.of("§7Вернуться к списку данжей")));
        inventory.setItem(26, buildButton(Material.BARRIER, "§cВыход", List.of("§7Закрыть меню")));
        return inventory;
    }

    private ItemStack buildButton(Material material, String title, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.setDisplayName(title);
        meta.setLore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public static class MainMenuHolder implements InventoryHolder {
        private Inventory inventory;

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }
    }

    public static class ConfirmMenuHolder implements InventoryHolder {
        private final String dungeonId;
        private Inventory inventory;

        public ConfirmMenuHolder(String dungeonId) {
            this.dungeonId = dungeonId;
        }

        public String dungeonId() {
            return dungeonId;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }

        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }
    }
}
