package de.raindancer.modules.oneblock.rules;

import de.raindancer.modules.oneblock.model.IslandDimension;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GeneratorRuleTest {

    private final GeneratorRule rule = new GeneratorRule();

    @Test
    @DisplayName("the phase's blocks decide by weight")
    void blocksByWeight() {
        assertThat(rule.next(Contents.content(), IslandDimension.OVERWORLD, 5, 99.9, 0.0).material())
                .isEqualTo(Material.DIRT);
        assertThat(rule.next(Contents.content(), IslandDimension.OVERWORLD, 5, 99.9, 0.74).material())
                .isEqualTo(Material.DIRT);
        assertThat(rule.next(Contents.content(), IslandDimension.OVERWORLD, 5, 99.9, 0.76).material())
                .isEqualTo(Material.OAK_LOG);
    }

    @Test
    @DisplayName("the block that comes back belongs to the phase of the count after the break")
    void followsThePhase() {
        assertThat(rule.next(Contents.content(), IslandDimension.OVERWORLD, 99, 99.9, 0.0).material())
                .isEqualTo(Material.DIRT);
        assertThat(rule.next(Contents.content(), IslandDimension.OVERWORLD, 100, 99.9, 0.0).material())
                .isEqualTo(Material.STONE);
        assertThat(rule.next(Contents.content(), IslandDimension.NETHER, 100, 99.9, 0.0).material())
                .isEqualTo(Material.NETHERRACK);
    }

    @Test
    @DisplayName("a mob hit spawns it on top and leaves the block to the phase's blocks")
    void mobs() {
        GeneratorRule.Outcome cow = rule.next(Contents.content(), IslandDimension.OVERWORLD, 5, 9.99, 0.0);
        assertThat(cow.mob()).isEqualTo(EntityType.COW);
        assertThat(cow.material()).isEqualTo(Material.DIRT);
        assertThat(rule.next(Contents.content(), IslandDimension.OVERWORLD, 5, 10.0, 0.0).mob()).isNull();
        assertThat(rule.next(Contents.content(), IslandDimension.OVERWORLD, 150, 0.0, 0.0).mob())
                .as("the caves have no mobs").isNull();
    }

    @Test
    @DisplayName("a phase's mobs share one roll, capped at a hundred percent")
    void mobsShareTheRoll() {
        assertThat(rule.mobTotal(Contents.DEPTHS)).isEqualTo(100.0);
        assertThat(rule.next(Contents.content(), IslandDimension.OVERWORLD, 400, 59.9, 0.0).mob())
                .isEqualTo(EntityType.ZOMBIE);
        assertThat(rule.next(Contents.content(), IslandDimension.OVERWORLD, 400, 60.0, 0.0).mob())
                .isEqualTo(EntityType.SKELETON);
        assertThat(rule.next(Contents.content(), IslandDimension.OVERWORLD, 400, 99.9, 0.0).mob())
                .isEqualTo(EntityType.SKELETON);
    }

    @Test
    @DisplayName("a dimension without phases of its own comes back as its own starting block")
    void fallback() {
        assertThat(rule.next(Contents.content(), IslandDimension.END, 50, 50, 0.5).material())
                .isEqualTo(Material.END_STONE);
    }
}
