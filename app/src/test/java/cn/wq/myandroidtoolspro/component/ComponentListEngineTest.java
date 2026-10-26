package cn.wq.myandroidtoolspro.component;

import org.junit.After;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

public class ComponentListEngineTest {
    private final ExecutorService states = Executors.newFixedThreadPool(2);
    private final ExecutorService services = Executors.newSingleThreadExecutor();

    @After public void shutdown() {
        states.shutdownNow();
        services.shutdownNow();
    }

    @Test public void oneScanPartitionsBothTabsAndQueriesServicesOnce() throws Exception {
        AtomicInteger scans = new AtomicInteger();
        AtomicInteger serviceQueries = new AtomicInteger();
        ComponentListEngine engine = new ComponentListEngine(() -> {
            scans.incrementAndGet();
            return new ComponentCatalog(Arrays.asList(app("user", false), app("system", true)), 0);
        }, (app, type) -> 1, () -> {
            serviceQueries.incrementAndGet();
            return ServiceSnapshot.available(Collections.singletonMap("user", 2));
        }, states, services);

        ComponentSnapshot snapshot = engine.load(0, false);

        assertEquals(1, scans.get());
        assertEquals(1, serviceQueries.get());
        assertEquals("user", snapshot.userApps.get(0).packageName);
        assertEquals("system", snapshot.systemApps.get(0).packageName);
        assertEquals(1, snapshot.userApps.get(0).disabledNum);
        assertEquals(2, snapshot.userApps.get(0).runningNum);
        assertEquals(0, snapshot.systemApps.get(0).runningNum);
    }

    @Test public void eachNewLoadConfirmsStateAgainButNonServiceDoesNotQueryServices() throws Exception {
        AtomicInteger queries = new AtomicInteger();
        ComponentListEngine engine = new ComponentListEngine(
                () -> new ComponentCatalog(Collections.singletonList(app("user", false)), 0),
                (app, type) -> queries.incrementAndGet(),
                () -> { throw new AssertionError("Unexpected service query"); }, states, services);

        assertEquals(1, engine.load(2, false).userApps.get(0).disabledNum);
        assertEquals(2, engine.load(2, false).userApps.get(0).disabledNum);
    }

    @Test public void failureIsUnknownRatherThanZeroAndDoesNotHideOtherApps() throws Exception {
        ComponentListEngine engine = new ComponentListEngine(
                () -> new ComponentCatalog(Arrays.asList(app("broken", false), app("ok", false)), 1),
                (app, type) -> { if (app.packageName.equals("broken")) throw new SecurityException(); return 0; },
                ServiceSnapshot::unavailable, states, services);

        ComponentSnapshot snapshot = engine.load(0, false);

        assertEquals(2, snapshot.userApps.size());
        assertEquals(-1, snapshot.userApps.get(0).disabledNum);
        assertEquals(-1, snapshot.userApps.get(1).runningNum);
        assertTrue(snapshot.partial);
    }

    @Test public void ifwUsesHistoryExceptProvidersWhichRefreshPm() throws Exception {
        AtomicInteger queries = new AtomicInteger();
        ComponentListEngine engine = new ComponentListEngine(
                () -> new ComponentCatalog(Collections.singletonList(app("user", false)), 0),
                (app, type) -> queries.incrementAndGet(), ServiceSnapshot::unavailable, states, services);
        for (int type = 0; type < 3; type++) {
            engine.load(type, true);
        }
        assertEquals(0, queries.get());
        assertEquals(1, engine.load(3, true).userApps.get(0).disabledNum);
        assertEquals(1, queries.get());
    }

    @Test public void ifwCountsAreSeparateFromPmCountsAndSharedByBothTabs() throws Exception {
        AtomicInteger reads = new AtomicInteger();
        ComponentListEngine engine = new ComponentListEngine(
                () -> new ComponentCatalog(Arrays.asList(app("user", false), app("system", true)), 0),
                (app, type) -> 2, ServiceSnapshot::unavailable, states, services, () -> {
                    reads.incrementAndGet();
                    IfwComponentCounts counts = new IfwComponentCounts(true);
                    counts.add(1, "user", "user.Enabled");
                    counts.add(1, "user", "user.Enabled");
                    counts.add(1, "system", "system.Disabled");
                    return counts;
                });
        ComponentSnapshot snapshot = engine.load(1, true);
        assertEquals(1, reads.get());
        assertEquals(-1, snapshot.userApps.get(0).disabledNum);
        assertEquals(-1, snapshot.systemApps.get(0).disabledNum);
        assertEquals(1, snapshot.userApps.get(0).ifwNum);
        assertEquals(1, snapshot.systemApps.get(0).ifwNum);
        assertFalse(snapshot.partial);
        engine.load(1, true);
        assertEquals(2, reads.get());
        engine.load(1, false);
        engine.load(3, true);
        assertEquals(2, reads.get());
    }

    @Test public void ifwReadFailureKeepsPmCountsAndMarksOnlyIfwUnknown() throws Exception {
        ComponentListEngine engine = new ComponentListEngine(
                () -> new ComponentCatalog(Collections.singletonList(app("user", false)), 0),
                (app, type) -> 1, ServiceSnapshot::unavailable, states, services,
                () -> { throw new SecurityException("Root denied"); });
        ComponentSnapshot snapshot = engine.load(2, true);
        assertEquals(-1, snapshot.userApps.get(0).disabledNum);
        assertEquals(-1, snapshot.userApps.get(0).ifwNum);
        assertTrue(snapshot.partial);
    }

    @Test public void onlyActiveModeQueriesAndSavesWhileOtherCountIsHistorical() throws Exception {
        AtomicInteger pmQueries = new AtomicInteger();
        AtomicInteger ifwQueries = new AtomicInteger();
        RecordingHistory history = new RecordingHistory(new CountHistory.Entry("fingerprint", 7, 3));
        ComponentListEngine engine = new ComponentListEngine(
                () -> new ComponentCatalog(Collections.singletonList(app("user", false)), 0),
                (app, type) -> { pmQueries.incrementAndGet(); return 2; }, ServiceSnapshot::unavailable,
                states, services, () -> {
                    ifwQueries.incrementAndGet();
                    IfwComponentCounts counts = new IfwComponentCounts(true);
                    counts.add(1, "user", "user.Enabled");
                    return counts;
                }, history);
        ComponentSnapshot pm = engine.load(1, false);
        assertEquals(2, pm.userApps.get(0).disabledNum);
        assertEquals(3, pm.userApps.get(0).ifwNum);
        assertEquals(1, pmQueries.get());
        assertEquals(0, ifwQueries.get());
        assertFalse(history.savedIfw);
        ComponentSnapshot ifw = engine.load(1, true);
        assertEquals(7, ifw.userApps.get(0).disabledNum);
        assertEquals(1, ifw.userApps.get(0).ifwNum);
        assertEquals(1, pmQueries.get());
        assertEquals(1, ifwQueries.get());
        assertTrue(history.savedIfw);
        engine.load(3, true);
        assertEquals(2, pmQueries.get());
        assertEquals(1, ifwQueries.get());
        assertFalse(history.savedIfw);
    }

    @Test public void missingOrOutdatedHistoryDoesNotCausePartialResult() throws Exception {
        ComponentListEngine engine = new ComponentListEngine(
                () -> new ComponentCatalog(Collections.singletonList(app("user", false)), 0),
                (app, type) -> 1, ServiceSnapshot::unavailable, states, services,
                () -> new IfwComponentCounts(true),
                new RecordingHistory(new CountHistory.Entry("outdated", 7, 3)));
        ComponentSnapshot pm = engine.load(2, false);
        assertEquals(1, pm.userApps.get(0).disabledNum);
        assertEquals(-1, pm.userApps.get(0).ifwNum);
        assertFalse(pm.partial);
        ComponentSnapshot ifw = engine.load(2, true);
        assertEquals(-1, ifw.userApps.get(0).disabledNum);
        assertEquals(0, ifw.userApps.get(0).ifwNum);
        assertFalse(ifw.partial);
    }

    @Test public void failedActiveIfwQueryKeepsHistoricalPmCount() throws Exception {
        ComponentListEngine engine = new ComponentListEngine(
                () -> new ComponentCatalog(Collections.singletonList(app("user", false)), 0),
                (app, type) -> { throw new AssertionError("PM must not be queried in IFW mode"); },
                ServiceSnapshot::unavailable, states, services,
                () -> { throw new SecurityException(); },
                new RecordingHistory(new CountHistory.Entry("fingerprint", 7, 3)));
        ComponentSnapshot snapshot = engine.load(1, true);
        assertEquals(7, snapshot.userApps.get(0).disabledNum);
        assertEquals(-1, snapshot.userApps.get(0).ifwNum);
        assertTrue(snapshot.partial);
    }

    private static final class RecordingHistory implements CountHistory {
        private final Entry entry;
        boolean savedIfw;
        RecordingHistory(Entry entry) { this.entry = entry; }
        @Override public java.util.Map<String, Entry> read(int type) {
            return Collections.singletonMap("user", entry);
        }
        @Override public void save(int type, boolean ifw, List<IndexedPackage> packages,
                                   List<cn.wq.myandroidtoolspro.model.AppEntry> apps) { savedIfw = ifw; }
    }

    @Test(timeout = 5000) public void cancellationStopsBothStateWorkersAndAllowsNextLoad() throws Exception {
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch stopped = new CountDownLatch(2);
        CountDownLatch block = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximum = new AtomicInteger();
        ComponentListEngine engine = new ComponentListEngine(
                () -> new ComponentCatalog(Arrays.asList(app("one", false), app("two", false),
                        app("three", false), app("four", false)), 0),
                (app, type) -> {
                    int running = active.incrementAndGet();
                    maximum.updateAndGet(previous -> Math.max(previous, running));
                    entered.countDown();
                    try { block.await(); return 0; }
                    finally { active.decrementAndGet(); stopped.countDown(); }
                }, ServiceSnapshot::unavailable, states, services);
        ExecutorService caller = Executors.newSingleThreadExecutor();
        try {
            Future<ComponentSnapshot> task = caller.submit(() -> engine.load(2, false));
            assertTrue(entered.await(2, TimeUnit.SECONDS));
            assertEquals(2, maximum.get());
            task.cancel(true);
            assertTrue(stopped.await(2, TimeUnit.SECONDS));
            assertEquals(0, active.get());
            block.countDown();
            assertEquals(4, caller.submit(() -> engine.load(2, false)).get(2, TimeUnit.SECONDS).userApps.size());
            assertEquals(2, maximum.get());
        } finally {
            block.countDown();
            caller.shutdownNow();
        }
    }

    private IndexedPackage app(String name, boolean system) {
        List<IndexedComponent> components = new ArrayList<>();
        for (int type = 0; type < 4; type++) {
            components.add(new IndexedComponent(type, name + ".Enabled", true, Collections.emptyList()));
            components.add(new IndexedComponent(type, name + ".Disabled", false, Collections.emptyList()));
        }
        return new IndexedPackage(name, name, system, "fingerprint", "en", components);
    }
}
