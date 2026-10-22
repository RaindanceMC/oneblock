package de.raindancer.modules.oneblock.listener;

import de.raindancer.core.moderation.players.AdminModes;
import de.raindancer.core.platform.permission.Duty;
import de.raindancer.core.ui.messages.Messages;
import de.raindancer.modules.oneblock.OneBlockSettings;
import de.raindancer.modules.oneblock.model.EconomyMode;
import de.raindancer.modules.oneblock.model.Island;
import de.raindancer.modules.oneblock.store.IslandBook;
import de.raindancer.modules.oneblock.util.PermissionNodes;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * SMP money on the island server only for islands that chose it. The economy itself knows nothing of islands, so the
 * commands that reach it are what is gated: {@code /shop}, {@code /pay} and the rest of the configured list.
 */
public final class MoneyGateListener implements IOneBlockListener {

    private final IslandBook book;
    private final Messages messages;
    private final Supplier<OneBlockSettings> settings;
    private final Predicate<UUID> onDuty;

    /** Admins are waved through only in admin or dev mode; off duty they play like everybody else. */
    public MoneyGateListener(IslandBook book, Messages messages, Supplier<OneBlockSettings> settings) {
        this(book, messages, settings, who -> AdminModes.isOn(who) || Duty.devOn(who));
    }

    MoneyGateListener(IslandBook book, Messages messages, Supplier<OneBlockSettings> settings, Predicate<UUID> onDuty) {
        this.book = book;
        this.messages = messages;
        this.settings = settings;
        this.onDuty = onDuty;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!settings.get().isMoneyCommand(event.getMessage())
                || (player.hasPermission(PermissionNodes.ADMIN) && onDuty.test(player.getUniqueId()))) {
            return;
        }
        Optional<EconomyMode> mode = book.ofPlayer(player.getUniqueId()).map(Island::mode);
        if (mode.filter(EconomyMode::allowsSmpMoney).isPresent()) {
            return;
        }
        event.setCancelled(true);
        messages.send(player, mode.isEmpty() ? "oneblock.money.no-island"
                : mode.get() == EconomyMode.UNDECIDED ? "oneblock.money.undecided" : "oneblock.money.pure");
    }

    /** Nothing kept per player. */
    @Override
    public void forget(UUID player) {
    }
}
