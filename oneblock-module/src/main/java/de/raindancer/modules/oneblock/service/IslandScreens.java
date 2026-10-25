package de.raindancer.modules.oneblock.service;

import de.raindancer.core.ui.hologram.ScreenKind;
import de.raindancer.core.ui.hologram.Screens;
import de.raindancer.modules.oneblock.model.Content;
import de.raindancer.modules.oneblock.model.Island;
import de.raindancer.modules.oneblock.model.IslandDimension;
import de.raindancer.modules.oneblock.rules.PhaseRule;
import de.raindancer.modules.oneblock.rules.ScreenRule;
import de.raindancer.modules.oneblock.store.IslandBook;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/** The island server's screens for Core's {@code /screen}: everybody's own island, and the top islands. */
public final class IslandScreens {

    public static final List<String> KINDS = List.of("oneblock-you", "oneblock-top");
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final int TOP_SHOWN = 8;

    private final IslandBook book;
    private final Supplier<Content> content;
    private final ScreenRule rule = new ScreenRule();
    private final PhaseRule phases = new PhaseRule();

    public IslandScreens(IslandBook book, Supplier<Content> content) {
        this.book = book;
        this.content = content;
    }

    public void register(Screens screens, Plugin owner) {
        screens.register(owner, ScreenKind.personal("oneblock-you", "Your island",
                "Everybody's own island: its phase, the blocks broken and the team.", Material.GRASS_BLOCK, this::you));
        screens.register(owner, ScreenKind.everyone("oneblock-top", "Top islands",
                "The islands with the most blocks broken.", Material.DIAMOND_ORE, this::top));
    }

    private Component you(ScreenKind.Viewer viewer) {
        String head = "<gold><bold>Your island</bold></gold>";
        Optional<Island> held = book.ofPlayer(viewer.id());
        if (held.isEmpty()) {
            return MINI.deserialize(head + "\n<gray>None yet — <white>/island");
        }
        Island island = held.get();
        Content phasesOf = content.get();
        StringBuilder text = new StringBuilder(head);
        text.append("\n<gray>Owner <white>").append(MINI.escapeTags(island.ownerName()));
        text.append("\n<gray>Team <white>").append(rule.team(island.everyone().size()));
        island.block(IslandDimension.OVERWORLD).ifPresent(block -> {
            var standing = phases.standing(phasesOf, IslandDimension.OVERWORLD, block.broken());
            text.append("\n<gray>Phase <white>").append(MINI.escapeTags(standing.phase().name()))
                    .append(" <dark_gray>(").append(standing.number()).append("/").append(standing.of()).append(")");
            text.append(standing.next().isEmpty() ? "\n<gray>Last phase reached"
                    : "\n<gray>Next <white>" + MINI.escapeTags(standing.next().get().name()) + " <dark_gray>in "
                    + rule.count(standing.toNext()) + " blocks");
        });
        text.append("\n<gray>Blocks broken <white>").append(rule.count(island.totalBroken()));
        return MINI.deserialize(text.toString());
    }

    private Component top() {
        StringBuilder text = new StringBuilder("<gold><bold>Top islands</bold></gold>");
        List<Island> best = rule.top(book.all(), TOP_SHOWN);
        if (best.isEmpty()) {
            return MINI.deserialize(text + "\n<gray>No block broken yet");
        }
        for (int i = 0; i < best.size(); i++) {
            Island island = best.get(i);
            text.append("\n").append(rule.place(i + 1)).append(i + 1).append(". ")
                    .append(MINI.escapeTags(island.ownerName())).append(" <dark_gray>· <gray>")
                    .append(rule.count(island.totalBroken()));
        }
        return MINI.deserialize(text.toString());
    }
}
