package org.questfreedom.core;

import org.junit.Test;
import static org.junit.Assert.*;

public final class LauncherFilterTest {
    @Test public void emptyQueryShowsAllValidPackages() {
        assertTrue(LauncherFilter.matches("Camera", "com.example.camera", ""));
        assertTrue(LauncherFilter.matches("Camera", "com.example.camera", null));
        assertFalse(LauncherFilter.matches("Camera", null, ""));
    }
    @Test public void matchesNameIgnoringCase() {
        assertTrue(LauncherFilter.matches("Firefox Browser", "org.mozilla.firefox", "fireFOX"));
        assertFalse(LauncherFilter.matches("Firefox Browser", "org.mozilla.firefox", "gallery"));
    }
    @Test public void matchesPackageAndTrimsSearch() {
        assertTrue(LauncherFilter.matches("Camera", "org.example.camera", "  ORG.EXAMPLE "));
        assertFalse(LauncherFilter.matches("Camera", "org.example.camera", "org.sample"));
    }
    @Test public void safelyHandlesMissingLabel() {
        assertTrue(LauncherFilter.matches(null, "org.example.camera", "camera"));
    }
}
