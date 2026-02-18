# DungenSpawner

Плагин для Paper, который спавнит данжи из schematic, интегрируется с WorldEdit и MobsRarity API.

## Матрица сборки по Java

По умолчанию проект теперь собирается на **JDK 17** (чтобы не падать с `release version 21 not supported` в окружениях со старой Java).

Автопрофиль Maven переключает зависимости при JDK 21+:

- **JDK 17–20**: `paper-api 1.20.4`, `worldedit-bukkit 7.2.15`, `release=17`
- **JDK 21+**: `paper-api 1.21.1`, `worldedit-bukkit 7.4.0`, `release=21`

Это позволяет разработчикам на JDK 17 хотя бы компилировать и проверять проект, а для целевого Paper 1.21.x использовать JDK 21+.

## Проверка окружения

```bash
mvn -v
java -version
```

## Сборка

```bash
mvn -DskipTests package
```

## Явно собирать под Paper 1.21.x

Даже на JDK 21+ профиль активируется автоматически. При необходимости можно включить вручную:

```bash
mvn -Pjava21-paper121 -DskipTests package
```
