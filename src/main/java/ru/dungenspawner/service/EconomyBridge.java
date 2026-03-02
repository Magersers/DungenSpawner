package ru.dungenspawner.service;

import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.InvocationTargetException;
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
                plugin.getLogger().warning("[EconomyBridge] EconomyService не зарегистрирован в ServicesManager.");
                clearCache();
                return;
            }

            Object provider = rsp.getProvider();
            if (provider == null) {
                plugin.getLogger().warning("[EconomyBridge] RegisteredServiceProvider вернул null provider.");
                clearCache();
                return;
            }

            currencyTypeClass = Class.forName("ru.ecoplugin.economy.api.CurrencyType");
            shardsCurrency = Enum.valueOf((Class<Enum>) currencyTypeClass.asSubclass(Enum.class), "SHARDS");
            depositMethod = serviceClass.getMethod("deposit", UUID.class, currencyTypeClass, double.class);
            economyService = provider;
            plugin.getLogger().info("[EconomyBridge] EconomyService подключен: " + provider.getClass().getName());
        } catch (ClassNotFoundException ex) {
            plugin.getLogger().warning("[EconomyBridge] Не найден класс Economy API: " + ex.getMessage());
            clearCache();
        } catch (NoSuchMethodException ex) {
            plugin.getLogger().warning("[EconomyBridge] У EconomyService нет метода deposit(UUID, CurrencyType, double): " + ex.getMessage());
            clearCache();
        } catch (Exception ex) {
            plugin.getLogger().warning("[EconomyBridge] Ошибка hook(): " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            clearCache();
        }
    }

    public boolean isAvailable() {
        if (economyService == null || depositMethod == null || shardsCurrency == null) {
            plugin.getLogger().info("[EconomyBridge] Попытка переподключения EconomyService...");
            hook();
        }
        return economyService != null && depositMethod != null && shardsCurrency != null;
    }

    public boolean depositShards(UUID playerId, double amount) {
        if (playerId == null || amount <= 0) {
            plugin.getLogger().warning("[EconomyBridge] Некорректные аргументы depositShards: playerId=" + playerId + ", amount=" + amount);
            return false;
        }

        if (!isAvailable()) {
            plugin.getLogger().warning("[EconomyBridge] Начисление отменено: EconomyService недоступен.");
            return false;
        }

        try {
            Object transactionResult = depositMethod.invoke(economyService, playerId, shardsCurrency, amount);
            plugin.getLogger().info("[EconomyBridge] deposit SHARDS player=" + playerId + ", amount=" + amount + ", result=" + describeTransactionResult(transactionResult));
            return transactionResult != null;
        } catch (InvocationTargetException ex) {
            Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
            plugin.getLogger().warning("[EconomyBridge] Ошибка внешнего API при выдаче шардов игроку " + playerId + ": " + cause.getClass().getSimpleName() + ": " + cause.getMessage());
            clearCache();
            return false;
        } catch (Exception ex) {
            plugin.getLogger().warning("[EconomyBridge] Ошибка выдачи шардов игроку " + playerId + ": " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
            clearCache();
            return false;
        }
    }

    private String describeTransactionResult(Object result) {
        if (result == null) {
            return "null";
        }

        StringBuilder sb = new StringBuilder(result.getClass().getSimpleName())
                .append("{")
                .append("toString=")
                .append(result);

        appendMethodValue(sb, result, "isSuccess");
        appendMethodValue(sb, result, "getStatus");
        appendMethodValue(sb, result, "status");
        appendMethodValue(sb, result, "getMessage");
        appendMethodValue(sb, result, "message");

        sb.append("}");
        return sb.toString();
    }

    private void appendMethodValue(StringBuilder sb, Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            Object value = method.invoke(target);
            sb.append(", ").append(methodName).append("=").append(value);
        } catch (Exception ignored) {
            // Optional diagnostic method
        }
    }

    private void clearCache() {
        economyService = null;
        currencyTypeClass = null;
        shardsCurrency = null;
        depositMethod = null;
    }
}
