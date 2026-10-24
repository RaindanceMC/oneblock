package de.raindancer.modules.oneblock;

import de.raindancer.core.ui.help.HelpLint;
import de.raindancer.modules.api.ModuleCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class GuideTest {

    @Test
    @DisplayName("the guide's pages read, quote only settings that exist, and cover every command")
    void theGuideIsComplete() {
        assertThat(HelpLint.of(Path.of("src/main/resources/rains-help"))
                .module("oneblock")
                .settings(OneBlockSettings.class, OneBlockSettings.DEFAULTS)
                .commands(new OneBlockModule().commands().stream().map(ModuleCommand::name).toList())
                .problems()).isEmpty();
    }
}
