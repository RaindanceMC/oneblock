package de.raindancer.modules.oneblock.rules;

import de.raindancer.modules.oneblock.model.Hold;
import de.raindancer.modules.oneblock.rules.UnloadRule.Decision;
import de.raindancer.modules.oneblock.rules.UnloadRule.Facts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class UnloadRuleTest {

    private static final long NOW = 1_000_000L;
    private static final long GRACE = 60_000L;
    private final UnloadRule rule = new UnloadRule();

    private static Facts facts(boolean loaded, int players, long emptySince, boolean autoUnload, Optional<Hold> hold) {
        return new Facts(loaded, players, emptySince, autoUnload, hold, NOW, GRACE);
    }

    @Test
    @DisplayName("an island somebody is on stays")
    void occupiedStays() {
        assertThat(rule.decide(facts(true, 1, -1, true, Optional.empty()))).isEqualTo(Decision.STAY);
    }

    @Test
    @DisplayName("an empty island is unloaded once the grace has passed, not before")
    void emptyAfterGrace() {
        assertThat(rule.decide(facts(true, 0, NOW - GRACE + 1, true, Optional.empty()))).isEqualTo(Decision.STAY);
        assertThat(rule.decide(facts(true, 0, NOW - GRACE, true, Optional.empty()))).isEqualTo(Decision.UNLOAD);
    }

    @Test
    @DisplayName("an island just emptied starts counting rather than going at once")
    void justEmptied() {
        assertThat(rule.decide(facts(true, 0, -1, true, Optional.empty()))).isEqualTo(Decision.START_COUNTING);
    }

    @Test
    @DisplayName("with auto-unload off nothing but an admin's hold takes it down")
    void autoUnloadOff() {
        assertThat(rule.decide(facts(true, 0, 0, false, Optional.empty()))).isEqualTo(Decision.STAY);
        assertThat(rule.decide(facts(false, 0, -1, false, Optional.empty()))).isEqualTo(Decision.LOAD);
        assertThat(rule.decide(facts(true, 0, 0, false, Optional.of(new Hold(Hold.Kind.DOWN, NOW + 1, "a")))))
                .isEqualTo(Decision.UNLOAD);
    }

    @Test
    @DisplayName("held up: loaded, and never unloaded, until the hold runs out")
    void heldUp() {
        Optional<Hold> up = Optional.of(new Hold(Hold.Kind.UP, NOW + 1, "a"));
        assertThat(rule.decide(facts(false, 0, -1, true, up))).isEqualTo(Decision.LOAD);
        assertThat(rule.decide(facts(true, 0, 0, true, up))).isEqualTo(Decision.STAY);

        Optional<Hold> over = Optional.of(new Hold(Hold.Kind.UP, NOW, "a"));
        assertThat(rule.decide(facts(true, 0, 0, true, over))).isEqualTo(Decision.UNLOAD);
    }

    @Test
    @DisplayName("held down: unloaded even with players on it, and kept so")
    void heldDown() {
        Optional<Hold> down = Optional.of(new Hold(Hold.Kind.DOWN, Hold.FOREVER, "a"));
        assertThat(rule.decide(facts(true, 3, -1, true, down))).isEqualTo(Decision.UNLOAD);
        assertThat(rule.decide(facts(false, 0, -1, false, down))).isEqualTo(Decision.NOTHING);
    }

    @Test
    @DisplayName("an unloaded island nobody asked for stays unloaded")
    void unloadedStaysUnloaded() {
        assertThat(rule.decide(facts(false, 0, -1, true, Optional.empty()))).isEqualTo(Decision.NOTHING);
    }

    @Test
    @DisplayName("an admin hold that ran out counts as no hold")
    void expiredHold() {
        assertThat(rule.activeHold(Optional.of(new Hold(Hold.Kind.DOWN, NOW, "a")), NOW)).isEmpty();
        assertThat(rule.activeHold(Optional.of(new Hold(Hold.Kind.DOWN, NOW + 1, "a")), NOW)).isPresent();
    }
}
