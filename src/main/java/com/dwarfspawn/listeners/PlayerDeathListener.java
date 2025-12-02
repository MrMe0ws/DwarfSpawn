package com.dwarfspawn.listeners;

import com.dwarfspawn.ConfigManager;
import com.dwarfspawn.StartKitManager;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import com.dwarfspawn.DwarfSpawn;

import java.util.Random;

public class PlayerDeathListener implements Listener {
    private final ConfigManager configManager;
    private final StartKitManager startKitManager;
    private final Random random = new Random();

    public PlayerDeathListener(ConfigManager configManager, StartKitManager startKitManager) {
        this.configManager = configManager;
        this.startKitManager = startKitManager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerJoin(PlayerJoinEvent event) {
        // Обрабатываем первый вход игрока на сервер
        try {
            Player player = event.getPlayer();

            // Проверяем, является ли игрок новым (первый вход)
            if (player.hasPlayedBefore()) {
                return; // Игрок уже играл, не обрабатываем
            }

            // Проверяем, есть ли у игрока своя точка спавна (кровать)
            Location bedSpawn = player.getBedSpawnLocation();
            if (bedSpawn != null) {
                return; // У игрока есть кровать, не вмешиваемся
            }

            // Получаем базовую точку спавна
            Location baseSpawn = configManager.getSpawnLocation();
            if (baseSpawn == null) {
                // Если нет точки спавна, все равно выдаем кит и книгу новому игроку
                giveKitAndBookToNewPlayer(player);
                return; // Не удалось получить точку спавна из конфига
            }

            World world = baseSpawn.getWorld();
            if (world == null) {
                // Если мир не найден, все равно выдаем кит и книгу новому игроку
                giveKitAndBookToNewPlayer(player);
                return;
            }

            // Генерируем место спавна для нового игрока
            Location spawnLocation;
            if (configManager.isRadiusEnabled()) {
                spawnLocation = generateRandomSpawnLocation(baseSpawn, world);
            } else {
                spawnLocation = baseSpawn.clone();
            }

            if (spawnLocation == null) {
                // Если не удалось найти место, все равно выдаем кит и книгу новому игроку
                giveKitAndBookToNewPlayer(player);
                return; // Не удалось найти подходящее место
            }

            // Загружаем чанк перед проверкой спавна (важно для первого входа)
            int chunkX = spawnLocation.getBlockX() >> 4;
            int chunkZ = spawnLocation.getBlockZ() >> 4;
            if (!world.isChunkLoaded(chunkX, chunkZ)) {
                // Загружаем чанк синхронно (это безопасно в главном потоке)
                world.loadChunk(chunkX, chunkZ);
            }

            // Проверяем и корректируем местоположение
            spawnLocation = findValidSpawnLocation(spawnLocation, world);

            if (spawnLocation != null) {
                // Телепортируем игрока на найденное место
                player.teleport(spawnLocation);
            }

            // Выдаем стартовый набор и книгу при первом входе (для нового игрока всегда)
            giveKitAndBookToNewPlayer(player);
        } catch (Exception e) {
            // Если что-то пошло не так, все равно пытаемся выдать кит и книгу новому игроку
            try {
                Player player = event.getPlayer();
                if (!player.hasPlayedBefore()) {
                    giveKitAndBookToNewPlayer(player);
                }
            } catch (Exception ex) {
                // Игнорируем ошибки при выдаче
            }
        }
    }

    /**
     * Выдает стартовый набор и книгу новому игроку
     * @param player Игрок
     */
    private void giveKitAndBookToNewPlayer(Player player) {
        // Выдаем стартовый набор при первом входе (если нет точки спавна)
        if (!startKitManager.hasSpawnPoint(player)) {
            // Выдаем набор с небольшой задержкой, чтобы игрок успел заспавниться
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (player.isOnline()) {
                        startKitManager.giveStartKit(player); // Предметы с кулдауном
                        startKitManager.giveEffects(player); // Эффекты всегда
                    }
                }
            }.runTaskLater(DwarfSpawn.getInstance(), 20L); // 1 секунда задержки
        }

        // Выдаем книгу при первом входе
        if (configManager.isFirstJoinBookEnabled()) {
            ItemStack book = configManager.getFirstJoinBook();
            if (book != null) {
                // Выдаем книгу с небольшой задержкой
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        if (player.isOnline()) {
                            // Проверяем, есть ли место в инвентаре
                            if (player.getInventory().firstEmpty() != -1) {
                                player.getInventory().addItem(book);
                            } else {
                                // Если инвентарь полон, выкидываем книгу на землю
                                player.getWorld().dropItemNaturally(player.getLocation(), book);
                            }
                        }
                    }
                }.runTaskLater(DwarfSpawn.getInstance(), 40L); // 2 секунды задержки
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        // Обрабатываем возрождение игрока после смерти
        try {
            Player player = event.getPlayer();

            // Проверяем, есть ли у игрока своя точка спавна (кровать или якорь)
            Location bedSpawn = player.getBedSpawnLocation();

            // Если у игрока есть кровать, не вмешиваемся
            if (bedSpawn != null) {
                return;
            }

            // Проверяем якорь возрождения через Paper API
            // В Paper API есть метод isAnchorSpawn()
            try {
                java.lang.reflect.Method isAnchorSpawnMethod = event.getClass().getMethod("isAnchorSpawn");
                boolean isAnchorSpawn = (Boolean) isAnchorSpawnMethod.invoke(event);
                if (isAnchorSpawn) {
                    // Игрок использует якорь возрождения, не вмешиваемся
                    return;
                }
            } catch (Exception e) {
                // Метод не найден (не Paper или старая версия), используем альтернативный
                // способ
                Location eventRespawnLocation = event.getRespawnLocation();
                Location worldSpawn = player.getWorld().getSpawnLocation();

                // Если локация спавна далеко от спавна мира, возможно это якорь
                if (eventRespawnLocation != null) {
                    double distanceToWorldSpawn = eventRespawnLocation.distance(worldSpawn);
                    if (distanceToWorldSpawn > 5) {
                        // Локация далеко от спавна мира, вероятно это якорь возрождения
                        return;
                    }
                }
            }

            // Получаем базовую точку спавна из конфига (это всегда основной мир - world)
            Location baseSpawn = configManager.getSpawnLocation();
            if (baseSpawn == null) {
                return; // Не удалось получить точку спавна из конфига
            }

            World spawnWorld = baseSpawn.getWorld();
            if (spawnWorld == null) {
                return;
            }

            // ВАЖНО: Независимо от того, в каком мире умер игрок (основной, энд, ад),
            // если у него нет кровати/якоря, применяем логику плагина в основном мире
            // (world).
            // Это соответствует дефолтной логике Minecraft - игроки спавнятся в основном
            // мире,
            // если у них нет своей точки спавна в других мирах.

            Location spawnLocation;

            if (configManager.isRadiusEnabled()) {
                // Генерируем случайную точку в радиусе в основном мире
                spawnLocation = generateRandomSpawnLocation(baseSpawn, spawnWorld);
            } else {
                // Используем конкретную точку спавна в основном мире
                spawnLocation = baseSpawn.clone();
            }

            // Проверяем, что spawnLocation не null перед вызовом findValidSpawnLocation
            if (spawnLocation == null) {
                return;
            }

            // Проверяем и корректируем местоположение в основном мире
            spawnLocation = findValidSpawnLocation(spawnLocation, spawnWorld);

            if (spawnLocation != null) {
                event.setRespawnLocation(spawnLocation);
            }

            // Выдаем стартовый набор при смерти (если нет точки спавна)
            // Проверяем якорь ада через Paper API
            boolean isAnchorSpawn = false;
            try {
                java.lang.reflect.Method isAnchorSpawnMethod = event.getClass().getMethod("isAnchorSpawn");
                isAnchorSpawn = (Boolean) isAnchorSpawnMethod.invoke(event);
            } catch (Exception e) {
                // Метод не найден (не Paper или старая версия), считаем что не якорь
            }

            if (!isAnchorSpawn && !startKitManager.hasSpawnPoint(player)) {
                // Выдаем эффекты всегда при возрождении (если включены)
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        startKitManager.giveEffects(player); // Эффекты всегда
                    }
                }.runTaskLater(DwarfSpawn.getInstance(), 20L); // 1 секунда задержки

                // Выдаем предметы с учетом кулдауна
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        startKitManager.giveStartKit(player); // Предметы с кулдауном
                    }
                }.runTaskLater(DwarfSpawn.getInstance(), 20L); // 1 секунда задержки
            }
        } catch (Exception e) {
            // Если что-то пошло не так, используем дефолтный спавн Minecraft
            // Не логируем ошибку, чтобы не засорять консоль
        }
    }

    private Location generateRandomSpawnLocation(Location center, World world) {
        try {
            int radius = configManager.getSpawnRadius();
            int maxHeight = configManager.getMaxSpawnHeight();
            int minHeight = configManager.getMinSpawnHeight();

            // Генерируем случайные координаты в радиусе
            int attempts = 0;
            int maxAttempts = configManager.getMaxSpawnAttempts();

            while (attempts < maxAttempts) {
                double angle = random.nextDouble() * 2 * Math.PI;
                double distance = random.nextDouble() * radius;

                double x = center.getX() + Math.cos(angle) * distance;
                double z = center.getZ() + Math.sin(angle) * distance;

                // Начинаем поиск с середины диапазона высот для лучшего результата
                double y = (minHeight + maxHeight) / 2.0;

                Location candidate = new Location(world, x, y, z);

                // Сначала пробуем найти место точно в этой точке
                Location validLocation = findValidSpawnLocation(candidate, world);

                // Если не нашли, пробуем поискать в небольшом радиусе вокруг (5 блоков)
                // Это помогает найти место, если рядом есть подходящие блоки
                if (validLocation == null) {
                    validLocation = findValidSpawnLocationInRadius(candidate, world, 5);
                }

                if (validLocation != null) {
                    return validLocation;
                }

                attempts++;
            }

            // Если не удалось найти подходящее место, возвращаем базовую точку
            return findValidSpawnLocation(center, world);
        } catch (Exception e) {
            // Если что-то пошло не так, возвращаем null (используется дефолтный спавн)
            return null;
        }
    }

    private Location findValidSpawnLocationInRadius(Location center, World world, int searchRadius) {
        // Ищем подходящее место в радиусе вокруг центральной точки
        // Проверяем точки в квадрате вокруг центра
        for (int dx = -searchRadius; dx <= searchRadius; dx++) {
            for (int dz = -searchRadius; dz <= searchRadius; dz++) {
                // Пропускаем точки слишком далеко от центра (вне круга)
                if (dx * dx + dz * dz > searchRadius * searchRadius) {
                    continue;
                }

                Location candidate = center.clone().add(dx, 0, dz);
                Location validLocation = findValidSpawnLocation(candidate, world);

                if (validLocation != null) {
                    return validLocation;
                }
            }
        }

        return null;
    }

    private Location findValidSpawnLocation(Location location, World world) {
        try {
            // Проверяем, что location не null
            if (location == null || world == null) {
                return null;
            }

            int minHeight = configManager.getMinSpawnHeight();
            int maxHeight = configManager.getMaxSpawnHeight();
            boolean checkBlockAbove = configManager.shouldCheckBlockAbove();

            int startY = Math.min((int) location.getY(), maxHeight);
            int minY = Math.max(world.getMinHeight(), minHeight); // Не ищем ниже минимальной высоты

            // Проверяем, что чанк загружен перед проверкой блоков
            int chunkX = location.getBlockX() >> 4;
            int chunkZ = location.getBlockZ() >> 4;
            if (!world.isChunkLoaded(chunkX, chunkZ)) {
                // Чанк не загружен, пытаемся загрузить его
                world.loadChunk(chunkX, chunkZ);
                // Если все еще не загружен, возвращаем null
                if (!world.isChunkLoaded(chunkX, chunkZ)) {
                    return null;
                }
            }

            // Ищем подходящее место, начиная с максимальной высоты и спускаясь вниз
            // Это гарантирует, что мы найдем место под землей, а не на поверхности
            for (int y = startY; y >= minY; y--) {
                Location testLocation = new Location(world, location.getX(), y, location.getZ());

                // Проверяем, что чанк для этой координаты загружен
                int testChunkX = testLocation.getBlockX() >> 4;
                int testChunkZ = testLocation.getBlockZ() >> 4;
                if (!world.isChunkLoaded(testChunkX, testChunkZ)) {
                    // Пытаемся загрузить чанк
                    world.loadChunk(testChunkX, testChunkZ);
                    // Если все еще не загружен, пропускаем эту координату
                    if (!world.isChunkLoaded(testChunkX, testChunkZ)) {
                        continue;
                    }
                }

                // Проверяем, что блок под ногами твердый
                Material groundBlock = world.getBlockAt(testLocation.clone().add(0, -1, 0)).getType();
                if (!groundBlock.isSolid()) {
                    continue;
                }

                // Проверяем, что место для спавна свободно (воздух или безопасный блок)
                Material spawnBlock = world.getBlockAt(testLocation).getType();
                if (!isSafeBlock(spawnBlock)) {
                    continue;
                }

                // Проверяем, что место не под водой или лавой
                if (isWaterOrLava(spawnBlock)) {
                    continue; // Не спавним в воде или лаве
                }

                // Проверяем блок над головой (на высоте +1) - должен быть воздух для головы
                Material headBlock = world.getBlockAt(testLocation.clone().add(0, 1, 0)).getType();

                // Проверяем, что над головой нет воды или лавы
                if (isWaterOrLava(headBlock)) {
                    continue; // Не спавним, если над головой вода или лава
                }

                // Проверяем, что место для головы безопасно (воздух или не-твердый блок)
                if (!isSafeBlock(headBlock)) {
                    continue; // Над головой твердый блок - нельзя спавнить
                }

                // ВАЖНО: Проверяем наличие блока выше для защиты от солнца
                // Игрок должен помещаться (2 блока высоты), затем проверяем наличие блока выше
                if (checkBlockAbove) {
                    // Проверяем, что на высоте +2 тоже есть место (для полного роста игрока)
                    Material blockAtHeight2 = world.getBlockAt(testLocation.clone().add(0, 2, 0)).getType();
                    if (!isSafeBlock(blockAtHeight2) || isWaterOrLava(blockAtHeight2)) {
                        continue; // На высоте +2 нет места или есть вода/лава
                    }

                    // Проверяем, что начиная с высоты +2 и выше (до максимальной высоты мира) есть
                    // хотя бы один блок
                    // Это означает, что над игроком есть защита от солнца (не открытое небо)
                    boolean hasBlockAbove = false;
                    int startCheckY = (int) testLocation.getY() + 2;
                    int maxCheckHeight = world.getMaxHeight();

                    for (int checkY = startCheckY; checkY <= maxCheckHeight; checkY++) {
                        Location checkLocation = new Location(world, testLocation.getX(), checkY, testLocation.getZ());

                        // Проверяем, что чанк загружен перед проверкой блока
                        int checkChunkX = checkLocation.getBlockX() >> 4;
                        int checkChunkZ = checkLocation.getBlockZ() >> 4;
                        if (!world.isChunkLoaded(checkChunkX, checkChunkZ)) {
                            // Пытаемся загрузить чанк
                            world.loadChunk(checkChunkX, checkChunkZ);
                            // Если все еще не загружен, пропускаем эту проверку
                            // Но продолжаем искать дальше, так как чанки могут быть загружены выше
                            if (!world.isChunkLoaded(checkChunkX, checkChunkZ)) {
                                continue;
                            }
                        }

                        Material blockAbove = world.getBlockAt(checkLocation).getType();
                        // Если нашли любой блок (не воздух, не вода, не лава) - это защита от солнца
                        if (!blockAbove.isAir() &&
                                blockAbove != Material.CAVE_AIR &&
                                blockAbove != Material.VOID_AIR &&
                                !isWaterOrLava(blockAbove)) {
                            hasBlockAbove = true;
                            break; // Нашли блок - есть защита от солнца
                        }
                    }

                    if (!hasBlockAbove) {
                        continue; // Над игроком нет блока (открытое небо), пропускаем
                    }
                    // Если есть блок выше - это защита от солнца, подходит!
                } else {
                    // Если проверка блока над головой выключена, просто проверяем безопасность
                    if (!isSafeBlock(headBlock)) {
                        continue;
                    }
                }

                // Нашли подходящее место! (под землей, с блоком над головой)
                return testLocation;
            }

            // Если не нашли подходящее место, возвращаем null
            return null;
        } catch (Exception e) {
            // Если что-то пошло не так, возвращаем null (используется дефолтный спавн)
            return null;
        }
    }

    private boolean isWaterOrLava(Material material) {
        return material == Material.WATER ||
                material == Material.LAVA ||
                material == Material.KELP ||
                material == Material.KELP_PLANT ||
                material == Material.SEAGRASS ||
                material == Material.TALL_SEAGRASS ||
                material == Material.BUBBLE_COLUMN;
    }

    private boolean isSafeBlock(Material material) {
        // Проверяем, что блок безопасен для спавна
        return material.isAir() ||
                material == Material.CAVE_AIR ||
                material == Material.VOID_AIR ||
                !material.isSolid();
    }
}
