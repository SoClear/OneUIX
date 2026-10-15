package io.github.soclear.oneuix.common

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioEraserTileStateTest {
    @Test fun playbackLifecycleKeepsTileAvailableWhileOffAndGreysItOutAfterPlaybackStops() {
        val pkg = "com.instagram.android"
        assertEquals(0, AudioEraserTileState.resolve(false, pkg, 0))
        assertEquals(1, AudioEraserTileState.resolve(true, pkg, 0))
        assertEquals(2, AudioEraserTileState.resolve(true, pkg, 1))
        assertEquals(0, AudioEraserTileState.resolve(false, pkg, 1))
        assertEquals(2, AudioEraserTileState.resolve(true, pkg, 1))
    }

    @Test fun unavailableSamsungModesCannotBecomeAnActiveToggle() {
        assertEquals(0, AudioEraserTileState.resolve(true, "com.snapchat.android", 2))
        assertEquals(0, AudioEraserTileState.resolve(true, "com.snapchat.android", -1))
        assertEquals(0, AudioEraserTileState.resolve(true, "com.snapchat.android", 3))
    }

    @Test fun staleOrMissingPackageCannotBeControlled() {
        for (pkg in listOf(null, "", "  ")) {
            assertEquals(0, AudioEraserTileState.resolve(true, pkg, 1))
        }
    }

    @Test fun supportedPackagesAreChosenBySamsungRatherThanAHardcodedList() {
        assertEquals(1, AudioEraserTileState.resolve(true, "future.supported.app", 0))
    }
}
