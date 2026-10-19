package de.raindancer.modules.oneblock.store;

import de.raindancer.modules.oneblock.model.Content;
import de.raindancer.modules.oneblock.model.IslandDimension;
import de.raindancer.modules.oneblock.model.Phase;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * {@code content.yml} into {@link Content}. Nothing in the file stops the islands: a line that cannot be read is named
 * in {@link Result#problems()} and left out, so an owner's typo costs one block or one phase and a log line, not the
 * server.
 */
public final class ContentReader {

    public record Result(Content content, List<String> problems) {
    }

    /** Why a block cannot be the one block; empty when it can. */
    @FunctionalInterface
    public interface BlockCheck {
        Optional<String> refuse(Material material);
    }

    /**
     * What falls, by name: Paper's own {@link Material#hasGravity()} only answers on a running server, and a list in
     * the file must be checked where there is none too.
     */
    private static final Set<Material> FALLS = Set.of(Material.SAND, Material.RED_SAND, Material.SUSPICIOUS_SAND,
            Material.GRAVEL, Material.SUSPICIOUS_GRAVEL, Material.ANVIL, Material.CHIPPED_ANVIL, Material.DAMAGED_ANVIL,
            Material.DRAGON_EGG, Material.POINTED_DRIPSTONE, Material.SCAFFOLDING);

    private static final String FALLING = "falls into the void the moment anything touches it";

    /** On the server: nothing that falls, nothing to fall through. */
    public static final BlockCheck ON_A_SERVER = material -> falls(material) ? Optional.of(FALLING)
            : material.isSolid() ? Optional.empty() : Optional.of("not solid, so nobody could stand on it");


    private static final int TEAM_SIZE = 6;
    private static final int MOB_CAP = 12;

    private ContentReader() {
    }

    public static boolean falls(Material material) {
        return FALLS.contains(material) || material.name().endsWith("_CONCRETE_POWDER") || material.hasGravity();
    }

    public static Result read(ConfigurationSection yaml) {
        return read(yaml, ON_A_SERVER);
    }

    public static Result read(ConfigurationSection yaml, BlockCheck check) {
        List<String> problems = new ArrayList<>();
        Map<IslandDimension, List<Phase>> phases = new EnumMap<>(IslandDimension.class);
        ConfigurationSection all = yaml.getConfigurationSection("phases");
        if (all != null) {
            for (String key : all.getKeys(false)) {
                Optional<IslandDimension> dimension = IslandDimension.byId(key);
                if (dimension.isEmpty()) {
                    problems.add("phases." + key + ": no such dimension (overworld, nether, end)");
                    continue;
                }
                phases.put(dimension.get(), phases(all.getConfigurationSection(key), "phases." + key, check, problems));
            }
        }
        Content content = new Content(phases, yaml.getInt("team-size", TEAM_SIZE), yaml.getInt("mob-cap", MOB_CAP));
        return new Result(content, List.copyOf(problems));
    }

    private static List<Phase> phases(ConfigurationSection section, String path, BlockCheck check,
                                      List<String> problems) {
        List<Phase> phases = new ArrayList<>();
        if (section == null) {
            problems.add(path + ": not a section");
            return phases;
        }
        for (String id : section.getKeys(false)) {
            phase(id, section.getConfigurationSection(id), path + "." + id, check, problems).ifPresent(phases::add);
        }
        phases.stream().min(Comparator.comparingLong(Phase::from)).filter(first -> first.from() != 0)
                .ifPresent(first -> problems.add(path + "." + first.id() + ": the first phase starts at 0, not at "
                        + first.from() + " — read as 0"));
        return phases;
    }

    private static Optional<Phase> phase(String id, ConfigurationSection section, String path, BlockCheck check,
                                         List<String> problems) {
        if (section == null) {
            problems.add(path + ": not a section");
            return Optional.empty();
        }
        long from = section.getLong("from", 0);
        if (from < 0) {
            problems.add(path + ".from: " + from + " is below zero");
            return Optional.empty();
        }
        List<Phase.Weighted> blocks = blocks(section.getConfigurationSection("blocks"), path + ".blocks", check, problems);
        if (blocks.isEmpty()) {
            problems.add(path + ": no blocks, so it has nothing to turn into");
            return Optional.empty();
        }
        Material icon = Optional.ofNullable(Material.matchMaterial(section.getString("icon", "")))
                .filter(Material::isItem).orElse(blocks.getFirst().material());
        return Optional.of(new Phase(id, section.getString("name", id), from, icon, blocks,
                mobs(section.getConfigurationSection("mobs"), path + ".mobs", problems),
                opens(section.getStringList("opens"), path + ".opens", problems)));
    }

    private static List<Phase.Weighted> blocks(ConfigurationSection section, String path, BlockCheck check,
                                               List<String> problems) {
        List<Phase.Weighted> blocks = new ArrayList<>();
        if (section == null) {
            return blocks;
        }
        for (String name : section.getKeys(false)) {
            Material material = Material.matchMaterial(name);
            int weight = section.getInt(name, 0);
            Optional<String> refused = material == null ? Optional.empty() : check.refuse(material);
            if (material == null || !material.isBlock()) {
                problems.add(path + "." + name + ": not a block");
            } else if (refused.isPresent()) {
                problems.add(path + "." + name + ": " + refused.get());
            } else if (weight <= 0) {
                problems.add(path + "." + name + ": a weight must be above zero");
            } else {
                blocks.add(new Phase.Weighted(material, weight));
            }
        }
        return blocks;
    }

    private static List<Phase.Mob> mobs(ConfigurationSection section, String path, List<String> problems) {
        List<Phase.Mob> mobs = new ArrayList<>();
        if (section == null) {
            return mobs;
        }
        for (String name : section.getKeys(false)) {
            EntityType type = entity(name);
            double chance = section.getDouble(name, 0);
            if (type == null || !type.isAlive() || !type.isSpawnable()) {
                problems.add(path + "." + name + ": not a mob that can be spawned");
            } else if (chance <= 0) {
                problems.add(path + "." + name + ": a chance must be above zero");
            } else {
                mobs.add(new Phase.Mob(type, Math.min(100, chance)));
            }
        }
        return mobs;
    }

    private static Set<IslandDimension> opens(List<String> raw, String path, List<String> problems) {
        Set<IslandDimension> opens = EnumSet.noneOf(IslandDimension.class);
        for (String name : raw) {
            Optional<IslandDimension> dimension = IslandDimension.byId(name);
            if (dimension.isEmpty()) {
                problems.add(path + ": no such dimension '" + name + "'");
            } else if (dimension.get() == IslandDimension.OVERWORLD) {
                problems.add(path + ": the overworld is always open");
            } else {
                opens.add(dimension.get());
            }
        }
        return opens;
    }

    private static EntityType entity(String name) {
        try {
            return EntityType.valueOf(name.strip().toUpperCase(Locale.ROOT).replace("MINECRAFT:", ""));
        } catch (IllegalArgumentException unknown) {
            return null;
        }
    }
}
