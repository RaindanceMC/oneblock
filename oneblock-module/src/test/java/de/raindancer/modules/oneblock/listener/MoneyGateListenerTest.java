package de.raindancer.modules.oneblock.listener;

import de.raindancer.core.ui.messages.Messages;
import de.raindancer.modules.oneblock.OneBlockSettings;
import de.raindancer.modules.oneblock.store.IslandBook;
import de.raindancer.modules.oneblock.util.PermissionNodes;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MoneyGateListenerTest {

    private final UUID id = UUID.randomUUID();
    private final Player player = mock(Player.class);
    private final Set<UUID> onDuty = new HashSet<>();
    private final MoneyGateListener gate;

    MoneyGateListenerTest() {
        IslandBook book = mock(IslandBook.class);
        when(book.ofPlayer(any())).thenReturn(Optional.empty());
        Server server = mock(Server.class);
        doReturn(List.of()).when(server).getOnlinePlayers();
        when(player.getServer()).thenReturn(server);
        when(player.getUniqueId()).thenReturn(id);
        gate = new MoneyGateListener(book, mock(Messages.class), () -> OneBlockSettings.DEFAULTS, onDuty::contains);
    }

    private boolean blocked(String typed) {
        PlayerCommandPreprocessEvent event = new PlayerCommandPreprocessEvent(player, typed);
        gate.onCommand(event);
        return event.isCancelled();
    }

    @Test
    @DisplayName("without an island that chose SMP money, /shop is blocked")
    void blocked() {
        assertThat(blocked("/shop")).isTrue();
        assertThat(blocked("/rainseconomy:shop")).isTrue();
        assertThat(blocked("/island menu")).isFalse();
    }

    @Test
    @DisplayName("an admin plays like everybody else: their permission waves it through only in /admin or /dev")
    void adminOnlyOnDuty() {
        when(player.hasPermission(PermissionNodes.ADMIN)).thenReturn(true);
        assertThat(blocked("/shop")).as("an admin off duty").isTrue();
        onDuty.add(id);
        assertThat(blocked("/shop")).as("on duty").isFalse();
    }

    @Test
    @DisplayName("being on duty alone is not enough: it takes the admin permission too")
    void dutyWithoutPermission() {
        onDuty.add(id);
        assertThat(blocked("/shop")).isTrue();
    }
}
