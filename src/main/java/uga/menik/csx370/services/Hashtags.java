package uga.menik.csx370.services;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Hashtags consist of # followed by letters, numbers, or underscores. */
final class Hashtags {
    private static final Pattern TAG = Pattern.compile("#[\\p{L}\\p{N}_]+");

    private Hashtags() { }

    static List<String> parse(String input) {
        if (input == null || input.isBlank()) {
            return List.of();
        }
        var tags = new LinkedHashSet<String>();
        for (String token : input.strip().split("\\s+")) {
            if (!TAG.matcher(token).matches()) {
                throw new IllegalArgumentException("Enter hashtags separated by spaces, such as #2025 #fireworks.");
            }
            tags.add(token.toLowerCase(Locale.ROOT));
        }
        return List.copyOf(tags);
    }

    static String sqlPattern(String tag) {
        return "(^|[^[:alnum:]_#])" + tag + "([^[:alnum:]_]|$)";
    }
}
