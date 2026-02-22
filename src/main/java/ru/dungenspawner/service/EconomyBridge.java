package ru.dungenspawner.service;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.UUID;

public class EconomyBridge {
    private final JavaPlugin plugin;
    private Object economyService;
    private Class<?> currencyTypeClass;
    private Object shardsCurrency;
    private Method depositMethod;

    public EconomyBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void hook() {
        try {
            Class<?> serviceClass = Class.forName("ru.ecoplugin.economy.api.EconomyService");
            RegisteredServiceProvider<?> rsp = Bukkit.getServicesManager().getRegistration(serviceClass);
            if (rsp == null) {
                plugin.getLogger().warning("EconomyService не найден в ServiceManager. Награды шардов отключены.");
                return;
            }

            Object provider = rsp.getProvider();
            if (provider == null) {
                plugin.getLogger().warning("EconomyService provider вернул null. Награды шардов отключены.");
                return;
            }

            currencyTypeClass = Class.forName("ru.ecoplugin.economy.api.CurrencyType");
            shardsCurrency = Enum.valueOf((Class<Enum>) currencyTypeClass.asSubclass(Enum.class), "SHARDS");
            depositMethod = serviceClass.getMethod("deposit", UUID.class, currencyTypeClass, double.class);

            economyService = provider;
            plugin.getLogger().info("EconomyService подключен. Награды шардов активированы.");
        } catch (ClassNotFoundException ex) {
            plugin.getLogger().warning("Economy API не найден (ru.ecoplugin.economy.api.*). Награды шардов отключены.");
        } catch (Exception ex) {
            plugin.getLogger().warning("Не удалось подключить EconomyService: " + ex.getMessage());
        }
    }

    public boolean isAvailable() {
        return economyService != null && depositMethod != null && shardsCurrency != null;
    }

    public boolean depositShards(Player player, double amount) {
        if (player == null || amount <= 0 || !isAvailable()) {
            return false;
        }
        try {
            depositMethod.invoke(economyService, player.getUniqueId(), shardsCurrency, amount);
            return true;
        } catch (Exception ex) {
            plugin.getLogger().warning("Ошибка выдачи шардов игроку " + player.getName() + ": " + ex.getMessage());
            return false;
        }
    }
}
