package org.questfreedom.core;

import java.util.Locale;

/** Pure-Java filter logic shared by launcher and unit tests. */
public final class LauncherFilter {
    private LauncherFilter() {}

    public static boolean matches(String label, String packageName, String query) {
        if (packageName == null) return false;
        if (query == null || query.trim().isEmpty()) return true;
        String q = query.trim().toLowerCase(Locale.ROOT);
        return (label != null && label.toLowerCase(Locale.ROOT).contains(q))
                || packageName.toLowerCase(Locale.ROOT).contains(q);
    }
}
