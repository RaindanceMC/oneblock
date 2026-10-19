package de.raindancer.modules.oneblock.service;

import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.core.ui.actionbar.ActionBarPriority;
import de.raindancer.core.ui.actionbar.ActionBars;
import de.raindancer.core.ui.effect.Cues;
import de.raindancer.core.ui.effect.Effects;
import de.raindancer.core.ui.messages.Messages;
import de.raindancer.core.ui.text.Markup;
import de.raindancer.modules.oneblock.OneBlockSettings;
import de.raindancer.modules.oneblock.model.Content;
import de.raindancer.modules.oneblock.model.Island;
import de.raindancer.modules.oneblock.model.IslandBlock;
import de.raindancer.modules.oneblock.model.IslandDimension;
import de.raindancer.modules.oneblock.model.Phase;
import de.raindancer.modules.oneblock.rules.GeneratorRule;
import de.raindancer.modules.oneblock.rules.PhaseRule;
import de.raindancer.modules.oneblock.store.IslandBook;
import de.raindancer.modules.oneblock.util.ProgressBar;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/**
 * The one block itself: a break counts, moves the block through its dimension's phases — opening a dimension when a
 * phase says so — and the block comes back a tick later as one of the phase's blocks, sometimes with one of its mobs
 * on top. Server thread.
 */
public final class OneBlockService implements IOneBlockService {

    /** Where a break happened: the island, the dimension, the block. */
    public record Target(Island island, IslandDimension dimension, IslandBlock block) {
    }

    private static final String BAR_OWNER = "oneblock";
    private static final Duration BAR_FOR = Duration.ofSeconds(3);
    private static final int BAR_CELLS = 10;
    private static final double MOB_RADIUS = 16;

    private final Plugin plugin;
    private final Server server;
    private final IslandBook book;
    private final Supplier<Content> content;
    private final Messages messages;
    private final ActionBars bars;
    private final Effects effects;
    private final GeneratorRule generator = new GeneratorRule();
    private final PhaseRule phases = new PhaseRule();

    public OneBlockService(Plugin plugin, Server server, IslandBook book, Supplier<Content> content, Messages messages,
                           ActionBars bars, Effects effects) {
        this.plugin = plugin;
        this.server = server;
        this.book = book;
        this.content = content;
        this.messages = messages;
        this.bars = bars;
        this.effects = effects;
    }

    /** Nothing about the block is in the settings: it is all {@code content.yml}. */
    @Override
    public void settings(OneBlockSettings settings) {
    }

    /** The island, dimension and block this block is the one block of; empty for every other block. */
    public Optional<Target> targetOf(Block block) {
        String world = block.getWorld().getName();
        Optional<Island> island = book.ofWorld(world);
        Optional<IslandDimension> dimension = Island.dimensionOf(world);
        if (island.isEmpty() || dimension.isEmpty()) {
            return Optional.empty();
        }
        return island.get().block(dimension.get())
                .filter(one -> one.spot().is(block.getX(), block.getY(), block.getZ()))
                .map(one -> new Target(island.get(), dimension.get(), one));
    }

    public boolean isOneBlock(Block block) {
        return targetOf(block).isPresent();
    }

    /** {@code breaker} broke the block: count it, move the phases on, bring the block back next tick. */
    public void broken(Target target, Player breaker, Location at) {
        Content now = content.get();
        PhaseRule.Broken[] result = new PhaseRule.Broken[1];
        IslandBook.Changed changed = book.change(target.island().id(), island -> {
            result[0] = phases.broken(now, island, target.dimension());
            return Optional.of(result[0].island());
        });
        long count = changed.island().flatMap(island -> island.block(target.dimension())).map(IslandBlock::broken)
                .orElse(target.block().broken() + 1);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        GeneratorRule.Outcome outcome = generator.next(now, target.dimension(), count, random.nextDouble(100.0),
                random.nextDouble());
        Location block = at.clone();
        Scheduling.regionLater(plugin, block, 1L, () -> regrow(block, outcome, now.mobCap()));

        PhaseRule.Standing standing = phases.standing(now, target.dimension(), count);
        if (breaker != null) {
            bars.show(breaker.getUniqueId(), BAR_OWNER, bar(standing), BAR_FOR, ActionBarPriority.NORMAL);
        }
        if (result[0] != null && changed.island().isPresent()) {
            Island island = changed.island().get();
            result[0].entered().ifPresent(phase -> entered(island, target.dimension(), phase, standing, at.getWorld()));
            result[0].opened().forEach(dimension -> opened(island, dimension));
        }
    }

    private net.kyori.adventure.text.Component bar(PhaseRule.Standing standing) {
        Markup cells = Markup.of(ProgressBar.mini(standing.percent(), BAR_CELLS));
        return standing.next().isEmpty()
                ? messages.get("oneblock.phase.bar-last", "phase", standing.phase().name(), "bar", cells)
                : messages.get("oneblock.phase.bar", "phase", standing.phase().name(), "bar", cells,
                "blocks", String.format("%,d", standing.toNext()), "next", standing.next().get().name());
    }

    /** Everybody in the world sees it come; the team elsewhere on this server reads about it. */
    private void entered(Island island, IslandDimension dimension, Phase phase, PhaseRule.Standing standing,
                         World world) {
        Title title = Title.title(messages.get("oneblock.phase.title", "phase", phase.name()),
                messages.get("oneblock.phase.subtitle", "number", String.valueOf(standing.number()),
                        "of", String.valueOf(standing.of())),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofSeconds(1)));
        Set<Player> told = new LinkedHashSet<>(world.getPlayers());
        for (Player there : told) {
            there.showTitle(title);
            effects.play(there.getUniqueId(), Cues.EARNED);
        }
        for (Player member : online(island)) {
            told.add(member);
        }
        for (Player player : told) {
            messages.send(player, "oneblock.phase.entered", "phase", phase.name(),
                    "dimension", messages.get(dimension.nameKey()),
                    "number", String.valueOf(standing.number()), "of", String.valueOf(standing.of()));
        }
    }

    private void opened(Island island, IslandDimension dimension) {
        for (Player member : online(island)) {
            messages.send(member, "oneblock.phase.opened", "dimension", messages.get(dimension.nameKey()),
                    "id", dimension.id());
        }
    }

    private Set<Player> online(Island island) {
        Set<Player> online = new LinkedHashSet<>();
        for (UUID member : island.everyone()) {
            Player player = server.getPlayer(member);
            if (player != null) {
                online.add(player);
            }
        }
        return online;
    }

    private void regrow(Location at, GeneratorRule.Outcome outcome, int mobCap) {
        Block block = at.getBlock();
        // No physics: a neighbour update would otherwise pop a block that needs support.
        block.setType(outcome.material(), false);
        if (outcome.mob() == null) {
            return;
        }
        World world = at.getWorld();
        long near = world.getNearbyEntities(at, MOB_RADIUS, MOB_RADIUS, MOB_RADIUS,
                entity -> entity.getType() == outcome.mob()).size();
        // A passive mob is never cleaned up by anything, so an island would fill with cows until the server crawls;
        // past the cap the roll is simply spent.
        if (near < mobCap) {
            world.spawnEntity(at.clone().add(0.5, 1.0, 0.5), outcome.mob());
        }
    }
}
