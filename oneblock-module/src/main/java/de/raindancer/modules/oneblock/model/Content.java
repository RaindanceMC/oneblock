package de.raindancer.modules.oneblock.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * What the islands are made of: each dimension's phases, in order. Read from {@code content.yml}; the shipped file is
 * the default, the owner's wins. Every dimension has at least one phase and the first starts at zero, whatever the
 * file said.
 *
 * @param teamSize the most players on one island, the owner included
 * @param mobCap   a phase stops spawning a mob once this many of its kind are near the block
 */
public record Content(Map<IslandDimension, List<Phase>> phases, int teamSize, int mobCap) {

    public Content {
        EnumMap<IslandDimension, List<Phase>> sorted = new EnumMap<>(IslandDimension.class);
        for (IslandDimension dimension : IslandDimension.values()) {
            List<Phase> own = new ArrayList<>(phases.getOrDefault(dimension, List.of()));
            own.sort(Comparator.comparingLong(Phase::from));
            if (own.isEmpty()) {
                own.add(Phase.fallback(dimension));
            } else if (own.getFirst().from() != 0) {
                own.set(0, own.getFirst().startingAt(0));
            }
            sorted.put(dimension, List.copyOf(own));
        }
        phases = Map.copyOf(sorted);
        teamSize = Math.max(1, teamSize);
        mobCap = Math.max(1, mobCap);
    }

    /** In order, the first from zero; never empty. */
    public List<Phase> phasesOf(IslandDimension dimension) {
        return phases.get(dimension);
    }
}
