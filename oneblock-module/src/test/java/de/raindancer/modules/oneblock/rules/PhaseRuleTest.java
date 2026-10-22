package de.raindancer.modules.oneblock.rules;

import de.raindancer.modules.oneblock.model.EconomyMode;
import de.raindancer.modules.oneblock.model.Island;
import de.raindancer.modules.oneblock.model.IslandDimension;
import de.raindancer.modules.oneblock.model.Phase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PhaseRuleTest {

    private final PhaseRule rule = new PhaseRule();

    private static Island island() {
        return Island.create(UUID.randomUUID(), UUID.randomUUID(), "Ada", EconomyMode.PURE, 0L);
    }

    private static Island brokenTimes(Island island, IslandDimension dimension, long times) {
        return island.withBlock(dimension, block -> block.withBroken(times));
    }

    @Test
    @DisplayName("the phases run in order of where they start, whatever order the file had them in")
    void sorted() {
        assertThat(Contents.content().phasesOf(IslandDimension.OVERWORLD))
                .containsExactly(Contents.MEADOW, Contents.CAVES, Contents.DEPTHS);
    }

    @Test
    @DisplayName("a phase holds from its first block up to the next phase's; the last holds for good")
    void current() {
        assertThat(rule.at(Contents.content(), IslandDimension.OVERWORLD, 0)).isEqualTo(Contents.MEADOW);
        assertThat(rule.at(Contents.content(), IslandDimension.OVERWORLD, 99)).isEqualTo(Contents.MEADOW);
        assertThat(rule.at(Contents.content(), IslandDimension.OVERWORLD, 100)).isEqualTo(Contents.CAVES);
        assertThat(rule.at(Contents.content(), IslandDimension.OVERWORLD, 1_000_000)).isEqualTo(Contents.DEPTHS);
    }

    @Test
    @DisplayName("a phase an admin names: by its number, its id or its name, any case; nothing else")
    void named() {
        var content = Contents.content();
        assertThat(rule.named(content, IslandDimension.OVERWORLD, "2")).contains(Contents.CAVES);
        assertThat(rule.named(content, IslandDimension.OVERWORLD, "depths")).contains(Contents.DEPTHS);
        assertThat(rule.named(content, IslandDimension.OVERWORLD, "MEADOW")).contains(Contents.MEADOW);
        assertThat(rule.named(content, IslandDimension.OVERWORLD, "0")).isEmpty();
        assertThat(rule.named(content, IslandDimension.OVERWORLD, "4")).isEmpty();
        assertThat(rule.named(content, IslandDimension.OVERWORLD, "wastes")).as("the nether's, not this one's").isEmpty();
        assertThat(rule.named(content, IslandDimension.NETHER, "wastes")).contains(Contents.WASTES);
    }

    @Test
    @DisplayName("a dimension without phases of its own still plays: one phase of its starting block")
    void fallback() {
        Phase end = rule.at(Contents.content(), IslandDimension.END, 500);
        assertThat(end.from()).isZero();
        assertThat(end.blocks()).extracting(Phase.Weighted::material).containsExactly(IslandDimension.END.icon());
        assertThat(end.mobs()).isEmpty();
    }

    @Test
    @DisplayName("progress: how far through this phase, the next one and how many blocks are left until it")
    void progress() {
        PhaseRule.Standing early = rule.standing(Contents.content(), IslandDimension.OVERWORLD, 25);
        assertThat(early.phase()).isEqualTo(Contents.MEADOW);
        assertThat(early.next()).contains(Contents.CAVES);
        assertThat(early.percent()).isEqualTo(25);
        assertThat(early.toNext()).isEqualTo(75);
        assertThat(early.number()).isEqualTo(1);
        assertThat(early.of()).isEqualTo(3);

        PhaseRule.Standing caves = rule.standing(Contents.content(), IslandDimension.OVERWORLD, 250);
        assertThat(caves.percent()).isEqualTo(75);
        assertThat(caves.toNext()).isEqualTo(50);

        PhaseRule.Standing last = rule.standing(Contents.content(), IslandDimension.OVERWORLD, 9_999);
        assertThat(last.next()).isEmpty();
        assertThat(last.percent()).isEqualTo(100);
        assertThat(last.toNext()).isZero();
    }

    @Test
    @DisplayName("a break counts, and the break that reaches a new phase says which one it entered")
    void entering() {
        Island at99 = brokenTimes(island(), IslandDimension.OVERWORLD, 98);

        PhaseRule.Broken first = rule.broken(Contents.content(), at99, IslandDimension.OVERWORLD);
        assertThat(first.island().block(IslandDimension.OVERWORLD).orElseThrow().broken()).isEqualTo(99);
        assertThat(first.entered()).isEmpty();

        PhaseRule.Broken second = rule.broken(Contents.content(), first.island(), IslandDimension.OVERWORLD);
        assertThat(second.entered()).contains(Contents.CAVES);
    }

    @Test
    @DisplayName("reaching a phase that opens a dimension opens it, once")
    void opens() {
        Island before = brokenTimes(island(), IslandDimension.OVERWORLD, 99);

        PhaseRule.Broken broken = rule.broken(Contents.content(), before, IslandDimension.OVERWORLD);
        assertThat(broken.opened()).containsExactly(IslandDimension.NETHER);
        assertThat(broken.island().unlocked(IslandDimension.NETHER)).isTrue();
        assertThat(broken.island().unlocked(IslandDimension.END)).isFalse();

        PhaseRule.Broken again = rule.broken(Contents.content(), broken.island(), IslandDimension.OVERWORLD);
        assertThat(again.opened()).isEmpty();
    }

    @Test
    @DisplayName("an island already past an opening phase gets it on its next break: islands from before phases")
    void catchesUp() {
        Island long_played = brokenTimes(island(), IslandDimension.OVERWORLD, 5_000);

        assertThat(rule.due(Contents.content(), long_played)).containsExactlyInAnyOrder(IslandDimension.NETHER,
                IslandDimension.END);
        PhaseRule.Broken broken = rule.broken(Contents.content(), long_played, IslandDimension.OVERWORLD);
        assertThat(broken.opened()).containsExactlyInAnyOrder(IslandDimension.NETHER, IslandDimension.END);
        assertThat(rule.opening(Contents.content(), long_played)).isPresent();
        assertThat(rule.opening(Contents.content(), broken.island())).isEmpty();
    }

    @Test
    @DisplayName("a dimension opened before stays open, even when the island has not reached its phase")
    void openStaysOpen() {
        Island bought = island().unlocking(IslandDimension.END).orElseThrow();

        assertThat(rule.due(Contents.content(), bought)).isEmpty();
        assertThat(rule.broken(Contents.content(), bought, IslandDimension.OVERWORLD).island()
                .unlocked(IslandDimension.END)).isTrue();
    }

    @Test
    @DisplayName("what opens a dimension: the phase, its dimension and how many blocks there are still to go")
    void opener() {
        Island island = brokenTimes(island(), IslandDimension.OVERWORLD, 40);

        PhaseRule.Opener nether = rule.opener(Contents.content(), island, IslandDimension.NETHER).orElseThrow();
        assertThat(nether.phase()).isEqualTo(Contents.CAVES);
        assertThat(nether.in()).isEqualTo(IslandDimension.OVERWORLD);
        assertThat(nether.toGo()).isEqualTo(60);
        assertThat(rule.opener(Contents.content(), island, IslandDimension.OVERWORLD)).isEmpty();
    }

    @Test
    @DisplayName("breaks in the nether count for the nether's own phases, not the overworld's")
    void perDimension() {
        Island island = island().unlocking(IslandDimension.NETHER).orElseThrow();

        PhaseRule.Broken broken = rule.broken(Contents.content(), island, IslandDimension.NETHER);
        assertThat(broken.island().block(IslandDimension.NETHER).orElseThrow().broken()).isEqualTo(1);
        assertThat(broken.island().block(IslandDimension.OVERWORLD).orElseThrow().broken()).isZero();
    }
}
