package de.raindancer.modules.oneblock.rules;

import de.raindancer.modules.oneblock.model.Content;
import de.raindancer.modules.oneblock.model.IslandDimension;
import de.raindancer.modules.oneblock.model.Phase;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A small catalogue the rule tests share. Overworld: Meadow from 0 (dirt 3, oak log 1, cows at 10%), Caves from 100
 * (stone, opens the nether), Depths from 300 (diamond ore, opens the end). Nether: Wastes from 0. The end has none of
 * its own, so it gets the fallback.
 */
final class Contents {

    static final Phase MEADOW = new Phase("meadow", "Meadow", 0, Material.GRASS_BLOCK,
            List.of(new Phase.Weighted(Material.DIRT, 3), new Phase.Weighted(Material.OAK_LOG, 1)),
            List.of(new Phase.Mob(EntityType.COW, 10)), Set.of());
    static final Phase CAVES = new Phase("caves", "Caves", 100, Material.STONE,
            List.of(new Phase.Weighted(Material.STONE, 1)), List.of(), Set.of(IslandDimension.NETHER));
    static final Phase DEPTHS = new Phase("depths", "Depths", 300, Material.DIAMOND_ORE,
            List.of(new Phase.Weighted(Material.DIAMOND_ORE, 1)),
            List.of(new Phase.Mob(EntityType.ZOMBIE, 60), new Phase.Mob(EntityType.SKELETON, 60)),
            Set.of(IslandDimension.END));
    static final Phase WASTES = new Phase("wastes", "Wastes", 0, Material.NETHERRACK,
            List.of(new Phase.Weighted(Material.NETHERRACK, 1)), List.of(), Set.of());

    static Content content() {
        return new Content(Map.of(IslandDimension.OVERWORLD, List.of(DEPTHS, MEADOW, CAVES),
                IslandDimension.NETHER, List.of(WASTES)), 4, 8);
    }

    private Contents() {
    }
}
