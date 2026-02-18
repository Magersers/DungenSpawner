# DungenSpawner

Плагин для Paper, который спавнит данжи из schematic, интегрируется с WorldEdit и MobsRarity API.

## Требования к сборке

- JDK 17
- Maven 3.9+

## Используемые зависимости

- `paper-api:1.20.4-R0.1-SNAPSHOT`
- `worldedit-bukkit:7.3.0`

## Проверка окружения

```bash
mvn -v
java -version
```

## Сборка

```bash
mvn -DskipTests package
```

## Путь к schematic

По умолчанию используется путь WorldEdit:

- `plugins/WorldEdit/schematics/simple-church.schematic`

Плагин также пробует fallback-пути: локальный `plugins/DungenSpawner/schematics/<file>` и относительный путь от корня сервера.
