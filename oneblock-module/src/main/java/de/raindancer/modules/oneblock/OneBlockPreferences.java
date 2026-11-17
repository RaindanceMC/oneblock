package de.raindancer.modules.oneblock;

import de.raindancer.core.ui.messages.Messages;
import de.raindancer.core.ui.preference.Preference;
import de.raindancer.modules.oneblock.service.SidebarService;

/** What /preferences offers for OneBlock: the same switch /island sidebar flips. */
public final class OneBlockPreferences {

    private OneBlockPreferences() {
    }

    /** Only the island server has a sidebar, so only there is this offered. */
    public static Preference sidebar(SidebarService board, Messages messages) {
        return Preference.toggle("oneblock:sidebar", "OneBlock", "Island sidebar",
                "Shows your island, its phase and your team on the side of the screen.",
                SidebarService.SHOWN::isOn,
                (player, on) -> {
                    SidebarService.SHOWN.set(player, on);
                    if (!on) {
                        board.clear(player.getUniqueId());
                    }
                    messages.send(player, on ? "oneblock.sidebar.shown" : "oneblock.sidebar.hidden");
                });
    }
}
