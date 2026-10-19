package de.raindancer.modules.oneblock.store;

import de.raindancer.core.platform.log.Log;
import de.raindancer.modules.oneblock.model.IslandDimension;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ContentStoreTest {

    /** What 0.4.0 wrote to the data folder: pools, upgrades and shards, no phases. */
    private static final String BEFORE_PHASES = """
            shards:
              every-blocks: 10
            pools:
              overworld:
                dirt: 5
            upgrades:
              coal:
                kind: block
                block: coal_ore
            team-size: 4
            """;

    @TempDir
    Path dir;

    @Test
    @DisplayName("a content.yml from before phases is kept as a backup and replaced by the shipped phases")
    void replacesTheOldFormat() throws Exception {
        Path file = dir.resolve("content.yml");
        Files.writeString(file, BEFORE_PHASES);

        ContentStore store = new ContentStore(file, Log.of("test"), material -> ContentReader.falls(material)
                ? java.util.Optional.of("falls") : java.util.Optional.empty());

        assertThat(store.current().phasesOf(IslandDimension.OVERWORLD)).hasSizeGreaterThan(1);
        assertThat(Files.readString(dir.resolve("content-before-phases.yml"))).isEqualTo(BEFORE_PHASES);
        assertThat(Files.readString(file)).contains("phases:").doesNotContain("upgrades:");
    }

    @Test
    @DisplayName("an owner's own phases are theirs: read as they are, nothing replaced")
    void keepsTheOwnersPhases() throws Exception {
        Path file = dir.resolve("content.yml");
        String own = """
                phases:
                  overworld:
                    only:
                      from: 0
                      blocks: {dirt: 1}
                """;
        Files.writeString(file, own);

        ContentStore store = new ContentStore(file, Log.of("test"), material -> ContentReader.falls(material)
                ? java.util.Optional.of("falls") : java.util.Optional.empty());

        assertThat(store.current().phasesOf(IslandDimension.OVERWORLD)).singleElement()
                .satisfies(phase -> assertThat(phase.id()).isEqualTo("only"));
        assertThat(Files.readString(file)).isEqualTo(own);
        assertThat(dir.resolve("content-before-phases.yml")).doesNotExist();
    }

    @Test
    @DisplayName("what counts as from before phases: no phases, but pools, upgrades or shards")
    void recognisesTheOldFormat() throws Exception {
        assertThat(ContentStore.beforePhases(yaml(BEFORE_PHASES))).isTrue();
        assertThat(ContentStore.beforePhases(yaml("phases: {}\nupgrades: {}"))).isFalse();
        assertThat(ContentStore.beforePhases(yaml("team-size: 4"))).isFalse();
    }

    private static org.bukkit.configuration.file.YamlConfiguration yaml(String text) throws Exception {
        org.bukkit.configuration.file.YamlConfiguration yaml = new org.bukkit.configuration.file.YamlConfiguration();
        yaml.loadFromString(text);
        return yaml;
    }
}
