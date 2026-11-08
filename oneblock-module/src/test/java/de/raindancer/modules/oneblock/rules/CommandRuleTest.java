package de.raindancer.modules.oneblock.rules;

import de.raindancer.modules.oneblock.rules.CommandRule.Argument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CommandRuleTest {

    private final CommandRule rule = new CommandRule();

    @Test
    @DisplayName("subcommands complete by prefix, every one the command knows")
    void subcommands() {
        assertThat(rule.subcommands("")).contains("menu", "invite", "accept", "kick", "sidebar");
        assertThat(rule.subcommands("ne")).containsExactly("nether");
        assertThat(rule.subcommands("zzz")).isEmpty();
    }

    @Test
    @DisplayName("each subcommand completes the one thing it takes: players, team members, dimensions, or nothing")
    void arguments() {
        assertThat(rule.argumentOf("invite")).isEqualTo(Argument.PLAYER);
        assertThat(rule.argumentOf("guest")).isEqualTo(Argument.PLAYER);
        assertThat(rule.argumentOf("accept")).isEqualTo(Argument.PLAYER);
        assertThat(rule.argumentOf("deny")).isEqualTo(Argument.PLAYER);
        assertThat(rule.argumentOf("info")).isEqualTo(Argument.PLAYER);
        assertThat(rule.argumentOf("kick")).isEqualTo(Argument.TEAM_MEMBER);
        assertThat(rule.argumentOf("phases")).isEqualTo(Argument.DIMENSION);
        assertThat(rule.argumentOf("home")).isEqualTo(Argument.NONE);
        assertThat(rule.argumentOf("leave")).isEqualTo(Argument.NONE);
        assertThat(rule.argumentOf("bogus")).isEqualTo(Argument.NONE);
    }

    @Test
    @DisplayName("a subcommand that needs a name has its own usage line")
    void usage() {
        assertThat(rule.usageKey("invite")).contains("oneblock.invite.usage");
        assertThat(rule.usageKey("guest")).contains("oneblock.guest.usage");
        assertThat(rule.usageKey("kick")).contains("oneblock.kick.usage");
        assertThat(rule.usageKey("home")).isEmpty();
    }

    @Test
    @DisplayName("an unknown subcommand is recognised, a known one in any case is not")
    void unknown() {
        assertThat(rule.knows("NETHER")).isTrue();
        assertThat(rule.knows("")).isTrue();
        assertThat(rule.knows("nether2")).isFalse();
    }
}
