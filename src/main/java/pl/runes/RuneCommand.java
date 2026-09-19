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

    private static final String USAGE = "/rune <give|list|reload|resetcd|status|resetcrafted>";

    private final RuneManager manager;

    public RuneCommand(RuneManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        boolean admin = sender.hasPermission("runes.admin");
        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + (admin ? USAGE : "/rune status"));
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        // everyone may use /rune status, everything else is admin-only
        if (!admin && !sub.equals("status")) {
            sender.sendMessage(ChatColor.RED + "Brak uprawnien. Dostepna komenda: /rune status");
            return true;
        }
        switch (sub) {
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
            case "status", "crafted" -> {
                sender.sendMessage(ChatColor.GOLD + "=== Status run ===");
                for (RuneType t : RuneType.values()) {
                    if (!manager.isCrafted(t)) {
                        sender.sendMessage(ChatColor.GREEN + "[wolna] " + ChatColor.GOLD + t.displayName
                                + ChatColor.GRAY + " - jeszcze nie stworzona");
                        continue;
                    }
                    String crafter = manager.crafterOf(t);
                    List<String> holders = manager.holdersOf(t);
                    List<String> ground = manager.groundLocationsOf(t);
                    StringBuilder sb = new StringBuilder();
                    sb.append(ChatColor.RED).append("[stworzona] ").append(ChatColor.GOLD).append(t.displayName);
                    sb.append(ChatColor.GRAY).append(" - stworzyl: ").append(ChatColor.YELLOW)
                            .append(crafter != null ? crafter : "?");
                    sb.append(ChatColor.GRAY).append(" | ma: ").append(ChatColor.YELLOW);
                    if (!holders.isEmpty()) {
                        sb.append(String.join(", ", holders));
                    } else if (!ground.isEmpty()) {
                        sb.append("na ziemi");
                        if (admin) sb.append(" (").append(String.join("; ", ground)).append(")");
                    } else {
                        sb.append("nieznane (gracz offline lub poza zaladowanym terenem)");
                    }
                    sender.sendMessage(sb.toString());
                }
            }
            case "resetcrafted" -> {
                if (args.length < 2) {
                    sender.sendMessage(ChatColor.YELLOW + "/rune resetcrafted <typ|all>");
                    return true;
                }
                if (args[1].equalsIgnoreCase("all")) {
                    manager.resetAllCrafted();
                    sender.sendMessage(ChatColor.GREEN + "Wszystkie runy mozna znow stworzyc.");
                } else {
                    RuneType type = RuneType.fromString(args[1]);
                    if (type == null) {
                        sender.sendMessage(ChatColor.RED + "Nieznany typ runy. Uzyj /rune list");
                        return true;
                    }
                    manager.resetCrafted(type);
                    sender.sendMessage(ChatColor.GREEN + type.displayName + " mozna znow stworzyc.");
                }
            }
            case "resetcd" -> {
                Player target;
                if (args.length >= 2) {
                    target = Bukkit.getPlayerExact(args[1]);
                } else {
                    target = sender instanceof Player p ? p : null;
                }
                if (target == null) {
                    sender.sendMessage(ChatColor.RED + "Nie znaleziono gracza.");
                    return true;
                }
                manager.resetCooldowns(target);
                sender.sendMessage(ChatColor.GREEN + "Zresetowano cooldowny graczowi " + target.getName());
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
            default -> sender.sendMessage(ChatColor.YELLOW + USAGE);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!sender.hasPermission("runes.admin")) {
            if (args.length == 1) out.add("status");
            return out;
        }
        if (args.length == 1) {
            out.addAll(List.of("give", "list", "reload", "resetcd", "status", "resetcrafted"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("resetcrafted")) {
            out.add("all");
            Arrays.stream(RuneType.values()).forEach(t -> out.add(t.name()));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("resetcd")) {
            Bukkit.getOnlinePlayers().forEach(p -> out.add(p.getName()));
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
