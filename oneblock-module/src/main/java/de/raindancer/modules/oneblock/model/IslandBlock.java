package de.raindancer.modules.oneblock.model;

/** One dimension's one block: where it is, and how often it has been broken — which says its phase. */
public record IslandBlock(BlockSpot spot, long broken) {

    public IslandBlock {
        broken = Math.max(0, broken);
    }

    public static IslandBlock fresh(BlockSpot spot) {
        return new IslandBlock(spot, 0);
    }

    public IslandBlock brokenOnceMore() {
        return new IslandBlock(spot, broken + 1);
    }

    public IslandBlock withBroken(long count) {
        return new IslandBlock(spot, count);
    }
}
