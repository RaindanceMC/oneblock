package de.raindancer.modules.oneblock.rules;

import de.raindancer.modules.oneblock.model.EconomyMode;
import de.raindancer.modules.oneblock.model.Island;
import de.raindancer.modules.oneblock.model.IslandDimension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("What the OneBlock screens say")
class ScreenRuleTest {

    private final ScreenRule rule = new ScreenRule();

    private static Island island(String owner, long created, long overworld, long nether) {
        Island island = Island.create(UUID.randomUUID(), UUID.randomUUID(), owner, EconomyMode.PURE, created)
                .withBlock(IslandDimension.OVERWORLD, block -> block.withBroken(overworld));
        return nether < 0 ? island : island.unlocking(IslandDimension.NETHER).orElseThrow()
                .withBlock(IslandDimension.NETHER, block -> block.withBroken(nether));
    }

    @Test
    @DisplayName("the top islands are those with the most blocks broken across all dimensions, the older first on a tie")
    void top() {
        Island small = island("Small", 1, 10, -1);
        Island big = island("Big", 2, 100, 50);
        Island olderTie = island("Older", 3, 150, -1);
        Island newerTie = island("Newer", 4, 150, -1);
        assertThat(rule.top(List.of(small, newerTie, big, olderTie), 3)).containsExactly(big, olderTie, newerTie);
        assertThat(rule.top(List.of(), 3)).isEmpty();
    }

    @Test
    @DisplayName("an island nobody has broken a block on is not ranked")
    void untouchedIsLeftOut() {
        assertThat(rule.top(List.of(island("Idle", 1, 0, -1), island("Busy", 2, 5, -1)), 5))
                .extracting(Island::ownerName).containsExactly("Busy");
    }

    @Test
    @DisplayName("a place is coloured gold, grey, bronze and then white")
    void colours() {
        assertThat(rule.place(1)).isEqualTo("<gold>");
        assertThat(rule.place(2)).isEqualTo("<gray>");
        assertThat(rule.place(3)).isEqualTo("<#cd7f32>");
        assertThat(rule.place(4)).isEqualTo("<white>");
    }

    @Test
    @DisplayName("counts are grouped in thousands, the same way everywhere")
    void count() {
        assertThat(rule.count(1234567)).isEqualTo("1,234,567");
        assertThat(rule.count(0)).isEqualTo("0");
    }

    @Test
    @DisplayName("a team of one reads as 'just you', a bigger one as its size")
    void team() {
        assertThat(rule.team(1)).isEqualTo("just you");
        assertThat(rule.team(3)).isEqualTo("3 players");
    }
}
