package com.dwarfspawn;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;

import java.util.*;

public class StartKitManager {
    private final ConfigManager configManager;
    private final Map<UUID, Long> lastKitGiven = new HashMap<>(); // UUID игрока -> время последней выдачи

    public StartKitManager(ConfigManager configManager) {
        this.configManager = configManager;
    }

    /**
     * Проверяет, можно ли выдать стартовый набор игроку
     * @param player Игрок
     * @return true, если можно выдать набор
     */
    public boolean canGiveKit(Player player) {
        if (!configManager.isStartKitEnabled()) {
            return false;
        }

        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        long cooldown = configManager.getStartKitCooldown() * 1000L; // Конвертируем секунды в миллисекунды

        // Проверяем, есть ли запись о последней выдаче
        if (lastKitGiven.containsKey(playerId)) {
            long lastGiven = lastKitGiven.get(playerId);
            long timeSinceLastGiven = currentTime - lastGiven;

            // Если прошло меньше времени, чем cooldown, не выдаем
            if (timeSinceLastGiven < cooldown) {
                return false;
            }
        }

        return true;
    }

    /**
     * Выдает стартовый набор игроку (предметы с учетом кулдауна)
     * @param player Игрок
     */
    public void giveStartKit(Player player) {
        if (!canGiveKit(player)) {
            return;
        }

        // Выдаем предметы
        List<ItemStack> items = configManager.getStartKitItems();
        for (ItemStack item : items) {
            if (item != null && item.getType() != Material.AIR) {
                // Проверяем, есть ли место в инвентаре
                HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(item);
                // Если инвентарь полон, выкидываем предметы на землю
                for (ItemStack left : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), left);
                }
            }
        }

        // Сохраняем время выдачи
        lastKitGiven.put(player.getUniqueId(), System.currentTimeMillis());
    }

    /**
     * Выдает эффекты игроку (всегда, независимо от кулдауна)
     * @param player Игрок
     */
    public void giveEffects(Player player) {
        if (!configManager.isStartKitEffectsEnabled()) {
            return;
        }

        // Применяем эффекты
        List<PotionEffect> effects = configManager.getStartKitEffects();
        for (PotionEffect effect : effects) {
            if (effect != null) {
                player.addPotionEffect(effect);
            }
        }
    }

    /**
     * Проверяет, есть ли у игрока точка спавна (кровать или якорь ада)
     * @param player Игрок
     * @return true, если у игрока есть точка спавна
     */
    public boolean hasSpawnPoint(Player player) {
        // Проверяем кровать
        if (player.getBedSpawnLocation() != null) {
            return true;
        }

        // Проверяем якорь ада через Paper API
        try {
            // В Paper API можно проверить через respawn event
            // Но для простоты считаем, что если нет кровати, то нет точки спавна
            // Якорь ада проверяется в PlayerRespawnEvent через isAnchorSpawn()
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Очищает данные об игроке (для освобождения памяти)
     * @param playerId UUID игрока
     */
    public void removePlayerData(UUID playerId) {
        lastKitGiven.remove(playerId);
    }

    /**
     * Очищает все данные (для перезагрузки плагина)
     */
    public void clearAllData() {
        lastKitGiven.clear();
    }
}

