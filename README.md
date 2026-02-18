# DungenSpawner

Плагин для Paper 1.21.x, который спавнит данжи из schematic, интегрируется с WorldEdit 7.4.0 и MobsRarity API.

## Требования к сборке

- **JDK 21+** (обязательно)
- Maven 3.9+

Почему так: `paper-api 1.21.1-R0.1-SNAPSHOT` и `worldedit-bukkit 7.4.0` скомпилированы под class file version 65 (Java 21). Если собирать на JDK 17, будет ошибка вида:

- `class file has wrong version 65.0, should be 61.0`
- `cannot access org.bukkit...`

## Сборка

```bash
mvn -DskipTests package
```
