package ru.dungenspawner.service;

import org.bukkit.Bukkit;
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
            if (rsp == null || rsp.getProvider() == null) {
                economyService = null;
                return;
            }

            currencyTypeClass = Class.forName("ru.ecoplugin.economy.api.CurrencyType");
            shardsCurrency = Enum.valueOf((Class<Enum>) currencyTypeClass.asSubclass(Enum.class), "SHARDS");
            depositMethod = serviceClass.getMethod("deposit", UUID.class, currencyTypeClass, double.class);
            economyService = rsp.getProvider();
        } catch (Exception ex) {
            economyService = null;
            currencyTypeClass = null;
            shardsCurrency = null;
            depositMethod = null;
        }
    }

    public boolean isAvailable() {
        if (economyService == null || depositMethod == null || shardsCurrency == null) {
            hook();
        }
        return economyService != null && depositMethod != null && shardsCurrency != null;
    }

    public boolean depositShards(UUID playerId, double amount) {
        if (playerId == null || amount <= 0) {
            return false;
        }
        if (!isAvailable()) {
            return false;
        }

        try {
            Object transactionResult = depositMethod.invoke(economyService, playerId, shardsCurrency, amount);
            return transactionResult != null;
        } catch (Exception ex) {
            plugin.getLogger().warning("Ошибка выдачи шардов игроку " + playerId + ": " + ex.getMessage());
            economyService = null;
            return false;
        }
    }
}
