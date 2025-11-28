package com.dwarfspawn;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

public class ConfigManager {
    private final JavaPlugin plugin;
    private FileConfiguration config;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
        reloadConfig();
    }

    public void reloadConfig() {
        plugin.reloadConfig();
        config = plugin.getConfig();
    }

    public boolean isRadiusEnabled() {
        return config.getBoolean("radius-enabled", true);
    }

    public int getSpawnRadius() {
        return config.getInt("spawn-radius", 50);
    }

    public Location getSpawnLocation() {
        String worldName = config.getString("spawn-location.world", "world");
        World world = Bukkit.getWorld(worldName);

        if (world == null) {
            // Пытаемся использовать первый доступный мир
            if (Bukkit.getWorlds().isEmpty()) {
                plugin.getLogger().severe("Не найдено ни одного мира! Невозможно создать точку спавна.");
                return null;
            }

            // Получаем список доступных миров для сообщения
            StringBuilder availableWorlds = new StringBuilder();
            for (World w : Bukkit.getWorlds()) {
                if (availableWorlds.length() > 0) {
                    availableWorlds.append(", ");
                }
                availableWorlds.append("'").append(w.getName()).append("'");
            }

            world = Bukkit.getWorlds().get(0);
            if (world != null) {
                plugin.getLogger().warning("═══════════════════════════════════════════════════════");
                plugin.getLogger().warning("Мир '" + worldName + "' не найден в конфигурации!");
                plugin.getLogger().warning("Доступные миры: " + availableWorlds.toString());
                plugin.getLogger().warning("Используется мир '" + world.getName() + "' по умолчанию.");
                plugin.getLogger().warning("Чтобы исправить, измените 'spawn-location.world' в config.yml");
                plugin.getLogger().warning("═══════════════════════════════════════════════════════");
            } else {
                plugin.getLogger().severe("Не удалось получить мир для точки спавна!");
                return null;
            }
        }

        double x = config.getDouble("spawn-location.x", 0);
        double y = config.getDouble("spawn-location.y", 50);
        double z = config.getDouble("spawn-location.z", 0);

        return new Location(world, x, y, z);
    }

    public int getMinSpawnHeight() {
        return config.getInt("min-spawn-height", 50);
    }

    public int getMaxSpawnHeight() {
        return config.getInt("max-spawn-height", 64);
    }

    public boolean shouldCheckBlockAbove() {
        return config.getBoolean("check-block-above", true);
    }

    public int getMaxSpawnAttempts() {
        return config.getInt("max-spawn-attempts", 100);
    }
}
