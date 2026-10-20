package uga.menik.csx370.utility;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Database sessions use UTC. Every page displays Eastern Time, including daylight saving. */
public final class DisplayTime {
    private static final ZoneId DISPLAY_ZONE = ZoneId.of("America/New_York");
    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("MMM dd, yyyy, hh:mm a", Locale.US);

    private DisplayTime() { }

    public static String format(LocalDateTime utcTime) {
        return utcTime == null ? "Unknown" : utcTime.atOffset(ZoneOffset.UTC)
                .atZoneSameInstant(DISPLAY_ZONE).format(FORMAT);
    }
}
