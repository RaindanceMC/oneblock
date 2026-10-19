package de.raindancer.modules.oneblock.service;

import de.raindancer.modules.oneblock.OneBlockSettings;
import de.raindancer.modules.oneblock.model.Content;
import de.raindancer.modules.oneblock.model.EconomyMode;
import de.raindancer.modules.oneblock.model.Island;
import de.raindancer.modules.oneblock.model.IslandDimension;
import de.raindancer.modules.oneblock.model.Reply;
import de.raindancer.modules.oneblock.model.Spawn;
import de.raindancer.modules.oneblock.rules.PhaseRule;
import de.raindancer.modules.oneblock.store.IslandBook;

import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * What players do with islands: making one, choosing its money, the team, the spawn. Decides nothing itself — the
 * book keeps one island per player and the island keeps its own invariants.
 * Every answer is a {@link Reply}, said by whoever asked. No Bukkit here, so all of it is tested.
 */
public final class IslandService implements IOneBlockService {

    private final IslandBook book;
    private final Supplier<Content> content;
    private final LongSupplier clock;
    private final PhaseRule phases = new PhaseRule();

    public IslandService(IslandBook book, Supplier<Content> content, LongSupplier clock) {
        this.book = book;
        this.content = content;
        this.clock = clock;
    }

    /** Nothing in the settings changes what players may do with their islands. */
    @Override
    public void settings(OneBlockSettings settings) {
    }

    public IslandBook book() {
        return book;
    }

    // ------------------------------------------------------------------ making one

    /** A new island for {@code owner}, with the money they chose — once, for good. */
    public Reply create(UUID owner, String name, EconomyMode mode) {
        if (mode == null || !mode.decided()) {
            return Reply.refused("oneblock.create.choose");
        }
        if (book.ofPlayer(owner).isPresent()) {
            return Reply.refused("oneblock.create.already");
        }
        Island island = Island.create(UUID.randomUUID(), owner, name, mode, clock.getAsLong());
        IslandBook.Changed added = book.add(island);
        return added.done() ? Reply.ok("oneblock.create.done", "mode-key", mode.nameKey())
                : Reply.refused("oneblock.create.already");
    }

    /** The owner of an island that came over from BentoBox picks its money, once. */
    public Reply decide(UUID owner, EconomyMode mode) {
        Optional<Island> island = book.ofPlayer(owner);
        if (island.isEmpty()) {
            return Reply.refused("oneblock.refuse.no-island");
        }
        if (!island.get().owner().equals(owner)) {
            return Reply.refused("oneblock.mode.owner-only");
        }
        IslandBook.Changed changed = book.change(island.get().id(), it -> it.decide(mode));
        return changed.done() ? Reply.ok("oneblock.mode.chosen", "mode-key", mode.nameKey())
                : Reply.refused("oneblock.mode.already");
    }

    // ------------------------------------------------------------------ the team

    /** Somebody who accepted a team invite joins the inviter's island. */
    public Reply join(UUID player, UUID islandId) {
        Optional<Island> island = book.get(islandId);
        if (island.isEmpty()) {
            return Reply.refused("oneblock.refuse.no-island");
        }
        if (island.get().isMember(player)) {
            return Reply.silent();
        }
        Optional<Island> theirs = book.ofPlayer(player);
        if (theirs.isPresent()) {
            return Reply.refused(theirs.get().owner().equals(player) ? "oneblock.join.own-island"
                    : "oneblock.join.other-island");
        }
        int most = content.get().teamSize();
        IslandBook.Changed changed = book.change(islandId, it -> it.everyone().size() >= most ? Optional.empty()
                : Optional.of(it.withMember(player)));
        return switch (changed.outcome()) {
            case DONE -> Reply.ok("oneblock.join.done", "player", island.get().ownerName());
            case ON_ANOTHER_ISLAND -> Reply.refused("oneblock.join.other-island");
            default -> Reply.refused("oneblock.join.full", "most", String.valueOf(most));
        };
    }

    /**
     * A member leaves; an owner alone on their island gives it up — archived, worlds kept, nothing deleted. An owner
     * with a team cannot leave them without an owner.
     */
    public Reply leave(UUID player) {
        Optional<Island> island = book.ofPlayer(player);
        if (island.isEmpty()) {
            return Reply.refused("oneblock.refuse.no-island");
        }
        if (!island.get().owner().equals(player)) {
            book.change(island.get().id(), it -> Optional.of(it.withoutMember(player)));
            return Reply.ok("oneblock.leave.done", "player", island.get().ownerName());
        }
        if (!island.get().members().isEmpty()) {
            return Reply.refused("oneblock.leave.has-team");
        }
        book.archive(island.get().id());
        return Reply.ok("oneblock.leave.abandoned");
    }

    public Reply kick(UUID owner, UUID member, String memberName) {
        Optional<Island> island = book.ofPlayer(owner);
        if (island.isEmpty()) {
            return Reply.refused("oneblock.refuse.no-island");
        }
        if (!island.get().owner().equals(owner)) {
            return Reply.refused("oneblock.kick.owner-only");
        }
        if (owner.equals(member)) {
            return Reply.refused("oneblock.kick.self");
        }
        if (!island.get().members().contains(member)) {
            return Reply.refused("oneblock.kick.not-member", "player", memberName);
        }
        book.change(island.get().id(), it -> Optional.of(it.withoutMember(member)));
        return Reply.ok("oneblock.kick.done", "player", memberName);
    }

    // ------------------------------------------------------------------ phases

    /**
     * Opens what every island's phases have reached but it does not have yet — islands from before phases, and after
     * an owner moved an opening phase earlier. Breaking would do it too, on the island's next break.
     *
     * @return how many islands got something opened
     */
    public int openDue() {
        Content now = content.get();
        int opened = 0;
        for (Island island : book.all()) {
            if (phases.due(now, island).isEmpty()) {
                continue;
            }
            if (book.change(island.id(), it -> phases.opening(now, it)).done()) {
                opened++;
            }
        }
        return opened;
    }

    // ------------------------------------------------------------------ arriving

    /** Where the team arrives in this dimension, set to where the player stands. Members only, on their own island. */
    public Reply setSpawn(UUID player, String world, Spawn spawn) {
        Optional<Island> island = book.ofPlayer(player);
        if (island.isEmpty()) {
            return Reply.refused("oneblock.refuse.no-island");
        }
        Optional<Island> here = book.ofWorld(world);
        Optional<IslandDimension> dimension = Island.dimensionOf(world);
        if (here.isEmpty() || dimension.isEmpty() || !here.get().id().equals(island.get().id())) {
            return Reply.refused("oneblock.spawn.not-here");
        }
        book.change(island.get().id(), it -> Optional.of(it.withSpawn(dimension.get(), spawn)));
        return Reply.ok("oneblock.spawn.set", "dimension-key", dimension.get().nameKey());
    }
}
