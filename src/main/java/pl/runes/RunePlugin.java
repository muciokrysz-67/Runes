package pl.runes;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class RunePlugin extends JavaPlugin {

    private RuneManager manager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        manager = new RuneManager(this);
        getServer().getPluginManager().registerEvents(new RuneListener(manager), this);

        RuneCommand command = new RuneCommand(manager);
        PluginCommand rune = getCommand("rune");
        if (rune == null) {
            getLogger().severe("Komenda /rune nie jest zarejestrowana w plugin.yml!");
        } else {
            rune.setExecutor(command);
            rune.setTabCompleter(command);
        }

        manager.startTasks();
        getLogger().info("PotteryRunes wlaczony (v" + getDescription().getVersion() + ")");
    }

    @Override
    public void onDisable() {
        if (manager != null) manager.shutdown();
    }
}
