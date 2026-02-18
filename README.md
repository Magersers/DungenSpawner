# DungenSpawner

Плагин для Paper, который спавнит данжи из schematic, интегрируется с WorldEdit, MobsRarity API и PlaceholderAPI.

## Возможности

- Авто-спавн данжей 2-3 раза в день.
- Ручной спавн с редкостью: `/spawndungeon [rarity]`.
- Если команду вводит админ-игрок, данж спавнится рядом с ним (на земле).
- Удаление данжей админом: `/spawndungeon remove <id|all>`.
- Лимит одновременно активных данжей (`max-active-dungeons`, по умолчанию 5).
- Таймер жизни каждого данжа (`dungeon-lifetime-seconds`, по умолчанию 600 сек = 10 минут).
- По истечении таймера данж автоматически разрушается, а его мобы удаляются.
- У мобов данжа и босса в имени есть префикс `Данж`.
- В центре каждого активного данжа показывается плавающий таймер (ArmorStand-холограмма).

## PlaceholderAPI

Регистрируются плейсхолдеры:

- `%dungenspawner_active_count%` — количество активных данжей.
- `%dungenspawner_all_timers%` — список таймеров всех данжей.
- `%dungenspawner_nearest_timer%` — таймер ближайшего к игроку данжа.

## Требования к сборке

- JDK 17
- Maven 3.9+

## Используемые зависимости

- `paper-api:1.20.4-R0.1-SNAPSHOT`
- `worldedit-bukkit:7.3.0`
- `placeholderapi:2.12.2`

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
