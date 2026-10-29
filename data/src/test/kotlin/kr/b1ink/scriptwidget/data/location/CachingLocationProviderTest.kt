package kr.b1ink.scriptwidget.data.location

import kotlinx.coroutines.test.runTest
import kr.b1ink.scriptwidget.data.engine.FakeLocationProvider
import kr.b1ink.scriptwidget.data.engine.GeoLocation
import kr.b1ink.scriptwidget.data.engine.LocationAccuracy
import kr.b1ink.scriptwidget.data.engine.LocationUnavailableException
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CachingLocationProviderTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val delegate = FakeLocationProvider()
    private val provider by lazy { CachingLocationProvider(delegate, tmp.newFile("last-location.json")) }
    private val seoul = GeoLocation(37.5665, 126.978, timestamp = 100L)

    @Test
    fun `returns and remembers fresh locations`() = runTest {
        delegate.location = seoul

        assertEquals(seoul, provider.current(LocationAccuracy.Best))

        delegate.location = null
        assertEquals(seoul, provider.current(LocationAccuracy.Best))
    }

    @Test
    fun `falls back to the last location when a fix is unavailable, e g in a background widget`() = runTest {
        delegate.location = seoul
        provider.current(LocationAccuracy.Best)

        delegate.failure = LocationUnavailableException("timeout")
        val fresh = CachingLocationProvider(delegate, tmp.root.resolve("last-location.json"))

        assertEquals(seoul, fresh.current(LocationAccuracy.Best))
    }

    @Test
    fun `without permission the cache is not used`() = runTest {
        delegate.location = seoul
        provider.current(LocationAccuracy.Best)

        delegate.failure = LocationUnavailableException("denied", permissionDenied = true)

        assertFailsWith<LocationUnavailableException> { provider.current(LocationAccuracy.Best) }
    }

    @Test
    fun `fails when there is neither a fix nor a cached location`() = runTest {
        delegate.location = null

        assertFailsWith<LocationUnavailableException> { provider.current(LocationAccuracy.Best) }
    }

    // --- battery: a recent fix is reused instead of turning on GPS again ---

    private var now = 1_000_000L
    private var permitted = true
    private val reusing by lazy {
        CachingLocationProvider(delegate, tmp.newFile("reuse.json"), now = { now }, hasPermission = { permitted })
    }

    @Test
    fun `a recent enough fix is reused without asking for a new one`() = runTest {
        delegate.location = seoul.copy(timestamp = now)
        reusing.current(LocationAccuracy.ThreeKilometers)

        now += 10 * 60_000L
        reusing.current(LocationAccuracy.ThreeKilometers)

        assertEquals(1, delegate.requestedAccuracies.size)
    }

    @Test
    fun `precise requests only reuse very recent fixes`() = runTest {
        delegate.location = seoul.copy(timestamp = now)
        reusing.current(LocationAccuracy.Best)

        now += 30_000L
        reusing.current(LocationAccuracy.Best)
        assertEquals(1, delegate.requestedAccuracies.size)

        now += 2 * 60_000L
        reusing.current(LocationAccuracy.Best)
        assertEquals(2, delegate.requestedAccuracies.size)
    }

    @Test
    fun `a stale fix is not reused`() = runTest {
        delegate.location = seoul.copy(timestamp = now)
        reusing.current(LocationAccuracy.ThreeKilometers)

        now += 60 * 60_000L
        reusing.current(LocationAccuracy.ThreeKilometers)

        assertEquals(2, delegate.requestedAccuracies.size)
    }

    @Test
    fun `without permission a recent fix isn't handed out`() = runTest {
        delegate.location = seoul.copy(timestamp = now)
        reusing.current(LocationAccuracy.ThreeKilometers)

        permitted = false
        delegate.failure = LocationUnavailableException("denied", permissionDenied = true)

        assertFailsWith<LocationUnavailableException> { reusing.current(LocationAccuracy.ThreeKilometers) }
    }
}
