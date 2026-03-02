package ru.dungenspawner.listener;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.dungenspawner.command.DungeMenuCommand;
import ru.dungenspawner.manager.DungeonManager;
import ru.dungenspawner.model.ActiveDungeon;

import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

public class DungeMenuListener implements Listener {
    private final DungeonManager dungeonManager;
    private final DungeMenuCommand dungeMenuCommand;

    public DungeMenuListener(DungeonManager dungeonManager, DungeMenuCommand dungeMenuCommand) {
        this.dungeonManager = dungeonManager;
        this.dungeMenuCommand = dungeMenuCommand;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Inventory inventory = event.getView().getTopInventory();
        if (inventory == null || inventory.getHolder() == null) {
            return;
        }

        if (inventory.getHolder() instanceof DungeMenuCommand.MainMenuHolder) {
            event.setCancelled(true);
            handleMainMenuClick(player, event.getCurrentItem());
            return;
        }

        if (inventory.getHolder() instanceof DungeMenuCommand.ConfirmMenuHolder confirmHolder) {
            event.setCancelled(true);
            handleConfirmClick(player, confirmHolder.dungeonId(), event.getCurrentItem());
        }
    }

    private void handleMainMenuClick(Player player, ItemStack clicked) {
        if (!isClickable(clicked)) {
            return;
        }

        if (clicked.getType() == Material.BARRIER) {
            player.closeInventory();
            return;
        }

        String dungeonId = extractDisplayName(clicked);
        if (dungeonId == null || dungeonId.isBlank()) {
            return;
        }

        Optional<ActiveDungeon> dungeon = dungeonManager.getActiveDungeons().stream()
                .filter(d -> d.getId().equals(dungeonId))
                .findFirst();

        if (dungeon.isEmpty()) {
            player.sendMessage("§cДанж больше не активен.");
            dungeMenuCommand.openMainMenu(player);
            return;
        }

        player.openInventory(dungeMenuCommand.buildConfirmMenu(dungeonId));
    }

    private void handleConfirmClick(Player player, String dungeonId, ItemStack clicked) {
        if (!isClickable(clicked)) {
            return;
        }

        Material type = clicked.getType();
        if (type == Material.BARRIER) {
            player.closeInventory();
            return;
        }

        if (type == Material.RED_CONCRETE) {
            dungeMenuCommand.openMainMenu(player);
            return;
        }

        if (type != Material.LIME_CONCRETE) {
            return;
        }

        Optional<ActiveDungeon> dungeonOptional = dungeonManager.getActiveDungeons().stream()
                .filter(d -> d.getId().equals(dungeonId))
                .findFirst();
        if (dungeonOptional.isEmpty()) {
            player.sendMessage("§cДанж больше не активен.");
            dungeMenuCommand.openMainMenu(player);
            return;
        }

        ActiveDungeon dungeon = dungeonOptional.get();
        Location target = findRandomLocationNearDungeon(dungeon, 100);
        player.closeInventory();
        player.teleport(target);
        player.sendMessage("§aВы телепортированы рядом с данжем §e" + dungeon.getId());
    }

    private Location findRandomLocationNearDungeon(ActiveDungeon dungeon, int radius) {
        World world = dungeon.getWorld();
        double centerX = (dungeon.getMinX() + dungeon.getMaxX()) / 2.0;
        double centerZ = (dungeon.getMinZ() + dungeon.getMaxZ()) / 2.0;

        for (int i = 0; i < 20; i++) {
            double angle = ThreadLocalRandom.current().nextDouble(0, Math.PI * 2);
            double dist = ThreadLocalRandom.current().nextDouble(8, radius);
            int x = (int) Math.round(centerX + Math.cos(angle) * dist);
            int z = (int) Math.round(centerZ + Math.sin(angle) * dist);
            int y = world.getHighestBlockYAt(x, z) + 1;
            Location candidate = new Location(world, x + 0.5, y, z + 0.5);
            if (candidate.getBlock().getType().isAir() && candidate.clone().add(0, 1, 0).getBlock().getType().isAir()) {
                return candidate;
            }
        }

        int fallbackY = world.getHighestBlockYAt((int) centerX, (int) centerZ) + 1;
        return new Location(world, centerX + 0.5, fallbackY, centerZ + 0.5);
    }

    private boolean isClickable(ItemStack stack) {
        return stack != null && stack.getType() != Material.AIR;
    }

    private String extractDisplayName(ItemStack stack) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return null;
        }
        return meta.getDisplayName().replace("§e", "").trim();
    }
}
