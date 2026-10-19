package de.raindancer.modules.oneblock.rules;

import de.raindancer.modules.oneblock.model.Content;
import de.raindancer.modules.oneblock.model.IslandDimension;
import de.raindancer.modules.oneblock.model.Phase;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.List;

/**
 * What a broken one block comes back as: a block of the phase its count is in now, by weight. The phase's mobs share
 * one roll, so two 5% mobs spawn on 10% of the breaks; a mob comes on top and the block still changes.
 */
public final class GeneratorRule implements IOneBlockRule {

    private static final double FULL = 100.0;
    private final PhaseRule phases = new PhaseRule();

    /** @param mob spawned on top of the block; null for none */
    public record Outcome(Material material, EntityType mob) {
    }

    /**
     * @param broken the block's count after this break
     * @param roll   0 (inclusive) to 100 (exclusive): the roll the phase's mobs share
     * @param pick   0 (inclusive) to 1 (exclusive): where in the blocks' weight it lands
     */
    public Outcome next(Content content, IslandDimension dimension, long broken, double roll, double pick) {
        Phase phase = phases.at(content, dimension, broken);
        return new Outcome(block(phase, dimension, pick), mob(phase, roll));
    }

    /** All of a phase's mob chances together, at most a hundred. */
    public double mobTotal(Phase phase) {
        return Math.min(FULL, phase.mobs().stream().mapToDouble(Phase.Mob::chance).sum());
    }

    private static EntityType mob(Phase phase, double roll) {
        double passed = 0;
        for (Phase.Mob mob : phase.mobs()) {
            passed = Math.min(FULL, passed + mob.chance());
            if (roll < passed) {
                return mob.type();
            }
        }
        return null;
    }

    private static Material block(Phase phase, IslandDimension dimension, double pick) {
        List<Phase.Weighted> blocks = phase.blocks();
        int total = blocks.stream().mapToInt(Phase.Weighted::weight).sum();
        if (total <= 0) {
            return dimension.icon();
        }
        double target = Math.clamp(pick, 0.0, 0.999_999) * total;
        int seen = 0;
        for (Phase.Weighted weighted : blocks) {
            seen += weighted.weight();
            if (target < seen) {
                return weighted.material();
            }
        }
        return blocks.getLast().material();
    }

    @Override
    public String describe() {
        return "what the one block turns into after a break";
    }
}
