package lt.tsx.tennarc.tv

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConnectionKeeperTest {
    private val scope = TestScope()
    private var runs = 0
    private val keeper = ConnectionKeeper(scope, idleMs = 1_000) {
        runs++
        awaitCancellation()
    }

    @Test
    fun `let go of, the connection goes after the idle time`() {
        keeper.hold()
        scope.runCurrent()
        assertTrue(keeper.active)
        keeper.release()
        scope.advanceTimeBy(1_001)
        assertFalse(keeper.active)
        scope.cancel()
    }

    @Test
    fun `held again before the idle time, it stays`() {
        keeper.hold()
        keeper.release()
        scope.advanceTimeBy(500)
        keeper.hold()
        scope.advanceTimeBy(2_000)
        assertTrue(keeper.active)
        assertTrue(runs == 1)
        scope.cancel()
    }

    @Test
    fun `a second release doesn't leave a stale timer to drop a connection held since`() {
        keeper.hold()
        keeper.release()
        scope.advanceTimeBy(500)
        keeper.release()
        keeper.hold()
        scope.advanceTimeBy(2_000)
        assertTrue(keeper.active)
        scope.cancel()
    }
}
