package org.remus.resticexplorer.config;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

public final class FormatUtils {

    private static final long MB = 1_048_576L;
    private static final long GB = 1_073_741_824L;
    private static final long TB = 1_099_511_627_776L;
    private static final long GB_THRESHOLD = 1_000L * MB;   // 1000 MB
    private static final long TB_THRESHOLD = 1_000L * GB;   // 1000 GB

    private FormatUtils() {
    }

    /**
     * Formats a byte count into a human-readable size string using adaptive units.
     * <ul>
     *   <li>&lt; 1000 MB → shown in MB (1 decimal)</li>
     *   <li>≥ 1000 MB and &lt; 1000 GB → shown in GB (2 decimals)</li>
     *   <li>≥ 1000 GB → shown in TB (2 decimals)</li>
     * </ul>
     *
     * @param bytes size in bytes, may be {@code null}
     * @return formatted string like "42.5 MB", "1.23 GB", or "2.05 TB"; {@code null} when input is {@code null}
     */
    public static String formatSize(Long bytes) {
        if (bytes == null) {
            return null;
        }
        DecimalFormatSymbols symbols = DecimalFormatSymbols.getInstance(Locale.US);
        if (bytes >= TB_THRESHOLD) {
            return new DecimalFormat("#,##0.00", symbols).format(bytes / (double) TB) + " TB";
        } else if (bytes >= GB_THRESHOLD) {
            return new DecimalFormat("#,##0.00", symbols).format(bytes / (double) GB) + " GB";
        } else {
            return new DecimalFormat("#,##0.0", symbols).format(bytes / (double) MB) + " MB";
        }
    }
}
