package org.remus.resticexplorer.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FormatUtilsTest {

    @Test
    void formatSizeReturnsNullForNull() {
        assertNull(FormatUtils.formatSize(null));
    }

    @Test
    void formatSizeShowsMBForSmallValues() {
        assertEquals("5.0 MB", FormatUtils.formatSize(5_242_880L));
    }

    @Test
    void formatSizeShowsMBForValueJustBelow1000MB() {
        // 999 MB = 999 * 1048576
        String result = FormatUtils.formatSize(999L * 1_048_576L);
        assertTrue(result.endsWith(" MB"), "Expected MB but got: " + result);
    }

    @Test
    void formatSizeShowsGBForValuesAbove1000MB() {
        // 5 GB = 5 * 1073741824
        assertEquals("5.00 GB", FormatUtils.formatSize(5_368_709_120L));
    }

    @Test
    void formatSizeShowsGBAtExactly1000MB() {
        // 1000 * 1048576 = 1048576000
        String result = FormatUtils.formatSize(1_048_576_000L);
        assertTrue(result.endsWith(" GB"), "Expected GB but got: " + result);
    }

    @Test
    void formatSizeShowsTBForValuesAbove1000GB() {
        // 2 TB = 2 * 1099511627776
        assertEquals("2.00 TB", FormatUtils.formatSize(2_199_023_255_552L));
    }

    @Test
    void formatSizeShowsTBAtExactly1000GB() {
        // 1000 * 1073741824 = 1073741824000
        String result = FormatUtils.formatSize(1_073_741_824_000L);
        assertTrue(result.endsWith(" TB"), "Expected TB but got: " + result);
    }

    @Test
    void formatSizeShowsZeroMB() {
        assertEquals("0.0 MB", FormatUtils.formatSize(0L));
    }
}
