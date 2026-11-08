package de.raindancer.modules.oneblock.service;

import de.raindancer.modules.oneblock.OneBlockSettings;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Who is waiting for an island's world to be made or loaded, so a second command does not queue a second trip. */
public final class Preparing implements IOneBlockService {

    private final Set<UUID> waiting = ConcurrentHashMap.newKeySet();

    /** @return false when they are waiting already */
    public boolean begin(UUID player) {
        return waiting.add(player);
    }

    public void done(UUID player) {
        waiting.remove(player);
    }

    @Override
    public void settings(OneBlockSettings settings) {
    }
}
