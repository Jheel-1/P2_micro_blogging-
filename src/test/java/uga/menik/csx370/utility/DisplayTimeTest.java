package uga.menik.csx370.utility;

import java.time.LocalDateTime;
import junit.framework.TestCase;

public class DisplayTimeTest extends TestCase {
    public void testEasternTimeUsesWinterAndSummerOffsets() {
        assertEquals("Mar 07, 2025, 10:54 PM", DisplayTime.format(LocalDateTime.parse("2025-03-08T03:54:00")));
        assertEquals("Jul 07, 2025, 10:54 PM", DisplayTime.format(LocalDateTime.parse("2025-07-08T02:54:00")));
        assertEquals("Unknown", DisplayTime.format(null));
    }
}
