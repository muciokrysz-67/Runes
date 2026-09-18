package pl.runes;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class RuneCommand implements CommandExecutor, TabCompleter {

    private final RuneManager manager;

    public RuneCommand(RuneManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("runes.admin")) {
            sender.sendMessage(ChatColor.RED + "Brak uprawnien.");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "/rune <give|list|reload>");
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "list" -> {
                for (RuneType t : RuneType.values()) {
                    sender.sendMessage(ChatColor.GOLD + t.name() + " - " + t.displayName
                            + " (cooldown " + manager.cooldownSeconds(t) + "s)");
                }
            }
            case "reload" -> {
                manager.reload();
                sender.sendMessage(ChatColor.GREEN + "Przeladowano config.");
            }
            case "give" -> {
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.YELLOW + "/rune give <typ> [gracz] [ilosc]");
                    return true;
                }
                RuneType type = RuneType.fromString(args[1]);
                if (type == null) {
                    sender.sendMessage(ChatColor.RED + "Nieznany typ runy. Uzyj /rune list");
                    return true;
                }
                Player target;
                if (args.length >= 3) {
                    target = Bukkit.getPlayerExact(args[2]);
                } else {
                    target = sender instanceof Player p ? p : null;
                }
                if (target == null) {
                    sender.sendMessage(ChatColor.RED + "Nie znaleziono gracza.");
                    return true;
                }
                int amount = 1;
                if (args.length >= 4) {
                    try {
                        amount = Math.max(1, Math.min(64, Integer.parseInt(args[3])));
                    } catch (NumberFormatException ignored) {
                    }
                }
                final Player receiver = target;
                receiver.getInventory().addItem(manager.createRune(type, amount))
                        .values().forEach(left -> receiver.getWorld().dropItem(receiver.getLocation(), left));
                sender.sendMessage(ChatColor.GREEN + "Dano " + type.displayName + " x" + amount
                        + " graczowi " + receiver.getName());
            }
            default -> sender.sendMessage(ChatColor.YELLOW + "/rune <give|list|reload>");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(List.of("give", "list", "reload"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            Arrays.stream(RuneType.values()).forEach(t -> out.add(t.name()));
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
        }
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(s -> !s.toLowerCase(Locale.ROOT).startsWith(prefix));
        return out;
    }
}
