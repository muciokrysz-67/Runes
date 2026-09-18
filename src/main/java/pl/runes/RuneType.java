package pl.runes;

import org.bukkit.Material;

import java.util.Locale;

public enum RuneType {
    BREEZE("Breeze Rune", "Guster", Material.GUSTER_POTTERY_SHERD, 40,
            "Dash 20 bloków. Im dalej wlecisz w przeciwnika, tym większe obrażenia."),
    FLAME("Flame Rune", "Burn", Material.BURN_POTTERY_SHERD, 50,
            "Pasywnie: Fire Resistance (w Netherze także Resistance 1).",
            "Aktywnie: przez 10s woda wysycha w promieniu 10 bloków."),
    EXPLOSION("Explosion Rune", "Danger", Material.DANGER_POTTERY_SHERD, 30,
            "Wystrzeliwuje wzmocniony fireball (4 serca), który robi bum."),
    WIND("Wind Rune", "Flow", Material.FLOW_POTTERY_SHERD, 50,
            "Wyrzuca cię ~40 bloków w górę, potem ściąga do ziemi",
            "zadając fall damage w okolicy."),
    FROST("Frost Rune", "Snort", Material.SNORT_POTTERY_SHERD, 120,
            "Zamraża losowego gracza w promieniu 15 bloków na 3s."),
    BLADE("Blade Rune", "Blade", Material.BLADE_POTTERY_SHERD, 80,
            "Strength 3 na 15s. Po czasie czyści wszystkie efekty."),
    MINER("Miner Rune", "Miner", Material.MINER_POTTERY_SHERD, 60,
            "Pasywnie: Haste 1. Aktywnie: Haste 3 na 30s."),
    HEART("Heart Rune", "Heart", Material.HEART_POTTERY_SHERD, 67,
            "Pasywnie: +2 serca. Aktywnie: Resistance 3 na 10s."),
    HEARTBREAK("HeartBreak Rune", "Heartbreak", Material.HEARTBREAK_POTTERY_SHERD, 120,
            "Pasywnie: +2 serca.",
            "Aktywnie: ostatnio trafiona osoba traci 2 serca na 30s."),
    ANCIENT("Ancient Rune", "Mourner", Material.MOURNER_POTTERY_SHERD, 360,
            "Niezniszczalna klatka z sculka (kula, promień 16 bloków).");

    public final String displayName;
    public final String subtitle;
    public final Material defaultMaterial;
    public final int defaultCooldown;
    public final String[] description;

    RuneType(String displayName, String subtitle, Material material, int cooldown, String... description) {
        this.displayName = displayName;
        this.subtitle = subtitle;
        this.defaultMaterial = material;
        this.defaultCooldown = cooldown;
        this.description = description;
    }

    public static RuneType fromString(String s) {
        if (s == null) return null;
        try {
            return valueOf(s.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
