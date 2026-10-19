package de.raindancer.modules.oneblock.rules;

import de.raindancer.modules.oneblock.model.Content;
import de.raindancer.modules.oneblock.model.Island;
import de.raindancer.modules.oneblock.model.IslandBlock;
import de.raindancer.modules.oneblock.model.IslandDimension;
import de.raindancer.modules.oneblock.model.Phase;
import de.raindancer.modules.oneblock.model.Progress;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Where each block is in its dimension's phases, and what reaching one opens. A dimension's phases follow that
 * dimension's own count. A dimension is opened by any reached phase that names it — checked against everything
 * reached, not just the phase a break entered, so an island from before phases (or after an owner moved a phase
 * earlier) catches up on its next break. Nothing is ever closed again.
 */
public final class PhaseRule implements IOneBlockRule {

    /** @param index the phase's place, from 0 */
    public record Standing(Phase phase, int index, int of, Optional<Phase> next, long broken) {

        public int number() {
            return index + 1;
        }

        public long toNext() {
            return next.map(upcoming -> Math.max(0, upcoming.from() - broken)).orElse(0L);
        }

        public int percent() {
            if (next.isEmpty()) {
                return 100;
            }
            long span = next.get().from() - phase.from();
            return span <= 0 ? 100 : (int) Math.clamp((broken - phase.from()) * 100 / span, 0, 100);
        }

        public Progress progress() {
            return new Progress(phase.name(), number(), of, broken, percent(), next.map(Phase::name).orElse(""),
                    toNext());
        }
    }

    /** One break, counted: the island after it, the phase it entered (if it did) and what it opened. */
    public record Broken(Island island, Optional<Phase> entered, Set<IslandDimension> opened) {
    }

    /** What opens a dimension: a phase of {@code in}, and how many of its breaks are still to go. */
    public record Opener(IslandDimension in, Phase phase, long toGo) {
    }

    public Phase at(Content content, IslandDimension dimension, long broken) {
        List<Phase> phases = content.phasesOf(dimension);
        Phase current = phases.getFirst();
        for (Phase phase : phases) {
            if (phase.from() <= broken) {
                current = phase;
            }
        }
        return current;
    }

    public Standing standing(Content content, IslandDimension dimension, long broken) {
        List<Phase> phases = content.phasesOf(dimension);
        int index = 0;
        for (int i = 0; i < phases.size(); i++) {
            if (phases.get(i).from() <= broken) {
                index = i;
            }
        }
        Optional<Phase> next = index + 1 < phases.size() ? Optional.of(phases.get(index + 1)) : Optional.empty();
        return new Standing(phases.get(index), index, phases.size(), next, broken);
    }

    public Broken broken(Content content, Island island, IslandDimension dimension) {
        long before = island.block(dimension).map(IslandBlock::broken).orElse(0L);
        Island counted = island.broken(dimension);
        Phase was = at(content, dimension, before);
        Phase now = at(content, dimension, before + 1);
        Set<IslandDimension> opened = due(content, counted);
        return new Broken(open(counted, opened), now.equals(was) ? Optional.empty() : Optional.of(now), opened);
    }

    /** The dimensions the island's phases have reached but that are not open yet. */
    public Set<IslandDimension> due(Content content, Island island) {
        Set<IslandDimension> due = EnumSet.noneOf(IslandDimension.class);
        for (Map.Entry<IslandDimension, IslandBlock> block : island.blocks().entrySet()) {
            for (Phase phase : content.phasesOf(block.getKey())) {
                if (phase.from() <= block.getValue().broken()) {
                    due.addAll(phase.opens());
                }
            }
        }
        due.removeIf(island::unlocked);
        due.remove(IslandDimension.OVERWORLD);
        return due;
    }

    /** The island with everything due opened; empty when nothing is. */
    public Optional<Island> opening(Content content, Island island) {
        Set<IslandDimension> due = due(content, island);
        return due.isEmpty() ? Optional.empty() : Optional.of(open(island, due));
    }

    /** The earliest phase of an open dimension that would open {@code wanted}; empty once it is open, or when none does. */
    public Optional<Opener> opener(Content content, Island island, IslandDimension wanted) {
        if (island.unlocked(wanted)) {
            return Optional.empty();
        }
        Optional<Opener> best = Optional.empty();
        for (Map.Entry<IslandDimension, IslandBlock> block : island.blocks().entrySet()) {
            for (Phase phase : content.phasesOf(block.getKey())) {
                if (phase.opens().contains(wanted)) {
                    long toGo = Math.max(0, phase.from() - block.getValue().broken());
                    if (best.isEmpty() || toGo < best.get().toGo()) {
                        best = Optional.of(new Opener(block.getKey(), phase, toGo));
                    }
                    break;
                }
            }
        }
        return best;
    }

    private static Island open(Island island, Set<IslandDimension> dimensions) {
        Island opened = island;
        for (IslandDimension dimension : dimensions) {
            opened = opened.unlocking(dimension).orElse(opened);
        }
        return opened;
    }

    @Override
    public String describe() {
        return "which phase each block is in, and which dimensions that opens";
    }
}
