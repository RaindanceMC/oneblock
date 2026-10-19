package de.raindancer.modules.oneblock.screen;

import de.raindancer.core.ui.menu.Icons;
import de.raindancer.core.ui.menu.Menu;
import de.raindancer.core.ui.menu.PaginatedMenu;
import de.raindancer.modules.oneblock.OneBlockServices;
import de.raindancer.modules.oneblock.model.Island;
import de.raindancer.modules.oneblock.model.IslandDimension;
import de.raindancer.modules.oneblock.model.Phase;
import de.raindancer.modules.oneblock.rules.PhaseRule;
import de.raindancer.modules.oneblock.service.IslandServer;
import de.raindancer.modules.oneblock.util.ProgressBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * One dimension's phases, in order: those behind the island, the one it is in with how far along, and those ahead
 * with what they bring and how far off they are. Nothing to click but the other dimensions in the toolbar.
 */
public final class PhasesMenu extends PaginatedMenu<Phase> implements IOneBlockScreen {

    private static final int[] DIMENSION_COLUMNS = {3, 4, 5};
    private static final int BAR = 10;
    private static final int SHOWN_BLOCKS = 6;

    private final OneBlockServices services;
    private final IslandServer islands;
    private final IslandDimension dimension;
    private final PhaseRule phases = new PhaseRule();

    public PhasesMenu(OneBlockServices services, IslandServer islands, Player viewer, Menu parent,
                      IslandDimension dimension) {
        super(viewer, islands.brand(), parent);
        this.services = services;
        this.islands = islands;
        this.dimension = dimension;
    }

    @Override
    protected Component title() {
        // Just "Phases": with the brand in front, a dimension's name runs past the title's 146 pixels. Which one this
        // is, the toolbar says in green.
        return Component.text("Phases", NamedTextColor.DARK_GRAY);
    }

    @Override
    public String breadcrumb() {
        return "Phases";
    }

    private Optional<Island> island() {
        return islands.book().ofPlayer(viewer.getUniqueId());
    }

    private long broken() {
        return island().flatMap(it -> it.block(dimension)).map(block -> block.broken()).orElse(0L);
    }

    @Override
    protected List<Phase> entries() {
        return islands.currentContent().phasesOf(dimension);
    }

    @Override
    protected ItemStack icon(Phase phase) {
        long broken = broken();
        PhaseRule.Standing standing = phases.standing(islands.currentContent(), dimension, broken);
        boolean current = standing.phase().equals(phase);
        boolean reached = phase.from() <= broken;
        List<String> lore = new ArrayList<>();
        lore.add("<dark_gray>From block " + String.format("%,d", phase.from()));
        if (current) {
            lore.add("<yellow>You are here  " + ProgressBar.mini(standing.percent(), BAR));
            standing.next().ifPresentOrElse(
                    next -> lore.add("<gray>" + String.format("%,d", standing.toNext()) + " more to <white>" + next.name()),
                    () -> lore.add("<gray>The last phase. It only gets better."));
        } else if (reached) {
            lore.add("<green>✔ Reached");
        } else {
            lore.add("<gray>In <white>" + String.format("%,d", phase.from() - broken) + "<gray> more blocks");
        }
        lore.add("");
        lore.add("<gray>Turns into:");
        int total = phase.blocks().stream().mapToInt(Phase.Weighted::weight).sum();
        phase.blocks().stream().sorted(Comparator.comparingInt(Phase.Weighted::weight).reversed()).limit(SHOWN_BLOCKS)
                .forEach(block -> lore.add("<dark_gray>· <white>" + pretty(block.material().name()) + " <dark_gray>"
                        + share(block.weight(), total)));
        if (phase.blocks().size() > SHOWN_BLOCKS) {
            lore.add("<dark_gray>· and " + (phase.blocks().size() - SHOWN_BLOCKS) + " more");
        }
        if (!phase.mobs().isEmpty()) {
            lore.add("<gray>Brings: <white>" + String.join(", ",
                    phase.mobs().stream().map(mob -> pretty(mob.type().name())).toList()));
        }
        phase.opens().stream().sorted().forEach(opened -> lore.add("<light_purple>Opens "
                + services.messages().raw(opened.nameKey())));
        ItemStack icon = Icons.of(phase.icon(), (current ? "<yellow>" : reached ? "<green>" : "<white>")
                + phase.name() + " <dark_gray>" + (entries().indexOf(phase) + 1) + "/" + entries().size(), lore);
        if (current) {
            icon.editMeta(meta -> meta.setEnchantmentGlintOverride(true));
        }
        return icon;
    }

    @Override
    protected void onClick(Phase phase, InventoryClickEvent event) {
    }

    @Override
    protected ItemStack emptyIcon() {
        return Icons.of(Material.BARRIER, "<gray>No phases here");
    }

    @Override
    protected void decorate() {
        Island island = island().orElse(null);
        IslandDimension[] all = IslandDimension.values();
        for (int i = 0; i < all.length; i++) {
            IslandDimension other = all[i];
            boolean open = island != null && island.unlocked(other);
            String name = services.messages().raw(other.nameKey());
            String locked = island == null ? "You have no island."
                    : phases.opener(islands.currentContent(), island, other)
                    .map(opener -> "Opens with " + opener.phase().name() + ", in "
                            + String.format("%,d", opener.toGo()) + " blocks.")
                    .orElse("Not open on your island.");
            toolbar(DIMENSION_COLUMNS[i], open && other != dimension, Icons.of(other.icon(),
                            (other == dimension ? "<green>" : "<white>") + name,
                            other == dimension ? "<gray>You are looking at these." : "<yellow>Click to see its phases."),
                    other == dimension ? "You are looking at these." : locked,
                    click -> new PhasesMenu(services, islands, viewer, parent(), other).open());
        }
    }

    private static String share(int weight, int total) {
        if (total <= 0) {
            return "";
        }
        double percent = weight * 100.0 / total;
        return percent < 1 ? "<1%" : Math.round(percent) + "%";
    }

    private static String pretty(String id) {
        String spaced = id.toLowerCase(Locale.ROOT).replace('_', ' ');
        return spaced.isEmpty() ? spaced : Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    @Override
    public String describe() {
        return "an island's phases for one dimension";
    }
}
