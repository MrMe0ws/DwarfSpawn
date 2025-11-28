package com.dwarfspawn;

import com.dwarfspawn.commands.DwarfSpawnCommand;
import com.dwarfspawn.listeners.PlayerDeathListener;
import org.bukkit.plugin.java.JavaPlugin;

public class DwarfSpawn extends JavaPlugin {

    private static DwarfSpawn instance;
    private ConfigManager configManager;

    @Override
    public void onEnable() {
        instance = this;

        // Сохраняем конфиг по умолчанию
        saveDefaultConfig();

        // Инициализируем менеджер конфигурации
        configManager = new ConfigManager(this);

        // Регистрируем слушателей
        getServer().getPluginManager().registerEvents(new PlayerDeathListener(configManager), this);

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
}
