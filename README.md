# DungenSpawner

Плагин для Paper 1.21.x, который спавнит данжи из schematic, интегрируется с WorldEdit 7.4.0 и MobsRarity API.

## Требования к сборке

- **JDK 21+** (обязательно)
- Maven 3.9+

Почему так: `paper-api 1.21.1-R0.1-SNAPSHOT` и `worldedit-bukkit 7.4.0` скомпилированы под class file version 65 (Java 21). Если собирать на JDK 17, будет ошибка вида:

- `class file has wrong version 65.0, should be 61.0`
- `cannot access org.bukkit...`

## Проверка окружения перед сборкой

```bash
mvn -v
java -version
```

Обе команды должны показывать Java 21+.

Если Maven использует не ту Java, выставите `JAVA_HOME` на JDK 21 перед сборкой.

Пример для Linux/macOS:

```bash
export JAVA_HOME=/path/to/jdk-21
export PATH="$JAVA_HOME/bin:$PATH"
mvn -v
```

## Сборка

```bash
mvn -DskipTests package
```
