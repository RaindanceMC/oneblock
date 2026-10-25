package de.raindancer.modules.oneblock.rules;

import de.raindancer.modules.oneblock.model.Island;

import java.util.Collection;
import java.util.Comparator;
import java.util.Locale;

/** What the OneBlock screens rank and say. */
public final class ScreenRule implements IOneBlockRule {

    /** The islands with the most blocks broken, the older first when equal; islands without a break are not ranked. */
    public java.util.List<Island> top(Collection<Island> islands, int most) {
        return islands.stream().filter(island -> island.totalBroken() > 0)
                .sorted(Comparator.comparingLong(Island::totalBroken).reversed()
                        .thenComparingLong(Island::created))
                .limit(most).toList();
    }

    /** The colour of a place in a top list. */
    public String place(int place) {
        return switch (place) {
            case 1 -> "<gold>";
            case 2 -> "<gray>";
            case 3 -> "<#cd7f32>";
            default -> "<white>";
        };
    }

    public String count(long number) {
        return String.format(Locale.ROOT, "%,d", number);
    }

    public String team(int everyone) {
        return everyone <= 1 ? "just you" : everyone + " players";
    }

    @Override
    public String describe() {
        return "what the OneBlock screens rank and say";
    }
}
