package de.raindancer.modules.oneblock.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PreparingTest {

    private final Preparing preparing = new Preparing();
    private final UUID ada = UUID.randomUUID();

    @Test
    @DisplayName("one preparation per player at a time: the second command while waiting is turned away")
    void oneAtATime() {
        assertThat(preparing.begin(ada)).isTrue();
        assertThat(preparing.begin(ada)).isFalse();
        assertThat(preparing.begin(UUID.randomUUID())).isTrue();
    }

    @Test
    @DisplayName("finished, they can be sent again")
    void finished() {
        preparing.begin(ada);
        preparing.done(ada);

        assertThat(preparing.begin(ada)).isTrue();
    }
}
