package com.dwarfspawn;

import com.dwarfspawn.commands.DwarfSpawnCommand;
import com.dwarfspawn.listeners.PlayerDeathListener;
import org.bukkit.plugin.java.JavaPlugin;

public class DwarfSpawn extends JavaPlugin {

    private static DwarfSpawn instance;
    private ConfigManager configManager;
    private StartKitManager startKitManager;

    @Override
    public void onEnable() {
        instance = this;

        // Сохраняем конфиг по умолчанию
        saveDefaultConfig();

        // Инициализируем менеджер конфигурации
        try {
            configManager = new ConfigManager(this);
        } catch (org.bukkit.configuration.InvalidConfigurationException e) {
            getLogger().severe("═══════════════════════════════════════════════════════");
            getLogger().severe("КРИТИЧЕСКАЯ ОШИБКА: Не удалось загрузить конфигурацию!");
            getLogger().severe("Проверьте файл config.yml на наличие синтаксических ошибок.");
            getLogger().severe("Ошибка: " + e.getMessage());
            if (e.getCause() != null) {
                getLogger().severe("Причина: " + e.getCause().getMessage());
            }
            getLogger().severe("Плагин будет отключен.");
            getLogger().severe("═══════════════════════════════════════════════════════");
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        } catch (Exception e) {
            getLogger().severe("Неожиданная ошибка при загрузке конфигурации: " + e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Инициализируем менеджер стартового набора
        startKitManager = new StartKitManager(configManager);

        // Регистрируем слушателей
        getServer().getPluginManager().registerEvents(new PlayerDeathListener(configManager, startKitManager), this);

        // Регистрируем команды
        DwarfSpawnCommand command = new DwarfSpawnCommand(this, configManager);
        getCommand("dwarfspawn").setExecutor(command);
        getCommand("dwarfspawn").setTabCompleter(command);

        getLogger().info("DwarfSpawn плагин успешно загружен!");
    }

    @Override
    public void onDisable() {
        getLogger().info("DwarfSpawn плагин выгружен!");
    }

    public static DwarfSpawn getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public StartKitManager getStartKitManager() {
        return startKitManager;
    }
}
