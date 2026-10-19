package de.raindancer.modules.oneblock.service;

import de.raindancer.core.ui.messages.Messages;
import de.raindancer.core.ui.scoreboard.Sidebar;
import de.raindancer.modules.oneblock.OneBlockModule;
import de.raindancer.modules.oneblock.model.EconomyMode;
import de.raindancer.modules.oneblock.model.IslandDimension;
import de.raindancer.modules.oneblock.model.IslandView;
import de.raindancer.modules.oneblock.model.Progress;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** What the sidebar says, read as a player reads it. */
class SidebarServiceTest {

    @TempDir
    Path dir;
    private SidebarService sidebar;

    @BeforeEach
    void setUp() {
        Messages messages = new Messages(dir.resolve("messages.yml"));
        messages.defineFrom(OneBlockModule.class.getResourceAsStream("messages.yml"));
        sidebar = new SidebarService(messages, null);
    }

    private static List<String> plain(Sidebar drawn) {
        return drawn.lines().stream().map(line -> PlainTextComponentSerializer.plainText().serialize(line).strip()).toList();
    }

    private static IslandView mine(boolean standingOn) {
        return new IslandView(standingOn, true, "Raindancer118", 1000, 2, 6, 1, EconomyMode.SMP,
                standingOn ? Optional.of(IslandDimension.NETHER) : Optional.empty(),
                new Progress("Crimson Forest", 2, 5, 5678, 40, "Warped Forest", 1234));
    }

    @Test
    @DisplayName("on your own island: whose it is, your rank, its money, where you are, the phase, the block, the team")
    void own() {
        List<String> lines = plain(sidebar.draw(Optional.of(mine(true)), room -> List.of()));

        assertThat(lines).contains("Your island", "You: Owner", "◆ SMP economy", "In the nether",
                "Crimson Forest 2/5", "Next: Warped Forest in 1,234", "5,678 blocks broken", "Team 2/6 · 1 online");
        assertThat(lines).anyMatch(line -> line.endsWith("40%"));
        assertThat(lines.size()).isLessThanOrEqualTo(Sidebar.MAX_LINES);
    }

    @Test
    @DisplayName("on somebody else's island: theirs, you as a visitor, and which money it plays with")
    void visiting() {
        IslandView view = new IslandView(true, false, "Ada", 0, 1, 6, 1, EconomyMode.PURE,
                Optional.of(IslandDimension.OVERWORLD), new Progress("Meadow", 1, 11, 3, 0, "Quarry", 497));
        assertThat(plain(sidebar.draw(Optional.of(view), room -> List.of())))
                .contains("Ada's island", "You: Visitor", "✦ Pure");
    }

    @Test
    @DisplayName("away from any island but owning one: says so, without a dimension line")
    void away() {
        List<String> lines = plain(sidebar.draw(Optional.of(mine(false)), room -> List.of()));
        assertThat(lines).contains("Your island (not here)").noneMatch(line -> line.startsWith("In "));
    }

    @Test
    @DisplayName("no island at all: how to get one, and nothing pretending to be numbers")
    void none() {
        List<String> lines = plain(sidebar.draw(Optional.empty(), room -> List.of()));
        assertThat(lines).contains("No island yet", "/island to start one").noneMatch(line -> line.contains("Phase") || line.contains("blocks broken"));
    }

    @Test
    @DisplayName("in the last phase: says so instead of a next one")
    void lastPhase() {
        IslandView view = new IslandView(true, true, "Ada", 1000, 1, 6, 1, EconomyMode.PURE,
                Optional.of(IslandDimension.OVERWORLD), new Progress("Plenty", 11, 11, 20_000, 100, "", 0));
        assertThat(plain(sidebar.draw(Optional.of(view), room -> List.of())))
                .contains("Plenty 11/11", "The last phase. It only gets better.").noneMatch(line -> line.startsWith("Next"));
    }

    @Test
    @DisplayName("other plugins' lines go last, and never past the client's fifteen")
    void sections() {
        List<Component> extra = List.of(Component.empty(), Component.text("Guild"), Component.text(" Rain"));
        Sidebar drawn = sidebar.draw(Optional.of(mine(true)), room -> extra);
        assertThat(drawn.lines().size()).isLessThanOrEqualTo(Sidebar.MAX_LINES);
        assertThat(plain(drawn)).first().isEqualTo("Island");
    }
}
