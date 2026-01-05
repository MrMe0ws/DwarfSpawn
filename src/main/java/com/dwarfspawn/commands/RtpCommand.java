package com.dwarfspawn.commands;

import com.dwarfspawn.ConfigManager;
import com.dwarfspawn.DwarfSpawn;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class RtpCommand implements CommandExecutor {
    private final DwarfSpawn plugin;
    private final ConfigManager configManager;
    private final Random random = new Random();
    private final Map<UUID, Long> lastRtpUsed = new HashMap<>(); // UUID игрока -> время последнего использования

    public RtpCommand(DwarfSpawn plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player targetPlayer = null;
        boolean isConsole = !(sender instanceof Player);
        boolean isTeleportingOther = false; // Флаг: телепортируем другого игрока

        // Если команда из консоли
        if (isConsole) {
            if (args.length == 0) {
                sender.sendMessage("§c[DwarfSpawn] §7Использование: /rtp <игрок>");
                return true;
            }

            String playerName = args[0];
            targetPlayer = plugin.getServer().getPlayer(playerName);

            if (targetPlayer == null) {
                sender.sendMessage("§c[DwarfSpawn] §7Игрок '" + playerName + "' не найден или не в сети!");
                return true;
            }
            isTeleportingOther = true;
        } else {
            // Если команда от игрока
            Player senderPlayer = (Player) sender;

            // Если указан аргумент (ник другого игрока)
            if (args.length > 0) {
                String playerName = args[0];
                targetPlayer = plugin.getServer().getPlayer(playerName);

                if (targetPlayer == null) {
                    senderPlayer.sendMessage("§c[DwarfSpawn] §7Игрок '" + playerName + "' не найден или не в сети!");
                    return true;
                }

                // Проверяем, не пытается ли игрок телепортировать сам себя
                if (targetPlayer.equals(senderPlayer)) {
                    targetPlayer = senderPlayer; // Телепортируем себя
                    isTeleportingOther = false;
                } else {
                    // Телепортируем другого игрока - проверяем права (OP или права администратора)
                    if (!senderPlayer.isOp() && !senderPlayer.hasPermission("bukkit.command.op")) {
                        senderPlayer.sendMessage(configManager.getRtpMessage("no-permission",
                                "§c[DwarfSpawn] §7У вас нет прав на использование этой команды!"));
                        return true;
                    }
                    isTeleportingOther = true; // Телепортируем другого игрока
                }
            } else {
                // Нет аргумента - телепортируем себя
                targetPlayer = senderPlayer;
                isTeleportingOther = false;
            }

            // Проверяем права доступа (только если телепортируем себя и не OP)
            if (!isTeleportingOther && !senderPlayer.isOp() && !senderPlayer.hasPermission("bukkit.command.op")) {
                if (!senderPlayer.hasPermission("dwarfspawn.rtp") && !senderPlayer.hasPermission("ds.rtp")) {
                    senderPlayer.sendMessage(configManager.getRtpMessage("no-permission",
                            "§c[DwarfSpawn] §7У вас нет прав на использование этой команды!"));
                    return true;
                }
            }
        }

        // Проверяем, включена ли команда
        if (!configManager.isRtpEnabled()) {
            if (isConsole) {
                sender.sendMessage("§c[DwarfSpawn] §7Команда /rtp отключена в конфигурации!");
            } else {
                targetPlayer.sendMessage(configManager.getRtpMessage("disabled",
                        "§c[DwarfSpawn] §7Команда /rtp отключена в конфигурации!"));
            }
            return true;
        }

        // Проверяем кулдаун (только если игрок телепортирует сам себя, не для консоли и
        // не при телепортации другого)
        if (!isConsole && !isTeleportingOther) {
            UUID playerId = targetPlayer.getUniqueId();
            long currentTime = System.currentTimeMillis();
            long cooldown = configManager.getRtpCooldown() * 1000L; // Конвертируем секунды в миллисекунды

            if (lastRtpUsed.containsKey(playerId)) {
                long lastUsed = lastRtpUsed.get(playerId);
                long timeSinceLastUsed = currentTime - lastUsed;

                if (timeSinceLastUsed < cooldown) {
                    long remainingSeconds = (cooldown - timeSinceLastUsed) / 1000L;
                    targetPlayer.sendMessage(configManager.getRtpMessage("cooldown",
                            "§c[DwarfSpawn] §7Вы должны подождать еще §e{seconds} §7секунд перед следующим использованием!",
                            "seconds=" + remainingSeconds));
                    return true;
                }
            }

            // Сохраняем время использования (только когда игрок телепортирует сам себя)
            lastRtpUsed.put(playerId, System.currentTimeMillis());
        }

        // Начинаем процесс телепортации
        int delay = configManager.getRtpDelay();
        if (isConsole || isTeleportingOther) {
            if (isConsole) {
                sender.sendMessage("§6[DwarfSpawn] §7Телепортирую игрока §e" + targetPlayer.getName() + " §7через §e"
                        + delay + " §7секунд...");
            } else {
                sender.sendMessage("§6[DwarfSpawn] §7Телепортирую игрока §e" + targetPlayer.getName() + " §7через §e"
                        + delay + " §7секунд...");
            }
        }
        targetPlayer.sendMessage(configManager.getRtpMessage("teleport-start",
                "§6[DwarfSpawn] §7Телепортация через §e{delay} §7секунд... Не двигайтесь!",
                "delay=" + delay));

        // Обратный отсчет
        final Player finalTargetPlayer = targetPlayer; // Для использования в анонимном классе
        final boolean finalIsConsole = isConsole; // Для использования в анонимном классе
        final boolean finalIsTeleportingOther = isTeleportingOther; // Для использования в анонимном классе
        new BukkitRunnable() {
            int countdown = delay;
            Location startLocation = finalTargetPlayer.getLocation();

            @Override
            public void run() {
                // Проверяем, что игрок все еще онлайн и не двигался
                if (!finalTargetPlayer.isOnline()) {
                    cancel();
                    return;
                }

                // Проверяем, что игрок не двигался слишком далеко (только если игрок
                // телепортирует сам себя)
                if (!finalIsConsole && !finalIsTeleportingOther
                        && finalTargetPlayer.getLocation().distance(startLocation) > 2.0) {
                    finalTargetPlayer.sendMessage(configManager.getRtpMessage("cancelled",
                            "§c[DwarfSpawn] §7Телепортация отменена! Вы двигались."));
                    cancel();
                    return;
                }

                if (countdown > 0) {
                    finalTargetPlayer.sendMessage(configManager.getRtpMessage("countdown",
                            "&6[DwarfSpawn] &e{countdown}",
                            "countdown=" + countdown));
                    countdown--;
                } else {
                    // Время телепортации
                    teleportPlayer(finalTargetPlayer);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 0L, 20L); // Каждую секунду

        return true;
    }

    private void teleportPlayer(Player player) {
        try {
            // Получаем базовую точку спавна из конфига
            Location baseSpawn = configManager.getSpawnLocation();
            if (baseSpawn == null) {
                player.sendMessage(configManager.getRtpMessage("error-spawn",
                        "§c[DwarfSpawn] §7Ошибка: не удалось получить точку спавна из конфига!"));
                if (configManager.isRtpDebug()) {
                    plugin.getLogger().warning("[RTP Debug] Не удалось получить spawn-location из конфига");
                }
                return;
            }

            // Получаем мир для телепортации
            String worldName = configManager.getRtpWorld();
            World targetWorld = plugin.getServer().getWorld(worldName);
            if (targetWorld == null) {
                player.sendMessage(configManager.getRtpMessage("error-world",
                        "§c[DwarfSpawn] §7Ошибка: мир '&e{world}&7' не найден!",
                        "world=" + worldName));
                if (configManager.isRtpDebug()) {
                    plugin.getLogger().warning("[RTP Debug] Мир '" + worldName + "' не найден");
                }
                return;
            }

            // Генерируем случайную точку в радиусе
            int radius = configManager.getRtpRadius();
            double angle = random.nextDouble() * 2 * Math.PI;
            double distance = random.nextDouble() * radius;

            double x = baseSpawn.getX() + Math.cos(angle) * distance;
            double z = baseSpawn.getZ() + Math.sin(angle) * distance;
            double y = (configManager.getRtpMinHeight() + configManager.getRtpMaxHeight()) / 2.0;

            Location candidateLocation = new Location(targetWorld, x, y, z);

            // Ищем подходящее место
            Location teleportLocation = findValidTeleportLocation(candidateLocation, targetWorld);

            if (teleportLocation == null) {
                // Если не нашли подходящее место, телепортируем в случайное место
                if (configManager.isRtpDebug()) {
                    plugin.getLogger()
                            .warning("[RTP Debug] Не удалось найти подходящее место, телепортируем в случайное место");
                }
                // Генерируем еще одну случайную точку
                angle = random.nextDouble() * 2 * Math.PI;
                distance = random.nextDouble() * radius;
                x = baseSpawn.getX() + Math.cos(angle) * distance;
                z = baseSpawn.getZ() + Math.sin(angle) * distance;
                // Если включен случайный Y - спавним на поверхности, иначе используем высоту из
                // конфига
                if (configManager.isRtpRandomY()) {
                    y = targetWorld.getHighestBlockYAt((int) x, (int) z) + 1;
                } else {
                    y = targetWorld.getHighestBlockYAt((int) x, (int) z) + 1;
                }
                teleportLocation = new Location(targetWorld, x, y, z);
            }

            // Сохраняем старую локацию игрока для эффекта частиц
            final Location oldLocation = player.getLocation().clone();

            // Показываем частицы на старом месте (до телепортации)
            applyVisualEffects(player, oldLocation, true);

            // Сохраняем финальную ссылку на локацию телепортации для использования во
            // внутреннем классе
            final Location finalTeleportLocation = teleportLocation;

            // Небольшая задержка перед телепортацией, чтобы частицы успели показаться
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (!player.isOnline()) {
                        return;
                    }

                    // Телепортируем игрока
                    player.teleport(finalTeleportLocation);
                    player.sendMessage(configManager.getRtpMessage("success",
                            "§6[DwarfSpawn] §aВы были телепортированы в случайное место!"));
                    if (configManager.isRtpDebug()) {
                        plugin.getLogger().info("[RTP Debug] Игрок " + player.getName() + " телепортирован в " +
                                finalTeleportLocation.getBlockX() + ", " + finalTeleportLocation.getBlockY() + ", "
                                + finalTeleportLocation.getBlockZ());
                    }

                    // Показываем частицы на новом месте (после телепортации)
                    applyVisualEffects(player, finalTeleportLocation, false);
                }
            }.runTaskLater(plugin, 5L); // Задержка 0.25 секунды (5 тиков)

        } catch (Exception e) {
            player.sendMessage(configManager.getRtpMessage("error-teleport",
                    "§c[DwarfSpawn] §7Ошибка при телепортации!"));
            if (configManager.isRtpDebug()) {
                plugin.getLogger().severe(
                        "[RTP Debug] Ошибка при телепортации игрока " + player.getName() + ": " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    private Location findValidTeleportLocation(Location location, World world) {
        try {
            if (location == null || world == null) {
                return null;
            }

            int minHeight = configManager.getRtpMinHeight();
            int maxHeight = configManager.getRtpMaxHeight();
            boolean checkBlockAbove = configManager.shouldRtpCheckBlockAbove();
            boolean randomY = configManager.isRtpRandomY();
            int maxAttempts = configManager.getRtpMaxAttempts();

            int minY = Math.max(world.getMinHeight(), minHeight);

            // Проверяем, что чанк загружен перед проверкой блоков
            int chunkX = location.getBlockX() >> 4;
            int chunkZ = location.getBlockZ() >> 4;
            if (!world.isChunkLoaded(chunkX, chunkZ)) {
                world.loadChunk(chunkX, chunkZ);
                if (!world.isChunkLoaded(chunkX, chunkZ)) {
                    if (configManager.isRtpDebug()) {
                        plugin.getLogger().warning("[RTP Debug] Не удалось загрузить чанк " + chunkX + ", " + chunkZ);
                    }
                    return null;
                }
            }

            // Если включен случайный Y - используем старую логику
            if (randomY) {
                return findValidTeleportLocationRandomY(location, world, minY, maxHeight, checkBlockAbove, maxAttempts);
            }

            // Иначе используем новую логику - поиск сверху вниз (как в спавне)
            // Это гарантирует, что мы найдем место под землей, а не на поверхности
            int startY = Math.min((int) location.getY(), maxHeight);

            // Ищем подходящее место, начиная с максимальной высоты и спускаясь вниз
            for (int y = startY; y >= minY; y--) {
                Location testLocation = new Location(world, location.getX(), y, location.getZ());

                // Проверяем, что чанк для этой координаты загружен
                int testChunkX = testLocation.getBlockX() >> 4;
                int testChunkZ = testLocation.getBlockZ() >> 4;
                if (!world.isChunkLoaded(testChunkX, testChunkZ)) {
                    world.loadChunk(testChunkX, testChunkZ);
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
                    continue;
                }

                // Проверяем блок над головой (на высоте +1) - должен быть воздух для головы
                Material headBlock = world.getBlockAt(testLocation.clone().add(0, 1, 0)).getType();

                // Проверяем, что над головой нет воды или лавы
                if (isWaterOrLava(headBlock)) {
                    continue;
                }

                // Проверяем, что место для головы безопасно (воздух или не-твердый блок)
                if (!isSafeBlock(headBlock)) {
                    continue;
                }

                // ВАЖНО: Проверяем наличие блока выше для защиты от солнца
                // Игрок должен помещаться (2 блока высоты), затем проверяем наличие блока выше
                if (checkBlockAbove) {
                    // Проверяем, что на высоте +2 тоже есть место (для полного роста игрока)
                    Material blockAtHeight2 = world.getBlockAt(testLocation.clone().add(0, 2, 0)).getType();
                    if (!isSafeBlock(blockAtHeight2) || isWaterOrLava(blockAtHeight2)) {
                        continue;
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
                            world.loadChunk(checkChunkX, checkChunkZ);
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
                            break;
                        }
                    }

                    if (!hasBlockAbove) {
                        continue;
                    }
                }

                // Нашли подходящее место! (под землей, с блоком над головой)
                return testLocation;
            }

            // Если не нашли подходящее место, пробуем поискать в небольшом радиусе вокруг
            // Используем ту же логику, что и в generateRandomSpawnLocation
            return findValidTeleportLocationInRadius(location, world, 5);
        } catch (Exception e) {
            if (configManager.isRtpDebug()) {
                plugin.getLogger().warning("[RTP Debug] Ошибка при поиске места: " + e.getMessage());
                e.printStackTrace();
            }
            return null;
        }
    }

    private Location findValidTeleportLocationInRadius(Location center, World world, int searchRadius) {
        // Ищем подходящее место в радиусе вокруг центральной точки
        // Проверяем точки в квадрате вокруг центра
        for (int dx = -searchRadius; dx <= searchRadius; dx++) {
            for (int dz = -searchRadius; dz <= searchRadius; dz++) {
                // Пропускаем точки слишком далеко от центра (вне круга)
                if (dx * dx + dz * dz > searchRadius * searchRadius) {
                    continue;
                }

                Location candidate = center.clone().add(dx, 0, dz);
                // Используем рекурсивный вызов, но с ограничением глубины
                Location validLocation = findValidTeleportLocationSingle(candidate, world);
                if (validLocation != null) {
                    return validLocation;
                }
            }
        }

        return null;
    }

    private Location findValidTeleportLocationRandomY(Location location, World world, int minY, int maxHeight,
            boolean checkBlockAbove, int maxAttempts) {
        // Логика для спавна на поверхности (как в ваниле)
        // Используем getHighestBlockYAt для поиска поверхности
        int surfaceY = world.getHighestBlockYAt(location.getBlockX(), location.getBlockZ());

        // Проверяем чанк
        int chunkX = location.getBlockX() >> 4;
        int chunkZ = location.getBlockZ() >> 4;
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            world.loadChunk(chunkX, chunkZ);
            if (!world.isChunkLoaded(chunkX, chunkZ)) {
                if (configManager.isRtpDebug()) {
                    plugin.getLogger().warning("[RTP Debug] Не удалось загрузить чанк " + chunkX + ", " + chunkZ);
                }
                return null;
            }
        }

        // Проверяем место на поверхности
        Location testLocation = new Location(world, location.getX(), surfaceY + 1, location.getZ());

        // Проверяем блок под ногами
        Material groundBlock = world.getBlockAt(testLocation.clone().add(0, -1, 0)).getType();
        if (!groundBlock.isSolid()) {
            if (configManager.isRtpDebug()) {
                plugin.getLogger().warning("[RTP Debug] Блок под ногами не твердый на поверхности");
            }
            return null;
        }

        // Проверяем место для спавна
        Material spawnBlock = world.getBlockAt(testLocation).getType();
        if (!isSafeBlock(spawnBlock)) {
            if (configManager.isRtpDebug()) {
                plugin.getLogger().warning("[RTP Debug] Место для спавна небезопасно на поверхности");
            }
            return null;
        }

        // Проверяем воду/лаву
        if (isWaterOrLava(spawnBlock)) {
            if (configManager.isRtpDebug()) {
                plugin.getLogger().warning("[RTP Debug] Место в воде/лаве на поверхности");
            }
            return null;
        }

        // Проверяем блок над головой
        Material headBlock = world.getBlockAt(testLocation.clone().add(0, 1, 0)).getType();
        if (isWaterOrLava(headBlock) || !isSafeBlock(headBlock)) {
            if (configManager.isRtpDebug()) {
                plugin.getLogger().warning("[RTP Debug] Блок над головой небезопасен на поверхности");
            }
            return null;
        }

        // Если включена проверка блока над головой, пропускаем (на поверхности нет
        // блока над головой)
        if (checkBlockAbove) {
            if (configManager.isRtpDebug()) {
                plugin.getLogger().info(
                        "[RTP Debug] Проверка блока над головой включена, но на поверхности нет блока - пропускаем проверку");
            }
            // На поверхности нет блока над головой, но все равно возвращаем место
        }

        // Нашли подходящее место на поверхности!
        return testLocation;
    }

    private Location findValidTeleportLocationSingle(Location location, World world) {
        // Упрощенная версия без рекурсии - только поиск сверху вниз в одной точке
        try {
            if (location == null || world == null) {
                return null;
            }

            int minHeight = configManager.getRtpMinHeight();
            int maxHeight = configManager.getRtpMaxHeight();
            boolean checkBlockAbove = configManager.shouldRtpCheckBlockAbove();

            int startY = Math.min((int) location.getY(), maxHeight);
            int minY = Math.max(world.getMinHeight(), minHeight);

            // Ищем подходящее место, начиная с максимальной высоты и спускаясь вниз
            for (int y = startY; y >= minY; y--) {
                Location testLocation = new Location(world, location.getX(), y, location.getZ());

                // Проверяем чанк
                int testChunkX = testLocation.getBlockX() >> 4;
                int testChunkZ = testLocation.getBlockZ() >> 4;
                if (!world.isChunkLoaded(testChunkX, testChunkZ)) {
                    world.loadChunk(testChunkX, testChunkZ);
                    if (!world.isChunkLoaded(testChunkX, testChunkZ)) {
                        continue;
                    }
                }

                // Проверяем блок под ногами
                Material groundBlock = world.getBlockAt(testLocation.clone().add(0, -1, 0)).getType();
                if (!groundBlock.isSolid()) {
                    continue;
                }

                // Проверяем место для спавна
                Material spawnBlock = world.getBlockAt(testLocation).getType();
                if (!isSafeBlock(spawnBlock)) {
                    continue;
                }

                // Проверяем воду/лаву
                if (isWaterOrLava(spawnBlock)) {
                    continue;
                }

                // Проверяем блок над головой
                Material headBlock = world.getBlockAt(testLocation.clone().add(0, 1, 0)).getType();
                if (isWaterOrLava(headBlock) || !isSafeBlock(headBlock)) {
                    continue;
                }

                // Проверяем блок над головой (защита от солнца)
                if (checkBlockAbove) {
                    Material blockAtHeight2 = world.getBlockAt(testLocation.clone().add(0, 2, 0)).getType();
                    if (!isSafeBlock(blockAtHeight2) || isWaterOrLava(blockAtHeight2)) {
                        continue;
                    }

                    // Проверяем наличие блока выше
                    boolean hasBlockAbove = false;
                    int startCheckY = (int) testLocation.getY() + 2;
                    int maxCheckHeight = world.getMaxHeight();

                    for (int checkY = startCheckY; checkY <= maxCheckHeight; checkY++) {
                        Location checkLocation = new Location(world, testLocation.getX(), checkY, testLocation.getZ());
                        int checkChunkX = checkLocation.getBlockX() >> 4;
                        int checkChunkZ = checkLocation.getBlockZ() >> 4;
                        if (!world.isChunkLoaded(checkChunkX, checkChunkZ)) {
                            world.loadChunk(checkChunkX, checkChunkZ);
                            if (!world.isChunkLoaded(checkChunkX, checkChunkZ)) {
                                continue;
                            }
                        }

                        Material blockAbove = world.getBlockAt(checkLocation).getType();
                        if (!blockAbove.isAir() &&
                                blockAbove != Material.CAVE_AIR &&
                                blockAbove != Material.VOID_AIR &&
                                !isWaterOrLava(blockAbove)) {
                            hasBlockAbove = true;
                            break;
                        }
                    }

                    if (!hasBlockAbove) {
                        continue;
                    }
                }

                // Нашли подходящее место!
                return testLocation;
            }

            return null;
        } catch (Exception e) {
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
        return material.isAir() ||
                material == Material.CAVE_AIR ||
                material == Material.VOID_AIR ||
                !material.isSolid();
    }

    /**
     * Применяет визуальные эффекты к игроку при телепортации
     * 
     * @param player           Игрок
     * @param location         Локация для показа эффектов
     * @param isBeforeTeleport true - эффекты до телепортации, false - после
     */
    private void applyVisualEffects(Player player, Location location, boolean isBeforeTeleport) {
        if (!configManager.isRtpVisualEffectsEnabled()) {
            return;
        }

        // Применяем эффекты зелий только после телепортации
        if (!isBeforeTeleport) {
            for (PotionEffect effect : configManager.getRtpPotionEffects()) {
                player.addPotionEffect(effect);
            }
        }

        // Показываем частицы
        String particleName = configManager.getRtpParticleEffect();
        if (particleName != null && !particleName.isEmpty()) {
            try {
                Particle particle = Particle.valueOf(particleName);
                int count = configManager.getRtpParticleCount();

                World world = location.getWorld();
                if (world == null) {
                    return;
                }

                // Показываем частицы сразу на указанной локации
                // Показываем частицы вокруг локации (кольцо) - видны всем
                double radius = 1.0;
                int particlesPerRing = Math.min(count, 30); // Ограничиваем для производительности

                for (int i = 0; i < particlesPerRing; i++) {
                    double angle = (2 * Math.PI * i) / particlesPerRing;
                    double x = location.getX() + radius * Math.cos(angle);
                    double y = location.getY() + 0.5;
                    double z = location.getZ() + radius * Math.sin(angle);

                    Location particleLoc = new Location(world, x, y, z);
                    world.spawnParticle(particle, particleLoc, 1, 0, 0, 0, 0);
                }

                // Показываем частицы в центре - видны всем
                int centerCount = Math.max(1, count / 3);
                world.spawnParticle(particle, location.clone().add(0, 1, 0), centerCount, 0.5, 1.0, 0.5, 0.1);

                // Показываем частицы снизу вверх (эффект телепортации) - видны всем
                for (int i = 0; i < 10; i++) {
                    double y = location.getY() + (i * 0.2);
                    Location verticalLoc = location.clone();
                    verticalLoc.setY(y);
                    world.spawnParticle(particle, verticalLoc, 2, 0.3, 0, 0.3, 0);
                }

            } catch (IllegalArgumentException e) {
                if (configManager.isRtpDebug()) {
                    plugin.getLogger().warning("[RTP Debug] Неизвестный тип частиц: " + particleName);
                }
            } catch (Exception e) {
                if (configManager.isRtpDebug()) {
                    plugin.getLogger().warning("[RTP Debug] Ошибка при показе частиц: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }
}
