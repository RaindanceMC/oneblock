package de.raindancer.modules.oneblock;

import de.raindancer.core.ui.messages.Messages;
import de.raindancer.core.ui.preference.Preference;
import de.raindancer.modules.oneblock.service.SidebarService;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OneBlockPreferencesTest {

    private final SidebarService board = mock(SidebarService.class);
    private final Messages messages = mock(Messages.class);
    private final Player player = mock(Player.class);
    private final PersistentDataContainer data = mock(PersistentDataContainer.class);
    private final Preference preference = OneBlockPreferences.sidebar(board, messages);

    @Test
    void isTheSidebarSwitchWithOnAndOff() {
        assertThat(preference.id()).isEqualTo("oneblock:sidebar");
        assertThat(preference.options()).containsExactly("On", "Off");
        assertThat(preference.isPage()).isFalse();
    }

    @Test
    void hidingClearsTheSidebarAndSaysSo() {
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        when(player.getPersistentDataContainer()).thenReturn(data);

        preference.choose().accept(player, 1);
        verify(board).clear(id);
        verify(messages).send(player, "oneblock.sidebar.hidden");
    }

    @Test
    void showingDoesNotClearAnything() {
        when(player.getPersistentDataContainer()).thenReturn(data);

        preference.choose().accept(player, 0);
        verify(board, never()).clear(any());
        verify(messages).send(player, "oneblock.sidebar.shown");
    }
}
