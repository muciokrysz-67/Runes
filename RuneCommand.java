package pl.runes;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
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
            sender.sendMessage(Component.text("Brak uprawnień.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(Component.text("/rune <give|list|reload>", NamedTextColor.YELLOW));
            return true;
        }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "list" -> {
                for (RuneType t : RuneType.values()) {
                    sender.sendMessage(Component.text(t.name() + " - " + t.displayName
                            + " (cooldown " + manager.cooldownSeconds(t) + "s)", NamedTextColor.GOLD));
                }
            }
            case "reload" -> {
                manager.reload();
                sender.sendMessage(Component.text("Przeładowano config.", NamedTextColor.GREEN));
            }
            case "give" -> {
                if (args.length < 2) {
                    sender.sendMessage(Component.text("/rune give <typ> [gracz] [ilość]", NamedTextColor.YELLOW));
                    return true;
                }
                RuneType type = RuneType.fromString(args[1]);
                if (type == null) {
                    sender.sendMessage(Component.text("Nieznany typ runy. Użyj /rune list", NamedTextColor.RED));
                    return true;
                }
                Player target;
                if (args.length >= 3) {
                    target = Bukkit.getPlayerExact(args[2]);
                } else {
                    target = sender instanceof Player p ? p : null;
                }
                if (target == null) {
                    sender.sendMessage(Component.text("Nie znaleziono gracza.", NamedTextColor.RED));
                    return true;
                }
                int amount = 1;
                if (args.length >= 4) {
                    try {
                        amount = Math.max(1, Math.min(64, Integer.parseInt(args[3])));
                    } catch (NumberFormatException ignored) {
                    }
                }
                target.getInventory().addItem(manager.createRune(type, amount))
                        .values().forEach(left -> target.getWorld().dropItem(target.getLocation(), left));
                sender.sendMessage(Component.text("Dano " + type.displayName + " x" + amount
                        + " graczowi " + target.getName(), NamedTextColor.GREEN));
            }
            default -> sender.sendMessage(Component.text("/rune <give|list|reload>", NamedTextColor.YELLOW));
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
