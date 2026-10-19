package de.raindancer.modules.oneblock.model;

/**
 * Where a block is in its phases, as the sidebar shows it.
 *
 * @param number  the phase's place, from 1
 * @param of      how many phases the dimension has
 * @param blocks  the block's count
 * @param percent how far through this phase, 0–100
 * @param next    the next phase's name; empty in the last
 * @param toNext  blocks left until it
 */
public record Progress(String phase, int number, int of, long blocks, int percent, String next, long toNext) {
}
