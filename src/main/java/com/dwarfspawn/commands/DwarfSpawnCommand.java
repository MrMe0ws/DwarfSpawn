package com.dwarfspawn.commands;

import com.dwarfspawn.ConfigManager;
import com.dwarfspawn.DwarfSpawn;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;

public class DwarfSpawnCommand implements CommandExecutor, TabCompleter {
    private final DwarfSpawn plugin;
    private final ConfigManager configManager;

    public DwarfSpawnCommand(DwarfSpawn plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§6[DwarfSpawn] §7Использование: /dwarfspawn reload");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("dwarfspawn.reload")) {
                sender.sendMessage("§c[DwarfSpawn] §7У вас нет прав на выполнение этой команды!");
                return true;
            }

            try {
                configManager.reloadConfig();
                sender.sendMessage("§6[DwarfSpawn] §aКонфигурация успешно перезагружена!");
            } catch (Exception e) {
                sender.sendMessage("§c[DwarfSpawn] §7Ошибка при перезагрузке конфигурации: " + e.getMessage());
                plugin.getLogger().severe("Ошибка при перезагрузке конфигурации: " + e.getMessage());
                e.printStackTrace();
            }
            return true;
        }

        sender.sendMessage("§6[DwarfSpawn] §7Неизвестная подкоманда. Используйте: /dwarfspawn reload");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            if (sender.hasPermission("dwarfspawn.reload")) {
                completions.add("reload");
            }
            return completions;
        }
        return new ArrayList<>();
    }
}
