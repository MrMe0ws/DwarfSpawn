package com.dwarfspawn;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
                ItemStack item = parseItemString(itemString);
                if (item != null) {
                    items.add(item);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Ошибка при парсинге предмета: " + itemString + " - " + e.getMessage());
            }
        }

        return items;
    }

    /**
     * Парсит строку предмета с поддержкой зачарований, названий и повреждений
     * Формат: "MATERIAL:AMOUNT|name:Название|damage:ПРОЧНОСТЬ|enchant:ЗАЧАРОВАНИЕ:УРОВЕНЬ"
     * Примеры:
     *   - "LEATHER_HELMET:1" - простой предмет
     *   - "LEATHER_HELMET:1|name:Шляпа от солнца" - с названием
     *   - "LEATHER_HELMET:1|name:Шляпа от солнца|damage:55" - с повреждением (55 из максимальной прочности)
     *   - "WOODEN_PICKAXE:1|enchant:BINDING_CURSE:1" - с зачарованием
     *   - "LEATHER_HELMET:1|name:Шляпа от солнца|damage:55|enchant:BINDING_CURSE:1" - все параметры
     */
    private ItemStack parseItemString(String itemString) {
        try {
            // Разделяем основную часть и дополнительные параметры
            String[] mainParts = itemString.split("\\|", 2);
            String basePart = mainParts[0];
            String extraParams = mainParts.length > 1 ? mainParts[1] : "";

            // Парсим основную часть (материал и количество)
            String[] parts = basePart.split(":");
            if (parts.length < 2) {
                plugin.getLogger().warning("Неверный формат предмета в start-kit-items: " + itemString);
                return null;
            }

            Material material;
            try {
                material = Material.valueOf(parts[0].toUpperCase());
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("Неверный материал в start-kit-items: " + parts[0] + " (строка: " + itemString + ")");
                return null;
            }

            int amount;
            try {
                amount = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("Неверное количество в start-kit-items: " + parts[1] + " (строка: " + itemString + ")");
                return null;
            }

        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        
        if (meta == null) {
            return item;
        }

        // Парсим дополнительные параметры
        if (!extraParams.isEmpty()) {
            String[] params = extraParams.split("\\|");
            
            for (String param : params) {
                if (param.isEmpty()) continue;
                
                String[] keyValue = param.split(":", 2);
                if (keyValue.length < 2) continue;
                
                String key = keyValue[0].toLowerCase();
                String value = keyValue[1];
                
                switch (key) {
                    case "name":
                        // Устанавливаем название предмета
                        LegacyComponentSerializer serializer = LegacyComponentSerializer.legacySection();
                        Component nameComponent = serializer.deserialize(value.replace('&', '§'));
                        meta.displayName(nameComponent);
                        break;
                        
                    case "damage":
                        // Устанавливаем повреждение (прочность)
                        try {
                            int damage = Integer.parseInt(value);
                            if (meta instanceof Damageable) {
                                Damageable damageable = (Damageable) meta;
                                int maxDurability = material.getMaxDurability();
                                if (maxDurability > 0) {
                                    // damage - это количество повреждения, а не оставшаяся прочность
                                    // Если указано 55, значит предмет поврежден на 55 единиц
                                    damageable.setDamage(Math.min(damage, maxDurability - 1));
                                }
                            }
                        } catch (NumberFormatException e) {
                            plugin.getLogger().warning("Неверное значение damage для предмета: " + itemString);
                        }
                        break;
                        
                    case "enchant":
                        // Добавляем зачарование
                        try {
                            String[] enchantParts = value.split(":");
                            if (enchantParts.length >= 2) {
                                String enchantName = enchantParts[0].toUpperCase();
                                Enchantment enchantment = null;
                                
                                // Пытаемся найти зачарование по имени
                                for (Enchantment ench : Enchantment.values()) {
                                    if (ench != null && ench.getKey().getKey().equalsIgnoreCase(enchantName)) {
                                        enchantment = ench;
                                        break;
                                    }
                                }
                                
                                // Если не нашли, пробуем через NamespacedKey
                                if (enchantment == null) {
                                    try {
                                        enchantment = Enchantment.getByKey(org.bukkit.NamespacedKey.minecraft(enchantName.toLowerCase()));
                                    } catch (Exception ignored) {
                                    }
                                }
                                
                                if (enchantment != null) {
                                    int level = Integer.parseInt(enchantParts[1]);
                                    meta.addEnchant(enchantment, level, true); // true = игнорировать ограничения
                                } else {
                                    plugin.getLogger().warning("Неизвестное зачарование: " + enchantParts[0]);
                                }
                            }
                        } catch (Exception e) {
                            plugin.getLogger().warning("Ошибка при парсинге зачарования: " + value + " - " + e.getMessage());
                        }
                        break;
                }
            }
        }
        
            item.setItemMeta(meta);
            return item;
        } catch (Exception e) {
            plugin.getLogger().warning("Ошибка при парсинге предмета: " + itemString + " - " + e.getMessage());
            e.printStackTrace();
            return null;
        }
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
        List<Component> pages = new ArrayList<>();

        for (String pageString : pageStrings) {
            Component pageComponent = parsePageWithLinks(pageString);
            pages.add(pageComponent);
        }

        if (pages.isEmpty()) {
            // Если страниц нет, добавляем дефолтную
            pages.add(Component.text("Добро пожаловать на сервер!"));
        }

        // Используем Adventure API для установки страниц с поддержкой ссылок
        // В Paper API используется метод pages() который возвращает BookMeta.Builder
        try {
            // Проверяем, есть ли метод pages() в BookMeta (Paper API)
            java.lang.reflect.Method pagesMethod = bookMeta.getClass().getMethod("pages", List.class);
            pagesMethod.invoke(bookMeta, pages);
        } catch (NoSuchMethodException e) {
            // Если метод не найден, пробуем использовать setPages с Component через рефлексию
            try {
                // Ищем метод setPages, который принимает List<? extends Component>
                for (java.lang.reflect.Method method : bookMeta.getClass().getMethods()) {
                    if (method.getName().equals("setPages") && method.getParameterCount() == 1) {
                        Class<?> paramType = method.getParameterTypes()[0];
                        if (List.class.isAssignableFrom(paramType)) {
                            method.invoke(bookMeta, pages);
                            break;
                        }
                    }
                }
            } catch (Exception ex) {
                // Если не поддерживается Component API, используем старый способ
                List<String> legacyPages = new ArrayList<>();
                for (String pageString : pageStrings) {
                    String formattedPage = pageString.replace('&', '§');
                    // Убираем формат ссылок [текст](url) для старого способа
                    formattedPage = formattedPage.replaceAll("\\[([^\\]]+)\\]\\([^\\)]+\\)", "$1");
                    legacyPages.add(formattedPage);
                }
                if (legacyPages.isEmpty()) {
                    legacyPages.add("Добро пожаловать на сервер!");
                }
                bookMeta.setPages(legacyPages);
            }
        } catch (Exception e) {
            // Если что-то пошло не так, используем старый способ
            List<String> legacyPages = new ArrayList<>();
            for (String pageString : pageStrings) {
                String formattedPage = pageString.replace('&', '§');
                // Убираем формат ссылок [текст](url) для старого способа
                formattedPage = formattedPage.replaceAll("\\[([^\\]]+)\\]\\([^\\)]+\\)", "$1");
                legacyPages.add(formattedPage);
            }
            if (legacyPages.isEmpty()) {
                legacyPages.add("Добро пожаловать на сервер!");
            }
            bookMeta.setPages(legacyPages);
        }
        
        book.setItemMeta(bookMeta);

        return book;
    }

    /**
     * Парсит страницу книги, преобразуя формат [текст](url) в кликабельные ссылки
     * @param pageString Исходная строка страницы
     * @return Component с поддержкой ссылок
     */
    private Component parsePageWithLinks(String pageString) {
        // Конвертируем цветовые коды & в формат Adventure
        LegacyComponentSerializer serializer = LegacyComponentSerializer.legacySection();
        Component baseComponent = serializer.deserialize(pageString.replace('&', '§'));

        // Паттерн для поиска ссылок в формате [текст](url)
        Pattern linkPattern = Pattern.compile("\\[([^\\]]+)\\]\\(([^\\)]+)\\)");
        Matcher matcher = linkPattern.matcher(pageString);

        // Если нет ссылок, просто возвращаем компонент с цветовыми кодами
        if (!matcher.find()) {
            return baseComponent;
        }

        // Строим компонент с ссылками
        matcher.reset();
        Component result = Component.empty();
        int lastEnd = 0;

        while (matcher.find()) {
            // Добавляем текст до ссылки
            if (matcher.start() > lastEnd) {
                String beforeLink = pageString.substring(lastEnd, matcher.start());
                Component beforeComponent = serializer.deserialize(beforeLink.replace('&', '§'));
                result = result.append(beforeComponent);
            }

            // Создаем кликабельную ссылку
            String linkText = matcher.group(1);
            String linkUrl = matcher.group(2);
            
            // Парсим цветовые коды в тексте ссылки
            Component linkComponent = serializer.deserialize(linkText.replace('&', '§'))
                    .clickEvent(ClickEvent.openUrl(linkUrl))
                    .decoration(TextDecoration.UNDERLINED, true)
                    .color(NamedTextColor.BLUE);

            result = result.append(linkComponent);
            lastEnd = matcher.end();
        }

        // Добавляем оставшийся текст после последней ссылки
        if (lastEnd < pageString.length()) {
            String afterLink = pageString.substring(lastEnd);
            Component afterComponent = serializer.deserialize(afterLink.replace('&', '§'));
            result = result.append(afterComponent);
        }

        return result;
    }

    // ============================================
    // Методы для команды /rtp
    // ============================================

    public boolean isRtpEnabled() {
        return config.getBoolean("rtp-enabled", true);
    }

    public String getRtpWorld() {
        return config.getString("rtp-world", "world");
    }

    public int getRtpRadius() {
        return config.getInt("rtp-radius", 100);
    }

    public boolean isRtpRandomY() {
        return config.getBoolean("rtp-random-y", false);
    }

    public int getRtpMinHeight() {
        return config.getInt("rtp-min-height", 25);
    }

    public int getRtpMaxHeight() {
        return config.getInt("rtp-max-height", 46);
    }

    public boolean shouldRtpCheckBlockAbove() {
        return config.getBoolean("rtp-check-block-above", true);
    }

    public int getRtpMaxAttempts() {
        return config.getInt("rtp-max-attempts", 100);
    }

    public int getRtpDelay() {
        return config.getInt("rtp-delay", 3);
    }

    public int getRtpCooldown() {
        return config.getInt("rtp-cooldown", 60);
    }

    public boolean isRtpDebug() {
        return config.getBoolean("rtp-debug", false);
    }

    public boolean isRtpVisualEffectsEnabled() {
        return config.getBoolean("rtp-visual-effects-enabled", true);
    }

    /**
     * Получает список эффектов зелий для применения после телепортации
     * @return Список эффектов зелий
     */
    public List<PotionEffect> getRtpPotionEffects() {
        List<PotionEffect> effects = new ArrayList<>();
        if (!isRtpVisualEffectsEnabled()) {
            return effects;
        }
        
        List<String> effectStrings = config.getStringList("rtp-potion-effects");

        for (String effectString : effectStrings) {
            // Пропускаем пустые строки
            if (effectString == null || effectString.trim().isEmpty()) {
                continue;
            }

            try {
                String[] parts = effectString.split(":");
                if (parts.length < 3) {
                    plugin.getLogger().warning("Неверный формат эффекта в rtp-potion-effects: " + effectString);
                    continue;
                }

                String effectName = parts[0].toUpperCase();
                int level = Integer.parseInt(parts[1]) - 1; // Уровень в Minecraft начинается с 0
                int duration = Integer.parseInt(parts[2]) * 20; // Конвертируем секунды в тики

                // Пытаемся найти через NamespacedKey (современный способ)
                PotionEffectType effectType = null;
                try {
                    effectType = PotionEffectType.getByKey(org.bukkit.NamespacedKey.minecraft(effectName.toLowerCase()));
                } catch (Exception ignored) {
                }
                
                // Если не нашли, пробуем устаревший метод (для совместимости)
                if (effectType == null) {
                    try {
                        effectType = PotionEffectType.getByName(effectName);
                    } catch (Exception ignored) {
                    }
                }

                if (effectType != null) {
                    effects.add(new PotionEffect(effectType, duration, level));
                } else {
                    plugin.getLogger().warning("Неизвестный тип эффекта в rtp-potion-effects: " + effectName);
                }
            } catch (NumberFormatException e) {
                plugin.getLogger().warning("Неверный формат эффекта в rtp-potion-effects: " + effectString);
            } catch (Exception e) {
                plugin.getLogger().warning("Ошибка при парсинге эффекта: " + effectString + " - " + e.getMessage());
            }
        }

        return effects;
    }

    /**
     * Получает тип частиц для визуального эффекта телепортации
     * @return Название типа частиц или null, если эффект отключен
     */
    public String getRtpParticleEffect() {
        if (!isRtpVisualEffectsEnabled()) {
            return null;
        }
        String particle = config.getString("rtp-particle-effect", "PORTAL");
        if (particle == null || particle.isEmpty() || particle.equalsIgnoreCase("NONE")) {
            return null;
        }
        return particle.toUpperCase();
    }

    /**
     * Получает количество частиц для визуального эффекта
     * @return Количество частиц
     */
    public int getRtpParticleCount() {
        return config.getInt("rtp-particle-count", 50);
    }

    /**
     * Получает настраиваемое сообщение для команды /rtp
     * @param key Ключ сообщения
     * @param defaultValue Значение по умолчанию
     * @return Сообщение с замененными переменными (если нужно)
     */
    public String getRtpMessage(String key, String defaultValue) {
        String message = config.getString("rtp-messages." + key, defaultValue);
        // Конвертируем цветовые коды & в §
        return message.replace('&', '§');
    }

    /**
     * Получает настраиваемое сообщение для команды /rtp с заменой переменных
     * @param key Ключ сообщения
     * @param defaultValue Значение по умолчанию
     * @param replacements Замены в формате "переменная=значение"
     * @return Сообщение с замененными переменными
     */
    public String getRtpMessage(String key, String defaultValue, String... replacements) {
        String message = getRtpMessage(key, defaultValue);
        
        // Заменяем переменные
        for (String replacement : replacements) {
            String[] parts = replacement.split("=", 2);
            if (parts.length == 2) {
                message = message.replace("{" + parts[0] + "}", parts[1]);
            }
        }
        
        return message;
    }
}
