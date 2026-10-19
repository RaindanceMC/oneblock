package de.raindancer.modules.oneblock.model;

import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.List;
import java.util.Set;

/**
 * One stretch of a dimension's block: from its first break on, the block turns into these blocks and now and then
 * brings one of these mobs, until the next phase starts. Reaching it may open another dimension.
 *
 * @param from   the block's count at which it starts
 * @param blocks weighted: what a break turns the block into
 * @param mobs   per break, each its own chance in percent; they share one roll
 * @param opens  the dimensions reaching it opens on the island
 */
public record Phase(String id, String name, long from, Material icon, List<Weighted> blocks, List<Mob> mobs,
                    Set<IslandDimension> opens) {

    public record Weighted(Material material, int weight) {
    }

    /** @param chance percent of the breaks, 0–100 */
    public record Mob(EntityType type, double chance) {
    }

    public Phase {
        from = Math.max(0, from);
        blocks = List.copyOf(blocks);
        mobs = List.copyOf(mobs);
        opens = Set.copyOf(opens);
    }

    /** The one every dimension has when the file gives it none: its starting block, nothing else. */
    public static Phase fallback(IslandDimension dimension) {
        return new Phase("start", "Beginnings", 0, dimension.icon(), List.of(new Weighted(dimension.icon(), 1)),
                List.of(), Set.of());
    }

    public Phase startingAt(long start) {
        return new Phase(id, name, start, icon, blocks, mobs, opens);
    }
}
