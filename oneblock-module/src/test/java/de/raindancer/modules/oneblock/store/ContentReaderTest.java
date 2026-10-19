package de.raindancer.modules.oneblock.store;

import de.raindancer.modules.oneblock.OneBlockModule;
import de.raindancer.modules.oneblock.model.Content;
import de.raindancer.modules.oneblock.model.IslandDimension;
import de.raindancer.modules.oneblock.model.Phase;
import org.bukkit.Material;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ContentReaderTest {

    private static YamlConfiguration yaml(String text) throws InvalidConfigurationException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.loadFromString(text);
        return yaml;
    }

    /**
     * Without a server Paper says every block is not solid and nothing falls, so the tests stand in for it: what falls
     * by name, and a torch for something that is not solid.
     */
    private static final ContentReader.BlockCheck OFFLINE = material -> ContentReader.falls(material)
            ? Optional.of("falls into the void") : material == Material.TORCH ? Optional.of("not solid")
            : Optional.empty();

    private static ContentReader.Result read(YamlConfiguration yaml) {
        return ContentReader.read(yaml, OFFLINE);
    }

    private static YamlConfiguration shipped() throws IOException, InvalidConfigurationException {
        try (InputStream in = OneBlockModule.class.getResourceAsStream("content.yml")) {
            assertThat(in).as("content.yml ships beside the module").isNotNull();
            return yaml(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    @Test
    @DisplayName("the shipped file reads without a single problem")
    void shippedIsClean() throws Exception {
        ContentReader.Result result = read(shipped());

        assertThat(result.problems()).isEmpty();
        Content content = result.content();
        assertThat(content.phasesOf(IslandDimension.OVERWORLD)).hasSizeGreaterThanOrEqualTo(8);
        assertThat(content.phasesOf(IslandDimension.NETHER)).hasSizeGreaterThanOrEqualTo(3);
        assertThat(content.phasesOf(IslandDimension.END)).hasSizeGreaterThanOrEqualTo(2);
        assertThat(content.teamSize()).isEqualTo(6);
        assertThat(content.phasesOf(IslandDimension.OVERWORLD).getFirst().id()).isEqualTo("meadow");
    }

    @Test
    @DisplayName("the shipped overworld opens the nether, and later the end")
    void shippedOpensBoth() throws Exception {
        List<Phase> overworld = read(shipped()).content().phasesOf(IslandDimension.OVERWORLD);

        Phase nether = overworld.stream().filter(phase -> phase.opens().contains(IslandDimension.NETHER)).findFirst()
                .orElseThrow();
        Phase end = overworld.stream().filter(phase -> phase.opens().contains(IslandDimension.END)).findFirst()
                .orElseThrow();
        assertThat(nether.from()).isPositive().isLessThan(end.from());
    }

    @Test
    @DisplayName("no shipped block falls into the void (whether each is solid, a real server's startup checks)")
    void blocksStand() throws Exception {
        Content content = read(shipped()).content();
        for (IslandDimension dimension : IslandDimension.values()) {
            for (Phase phase : content.phasesOf(dimension)) {
                assertThat(phase.blocks()).extracting(Phase.Weighted::material).as(dimension + " " + phase.id())
                        .isNotEmpty().allMatch(Material::isBlock).noneMatch(ContentReader::falls);
            }
        }
    }

    @Test
    @DisplayName("what falls is known by name, without a server: sand, gravel, concrete powder, anvils")
    void fallingByName() {
        assertThat(ContentReader.falls(Material.SAND)).isTrue();
        assertThat(ContentReader.falls(Material.GRAVEL)).isTrue();
        assertThat(ContentReader.falls(Material.LIME_CONCRETE_POWDER)).isTrue();
        assertThat(ContentReader.falls(Material.ANVIL)).isTrue();
        assertThat(ContentReader.falls(Material.STONE)).isFalse();
        assertThat(ContentReader.falls(Material.LIME_CONCRETE)).isFalse();
    }

    @Test
    @DisplayName("every shipped phase brings something new: no two phases of a dimension share a name")
    void namesDiffer() throws Exception {
        Content content = read(shipped()).content();
        for (IslandDimension dimension : IslandDimension.values()) {
            assertThat(content.phasesOf(dimension)).extracting(Phase::name).doesNotHaveDuplicates();
            assertThat(content.phasesOf(dimension)).extracting(Phase::from).doesNotHaveDuplicates();
        }
    }

    @Test
    @DisplayName("a phase reads with its name, start, icon, weighted blocks, mob chances and what it opens")
    void aPhase() throws Exception {
        ContentReader.Result result = read(yaml("""
                phases:
                  overworld:
                    meadow:
                      name: "Meadow"
                      from: 0
                      icon: grass_block
                      blocks: {grass_block: 3, dirt: 1}
                      mobs: {cow: 2.5}
                    caves:
                      name: "Caves"
                      from: 500
                      blocks: {stone: 1}
                      opens: [nether]
                """));

        assertThat(result.problems()).isEmpty();
        List<Phase> phases = result.content().phasesOf(IslandDimension.OVERWORLD);
        assertThat(phases).hasSize(2);
        Phase meadow = phases.getFirst();
        assertThat(meadow.name()).isEqualTo("Meadow");
        assertThat(meadow.icon()).isEqualTo(Material.GRASS_BLOCK);
        assertThat(meadow.blocks()).containsExactly(new Phase.Weighted(Material.GRASS_BLOCK, 3),
                new Phase.Weighted(Material.DIRT, 1));
        assertThat(meadow.mobs()).containsExactly(new Phase.Mob(EntityType.COW, 2.5));
        Phase caves = phases.get(1);
        assertThat(caves.from()).isEqualTo(500);
        assertThat(caves.icon()).as("no icon: its first block").isEqualTo(Material.STONE);
        assertThat(caves.opens()).containsExactly(IslandDimension.NETHER);
    }

    @Test
    @DisplayName("a mistake is named and skipped; the rest of the file still counts")
    void mistakesAreNamed() throws Exception {
        ContentReader.Result result = read(yaml("""
                phases:
                  overworld:
                    meadow:
                      from: 0
                      blocks: {dirt: 5, not_a_block: 3, stone: -1, sand: 2, torch: 1}
                      mobs: {cow: 2, dragon_of_doom: 1, pig: 0}
                      opens: [overworld, mars]
                    empty:
                      from: 50
                      blocks: {}
                    negative:
                      from: -5
                      blocks: {stone: 1}
                  narnia:
                    lamp:
                      blocks: {dirt: 1}
                """));

        Phase meadow = result.content().phasesOf(IslandDimension.OVERWORLD).getFirst();
        assertThat(meadow.blocks()).extracting(Phase.Weighted::material).containsExactly(Material.DIRT);
        assertThat(meadow.mobs()).extracting(Phase.Mob::type).containsExactly(EntityType.COW);
        assertThat(meadow.opens()).isEmpty();
        assertThat(result.content().phasesOf(IslandDimension.OVERWORLD)).extracting(Phase::id)
                .containsExactly("meadow");
        assertThat(result.problems()).hasSize(11)
                .anySatisfy(problem -> assertThat(problem).contains("not_a_block"))
                .anySatisfy(problem -> assertThat(problem).contains("sand").contains("falls"))
                .anySatisfy(problem -> assertThat(problem).contains("torch"))
                .anySatisfy(problem -> assertThat(problem).contains("narnia"))
                .anySatisfy(problem -> assertThat(problem).contains("dragon_of_doom"))
                .anySatisfy(problem -> assertThat(problem).contains("mars"))
                .anySatisfy(problem -> assertThat(problem).contains("empty"))
                .anySatisfy(problem -> assertThat(problem).contains("negative"));
    }

    @Test
    @DisplayName("phases that do not start at zero: the first one starts at zero anyway, and the owner is told")
    void firstStartsAtZero() throws Exception {
        ContentReader.Result result = read(yaml("""
                phases:
                  nether:
                    late:
                      from: 200
                      blocks: {netherrack: 1}
                """));

        assertThat(result.content().phasesOf(IslandDimension.NETHER).getFirst().from()).isZero();
        assertThat(result.problems()).singleElement().asString().contains("late").contains("0");
    }

    @Test
    @DisplayName("an empty file is a playable default: one phase of the starting block per dimension")
    void emptyFile() throws Exception {
        ContentReader.Result result = read(yaml(""));

        assertThat(result.problems()).isEmpty();
        for (IslandDimension dimension : IslandDimension.values()) {
            assertThat(result.content().phasesOf(dimension)).singleElement()
                    .satisfies(phase -> assertThat(phase.blocks()).extracting(Phase.Weighted::material)
                            .containsExactly(dimension.icon()));
        }
        assertThat(result.content().teamSize()).isEqualTo(6);
    }
}
