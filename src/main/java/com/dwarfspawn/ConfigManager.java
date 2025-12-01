package com.dwarfspawn;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public class ConfigManager {
    private final JavaPlugin plugin;
    private FileConfiguration config;

    public ConfigManager(JavaPlugin plugin) throws org.bukkit.configuration.InvalidConfigurationException {
        this.plugin = plugin;
        reloadConfig();
    }

    public void reloadConfig() throws org.bukkit.configuration.InvalidConfigurationException {
        try {
            plugin.reloadConfig();
            config = plugin.getConfig();
            
            // Проверяем, что конфиг действительно загрузился
            if (config == null) {
                throw new org.bukkit.configuration.InvalidConfigurationException("Конфигурация не была загружена");
            }
        } catch (org.bukkit.configuration.InvalidConfigurationException e) {
            // Пробрасываем исключение дальше
            throw e;
        } catch (Exception e) {
            // Оборачиваем другие исключения в InvalidConfigurationException
            throw new org.bukkit.configuration.InvalidConfigurationException("Ошибка при загрузке конфигурации: " + e.getMessage(), e);
        }
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

    // ============================================
    // Методы для стартового набора
    // ============================================

    public boolean isStartKitEnabled() {
        return config.getBoolean("start-kit-enabled", true);
    }

    public int getStartKitCooldown() {
        return config.getInt("start-kit-cooldown", 300);
    }

    public boolean isStartKitEffectsEnabled() {
        return config.getBoolean("start-kit-effects-enabled", true);
    }

    public List<ItemStack> getStartKitItems() {
        List<ItemStack> items = new ArrayList<>();
        List<String> itemStrings = config.getStringList("start-kit-items");

        for (String itemString : itemStrings) {
            try {
                String[] parts = itemString.split(":");
                if (parts.length < 2) {
                    plugin.getLogger().warning("Неверный формат предмета в start-kit-items: " + itemString);
                    continue;
                }

                Material material = Material.valueOf(parts[0].toUpperCase());
                int amount = Integer.parseInt(parts[1]);

                ItemStack item = new ItemStack(material, amount);
                items.add(item);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Неверный материал или количество в start-kit-items: " + itemString);
            } catch (Exception e) {
                plugin.getLogger().warning("Ошибка при парсинге предмета: " + itemString + " - " + e.getMessage());
            }
        }

        return items;
    }

    public List<PotionEffect> getStartKitEffects() {
        List<PotionEffect> effects = new ArrayList<>();
        List<String> effectStrings = config.getStringList("start-kit-effects");

        for (String effectString : effectStrings) {
            // Пропускаем пустые строки
            if (effectString == null || effectString.trim().isEmpty()) {
                continue;
            }

            try {
                String[] parts = effectString.split(":");
                if (parts.length < 3) {
                    plugin.getLogger().warning("Неверный формат эффекта в start-kit-effects: " + effectString);
                    continue;
                }

                PotionEffectType effectType = null;
                try {
                    // Пытаемся найти эффект по имени
                    for (PotionEffectType type : PotionEffectType.values()) {
                        if (type != null && type.getName() != null &&
                                type.getName().equalsIgnoreCase(parts[0])) {
                            effectType = type;
                            break;
                        }
                    }
                    // Если не нашли, пробуем через регистр
                    if (effectType == null) {
                        effectType = PotionEffectType.getByName(parts[0].toUpperCase());
                    }
                } catch (Exception e) {
                    // Игнорируем ошибки
                }

                if (effectType == null) {
                    plugin.getLogger().warning("Неизвестный тип эффекта: " + parts[0]);
                    continue;
                }

                int level = Integer.parseInt(parts[1]) - 1; // Уровень в Minecraft начинается с 0
                int duration = Integer.parseInt(parts[2]) * 20; // Конвертируем секунды в тики (20 тиков = 1 секунда)

                PotionEffect effect = new PotionEffect(effectType, duration, level);
                effects.add(effect);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Неверный формат эффекта в start-kit-effects: " + effectString);
            } catch (Exception e) {
                plugin.getLogger().warning("Ошибка при парсинге эффекта: " + effectString + " - " + e.getMessage());
            }
        }

        return effects;
    }

    // ============================================
    // Методы для книги при первом входе
    // ============================================

    public boolean isFirstJoinBookEnabled() {
        return config.getBoolean("first-join-book-enabled", true);
    }

    public ItemStack getFirstJoinBook() {
        if (!isFirstJoinBookEnabled()) {
            return null;
        }

        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta bookMeta = (BookMeta) book.getItemMeta();

        if (bookMeta == null) {
            return null;
        }

        // Устанавливаем заголовок и автора
        String title = config.getString("first-join-book-title", "Добро пожаловать!");
        String author = config.getString("first-join-book-author", "Администрация");

        // Убираем цветовые коды для заголовка (Minecraft не поддерживает цвета в
        // заголовке)
        title = title.replaceAll("&[0-9a-fk-or]", "");
        bookMeta.setTitle(title);
        bookMeta.setAuthor(author);

        // Устанавливаем страницы
        List<String> pageStrings = config.getStringList("first-join-book-pages");
        List<String> pages = new ArrayList<>();

        for (String pageString : pageStrings) {
            // Конвертируем цветовые коды & в §
            String formattedPage = pageString.replace('&', '§');
            pages.add(formattedPage);
        }

        if (pages.isEmpty()) {
            // Если страниц нет, добавляем дефолтную
            pages.add("Добро пожаловать на сервер!");
        }

        bookMeta.setPages(pages);
        book.setItemMeta(bookMeta);

        return book;
    }
}
