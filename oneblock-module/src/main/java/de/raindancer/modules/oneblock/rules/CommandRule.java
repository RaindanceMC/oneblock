package de.raindancer.modules.oneblock.rules;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** What {@code /island} knows: its subcommands, what each one takes as an argument, and its usage lines. */
public final class CommandRule implements IOneBlockRule {

    public enum Argument { NONE, PLAYER, TEAM_MEMBER, DIMENSION }

    /** What answers on every server, without going anywhere. */
    public static final List<String> ANYWHERE = List.of("invite", "guest", "accept", "deny", "sidebar");
    /** What only the island server does. */
    public static final List<String> ON_ISLANDS = List.of("menu", "create", "home", "nether", "end", "phases",
            "team", "kick", "leave", "setspawn", "info", "economy");

    private static final List<String> ALL;
    private static final Map<String, Argument> ARGUMENTS = Map.of("invite", Argument.PLAYER,
            "guest", Argument.PLAYER, "accept", Argument.PLAYER, "deny", Argument.PLAYER, "info", Argument.PLAYER,
            "kick", Argument.TEAM_MEMBER, "phases", Argument.DIMENSION);
    private static final Map<String, String> USAGE = Map.of("invite", "oneblock.invite.usage",
            "guest", "oneblock.guest.usage", "kick", "oneblock.kick.usage");

    static {
        List<String> all = new ArrayList<>(ON_ISLANDS);
        all.addAll(ANYWHERE);
        ALL = List.copyOf(all);
    }

    public List<String> subcommands(String prefix) {
        String started = prefix.toLowerCase(Locale.ROOT);
        return ALL.stream().filter(name -> name.startsWith(started)).toList();
    }

    public Argument argumentOf(String sub) {
        return ARGUMENTS.getOrDefault(sub.toLowerCase(Locale.ROOT), Argument.NONE);
    }

    public Optional<String> usageKey(String sub) {
        return Optional.ofNullable(USAGE.get(sub.toLowerCase(Locale.ROOT)));
    }

    /** Whether the word is a subcommand, or nothing at all. */
    public boolean knows(String sub) {
        return sub.isEmpty() || ALL.contains(sub.toLowerCase(Locale.ROOT));
    }

    @Override
    public String describe() {
        return "which subcommands /island has and what each completes";
    }
}
