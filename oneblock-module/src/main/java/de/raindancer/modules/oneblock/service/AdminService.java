package de.raindancer.modules.oneblock.service;

import de.raindancer.modules.oneblock.OneBlockSettings;
import de.raindancer.modules.oneblock.model.Content;
import de.raindancer.modules.oneblock.model.Hold;
import de.raindancer.modules.oneblock.model.Island;
import de.raindancer.modules.oneblock.model.IslandBlock;
import de.raindancer.modules.oneblock.model.IslandDimension;
import de.raindancer.modules.oneblock.model.Phase;
import de.raindancer.modules.oneblock.model.Reply;
import de.raindancer.modules.oneblock.rules.PhaseRule;
import de.raindancer.modules.oneblock.store.IslandBook;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Staff's say over islands: up or down for a while or for good, auto-unload on or off, a block moved through its phases. Every
 * change takes effect at once ({@code act} runs the lifecycle for that island) and survives a restart.
 */
public final class AdminService implements IOneBlockService {

    private final IslandBook book;
    private final LongSupplier clock;
    private final Supplier<Content> content;
    private final Consumer<Island> act;
    private final PhaseRule phases = new PhaseRule();

    public AdminService(IslandBook book, LongSupplier clock, Supplier<Content> content, Consumer<Island> act) {
        this.book = book;
        this.clock = clock;
        this.content = content;
        this.act = act;
    }

    /** Nothing configured: admins decide each island by hand. */
    @Override
    public void settings(OneBlockSettings settings) {
    }

    /** @param length empty for good */
    public Reply hold(UUID island, Hold.Kind kind, Optional<Duration> length, String by) {
        long until = length.map(duration -> saturatedAdd(clock.getAsLong(), duration.toMillis())).orElse(Hold.FOREVER);
        IslandBook.Changed changed = book.change(island, it -> Optional.of(it.withHold(
                Optional.of(new Hold(kind, until, by)))));
        if (!changed.done()) {
            return Reply.refused("oneblock.admin.no-such-island");
        }
        changed.island().ifPresent(act);
        String name = changed.island().map(Island::ownerName).orElse("?");
        String key = switch (kind) {
            case UP -> length.isEmpty() ? "oneblock.admin.held-up-forever" : "oneblock.admin.held-up";
            case DOWN -> length.isEmpty() ? "oneblock.admin.held-down-forever" : "oneblock.admin.held-down";
        };
        return Reply.ok(key, "player", name, "time", length.map(AdminService::describe).orElse(""));
    }

    public Reply release(UUID island) {
        IslandBook.Changed changed = book.change(island, it -> it.hold().isEmpty() ? Optional.empty()
                : Optional.of(it.withHold(Optional.empty())));
        if (changed.outcome() == IslandBook.Outcome.NOT_FOUND) {
            return Reply.refused("oneblock.admin.no-such-island");
        }
        if (!changed.done()) {
            return Reply.refused("oneblock.admin.not-held", "player", changed.island().map(Island::ownerName).orElse("?"));
        }
        changed.island().ifPresent(act);
        return Reply.ok("oneblock.admin.released", "player", changed.island().map(Island::ownerName).orElse("?"));
    }

    public Reply autoUnload(UUID island, boolean on) {
        IslandBook.Changed changed = book.change(island, it -> Optional.of(it.withAutoUnload(on)));
        if (!changed.done()) {
            return Reply.refused("oneblock.admin.no-such-island");
        }
        changed.island().ifPresent(act);
        return Reply.ok(on ? "oneblock.admin.auto-unload-on" : "oneblock.admin.auto-unload-off",
                "player", changed.island().map(Island::ownerName).orElse("?"));
    }

    /**
     * Moves one block's count by {@code amount}, never below zero. A dimension its phases open opens on the team's next
     * break, where they are told — as for any island that is behind. Lowering it closes nothing.
     */
    public Reply blocks(UUID island, IslandDimension dimension, long amount) {
        Content now = content.get();
        IslandBook.Changed changed = book.change(island, it -> it.unlocked(dimension)
                ? Optional.of(it.withBlock(dimension, block -> block.withBroken(saturatedAdd(block.broken(), amount))))
                : Optional.empty());
        if (!changed.done()) {
            return book.get(island).isPresent()
                    ? Reply.refused("oneblock.admin.locked", "dimension-key", dimension.nameKey())
                    : Reply.refused("oneblock.admin.no-such-island");
        }
        long count = changed.island().flatMap(it -> it.block(dimension)).map(IslandBlock::broken).orElse(0L);
        return Reply.ok("oneblock.admin.blocks", "player", changed.island().map(Island::ownerName).orElse("?"),
                "dimension-key", dimension.nameKey(), "blocks", String.format("%,d", count),
                "phase", phases.at(now, dimension, count).name());
    }

    /**
     * Puts the island's block of this dimension at the start of a phase (by number, id or name) — back or forward.
     * Whatever the phases up to there open is opened at once; nothing already open is closed.
     */
    public Reply phase(UUID island, IslandDimension dimension, String typed) {
        Content now = content.get();
        Optional<Phase> phase = phases.named(now, dimension, typed);
        if (phase.isEmpty()) {
            List<Phase> all = now.phasesOf(dimension);
            StringBuilder names = new StringBuilder();
            for (int i = 0; i < all.size(); i++) {
                names.append(i == 0 ? "" : ", ").append(i + 1).append(' ').append(all.get(i).name());
            }
            return Reply.refused("oneblock.admin.no-such-phase", "typed", String.valueOf(typed),
                    "dimension-key", dimension.nameKey(), "phases", names.toString());
        }
        IslandBook.Changed changed = book.change(island, it -> {
            if (!it.unlocked(dimension)) {
                return Optional.empty();
            }
            Island moved = it.withBlock(dimension, block -> block.withBroken(phase.get().from()));
            return Optional.of(phases.opening(now, moved).orElse(moved));
        });
        if (!changed.done()) {
            return book.get(island).isPresent()
                    ? Reply.refused("oneblock.admin.locked", "dimension-key", dimension.nameKey())
                    : Reply.refused("oneblock.admin.no-such-island");
        }
        return Reply.ok("oneblock.admin.phase", "player", changed.island().map(Island::ownerName).orElse("?"),
                "dimension-key", dimension.nameKey(), "phase", phase.get().name(),
                "blocks", String.format("%,d", phase.get().from()));
    }

    /**
     * The island an admin means: by a member's name or uuid, or by the start of its id (as the admin pages and world
     * names show it).
     */
    public Optional<Island> find(String typed, java.util.function.Function<String, Optional<UUID>> playerByName) {
        if (typed == null || typed.isBlank()) {
            return Optional.empty();
        }
        String wanted = typed.strip().toLowerCase(Locale.ROOT);
        try {
            UUID asId = UUID.fromString(wanted);
            Optional<Island> byId = book.get(asId);
            return byId.isPresent() ? byId : book.ofPlayer(asId);
        } catch (IllegalArgumentException notAUuid) {
            // A name or an id's start.
        }
        Optional<Island> byPlayer = playerByName.apply(typed.strip()).flatMap(book::ofPlayer);
        if (byPlayer.isPresent()) {
            return byPlayer;
        }
        String compact = wanted.replace("-", "").replace("island_", "");
        if (compact.length() < 6) {
            return Optional.empty();
        }
        return book.all().stream().filter(island -> island.idPart().startsWith(compact)
                || island.id().toString().replace("-", "").startsWith(compact)).findFirst();
    }

    private static long saturatedAdd(long a, long b) {
        long sum = a + b;
        return ((a ^ sum) & (b ^ sum)) < 0 ? Hold.FOREVER : Math.min(sum, Hold.FOREVER - 1);
    }

    static String describe(Duration duration) {
        return de.raindancer.core.platform.util.Times.describe(duration);
    }
}
