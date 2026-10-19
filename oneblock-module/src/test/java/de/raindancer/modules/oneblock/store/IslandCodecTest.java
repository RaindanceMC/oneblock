package de.raindancer.modules.oneblock.store;

import de.raindancer.modules.oneblock.model.Island;
import de.raindancer.modules.oneblock.model.IslandDimension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IslandCodecTest {

    /** As 0.4.0 wrote it, with shards and bought upgrades. */
    private static final String FROM_SHARDS = """
            {"id":"6f1c2a4e-0a6b-4f1e-9a51-2a7c3d9e8b10","owner":"3673c488-98af-41b2-9b72-6894f42640de",
             "ownerName":"Lilly","mode":"smp","created":1234,"shards":512,"members":[],
             "blocks":{"overworld":{"x":0,"y":64,"z":0,"broken":4321,"upgrades":{"coal":3,"cow":1}},
                       "nether":{"x":0,"y":64,"z":0,"broken":12,"upgrades":{}}},
             "spawns":{},"autoUnload":true}
            """;

    @Test
    @DisplayName("an island saved with shards and upgrades reads with its counts and dimensions, the rest let go")
    void readsAnIslandFromShards() {
        Island island = IslandCodec.read(FROM_SHARDS).orElseThrow();

        assertThat(island.block(IslandDimension.OVERWORLD).orElseThrow().broken()).isEqualTo(4321);
        assertThat(island.block(IslandDimension.NETHER).orElseThrow().broken()).isEqualTo(12);
        assertThat(island.unlocked(IslandDimension.END)).isFalse();
        assertThat(island.ownerName()).isEqualTo("Lilly");
    }

    @Test
    @DisplayName("what is written has no shards and no upgrades any more")
    void writesNeither() {
        String written = IslandCodec.write(IslandCodec.read(FROM_SHARDS).orElseThrow());

        assertThat(written).doesNotContain("shards").doesNotContain("upgrades").contains("\"broken\":4321");
        assertThat(IslandCodec.read(written)).isEqualTo(IslandCodec.read(FROM_SHARDS));
    }
}
