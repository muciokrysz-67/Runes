package pl.runes;

import org.bukkit.plugin.java.JavaPlugin;

public class RunePlugin extends JavaPlugin {

    private RuneManager manager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        manager = new RuneManager(this);
        getServer().getPluginManager().registerEvents(new RuneListener(manager), this);

        RuneCommand command = new RuneCommand(manager);
        getCommand("rune").setExecutor(command);
        getCommand("rune").setTabCompleter(command);

        manager.startTasks();
    }

    @Override
    public void onDisable() {
        if (manager != null) manager.shutdown();
    }
}
