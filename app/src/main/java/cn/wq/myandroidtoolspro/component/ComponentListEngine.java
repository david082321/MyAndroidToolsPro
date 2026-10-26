package cn.wq.myandroidtoolspro.component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import cn.wq.myandroidtoolspro.model.AppEntry;

final class ComponentListEngine {
    interface IndexSource { ComponentCatalog load() throws Exception; }
    interface StateSource { int disabledCount(IndexedPackage app, int type) throws Exception; }
    interface RunningSource { ServiceSnapshot load() throws Exception; }
    interface IfwSource { IfwComponentCounts load() throws Exception; }

    private final IndexSource index;
    private final StateSource state;
    private final RunningSource running;
    private final ExecutorService stateWorkers;
    private final ExecutorService serviceWorker;
    private final IfwSource ifwSource;
    private final CountHistory history;

    ComponentListEngine(IndexSource index, StateSource state, RunningSource running,
                        ExecutorService stateWorkers, ExecutorService serviceWorker) {
        this(index, state, running, stateWorkers, serviceWorker, () -> new IfwComponentCounts(false));
    }

    ComponentListEngine(IndexSource index, StateSource state, RunningSource running,
                        ExecutorService stateWorkers, ExecutorService serviceWorker, IfwSource ifwSource) {
        this(index, state, running, stateWorkers, serviceWorker, ifwSource, CountHistory.NONE);
    }

    ComponentListEngine(IndexSource index, StateSource state, RunningSource running,
                        ExecutorService stateWorkers, ExecutorService serviceWorker,
                        IfwSource ifwSource, CountHistory history) {
        this.index = index;
        this.state = state;
        this.running = running;
        this.stateWorkers = stateWorkers;
        this.serviceWorker = serviceWorker;
        this.ifwSource = ifwSource;
        this.history = history;
    }

    ComponentSnapshot load(int type, boolean ifw) throws Exception {
        Future<ServiceSnapshot> serviceTask = type == 0 ? serviceWorker.submit(running::load) : null;
        List<Future<List<AppEntry>>> tasks = new ArrayList<>();
        try {
            ComponentCatalog catalog = index.load();
            final boolean queryingIfw = ifw && type != 3;
            final java.util.Map<String, CountHistory.Entry> historical = readHistory(type);
            final IfwComponentCounts ifwCounts = queryingIfw ? loadIfwCounts() : new IfwComponentCounts(true);
            for (int worker = 0; worker < 2; worker++) {
                final int offset = worker;
                tasks.add(stateWorkers.submit(() -> {
                    List<AppEntry> entries = new ArrayList<>();
                    for (int i = offset; i < catalog.packages.size(); i += 2) {
                        checkCancelled();
                        IndexedPackage app = catalog.packages.get(i);
                        int total = app.componentsOfType(type).size();
                        if (total == 0) continue;
                        AppEntry entry = new ComponentSnapshot.ClassifiedAppEntry(app.system);
                        entry.packageName = app.packageName;
                        entry.label = app.label;
                        entry.totalNum = total;
                        CountHistory.Entry cached = historical.get(app.packageName);
                        boolean matching = cached != null && app.fingerprint.equals(cached.fingerprint);
                        entry.ifwNum = type == 3 ? 0 : queryingIfw ? ifwCounts.count(app, type)
                                : matching ? cached.ifwCount : -1;
                        if (queryingIfw) {
                            entry.disabledNum = matching ? cached.pmCount : -1;
                        } else {
                            try {
                                entry.disabledNum = state.disabledCount(app, type);
                            } catch (InterruptedException cancelled) {
                                throw cancelled;
                            } catch (Exception unavailable) {
                                entry.disabledNum = -1;
                            }
                        }
                        entries.add(entry);
                    }
                    return entries;
                }));
            }
            List<AppEntry> entries = new ArrayList<>();
            for (Future<List<AppEntry>> task : tasks) entries.addAll(task.get());
            // Keep source order deterministic; each worker visits alternate packages.
            java.util.Map<String, AppEntry> byPackage = new java.util.HashMap<>();
            for (AppEntry entry : entries) byPackage.put(entry.packageName, entry);
            entries.clear();
            for (IndexedPackage app : catalog.packages) {
                AppEntry entry = byPackage.get(app.packageName);
                if (entry != null) entries.add(entry);
            }
            ServiceSnapshot services = ServiceSnapshot.unavailable();
            if (serviceTask != null) {
                try {
                    services = serviceTask.get(12, TimeUnit.SECONDS);
                } catch (ExecutionException | TimeoutException unavailable) {
                    services = ServiceSnapshot.unavailable();
                }
            }
            boolean partial = catalog.failures > 0 || (type == 0 && !services.available);
            for (AppEntry entry : entries) {
                if (type == 0) entry.runningNum = services.count(entry.packageName);
                partial |= queryingIfw ? entry.ifwNum < 0 : entry.disabledNum < 0;
            }
            checkCancelled();
            try { history.save(type, queryingIfw, catalog.packages, entries); }
            catch (InterruptedException cancelled) { throw cancelled; }
            catch (Exception unavailable) { /* Current counts remain valid when history cannot be saved. */ }
            return new ComponentSnapshot(entries, partial);
        } finally {
            for (Future<?> task : tasks) if (!task.isDone()) task.cancel(true);
            if (serviceTask != null && !serviceTask.isDone()) serviceTask.cancel(true);
        }
    }

    private IfwComponentCounts loadIfwCounts() throws InterruptedException {
        try { return ifwSource.load(); }
        catch (InterruptedException cancelled) { throw cancelled; }
        catch (Exception unavailable) { return new IfwComponentCounts(false); }
    }

    private java.util.Map<String, CountHistory.Entry> readHistory(int type) throws InterruptedException {
        try { return history.read(type); }
        catch (InterruptedException cancelled) { throw cancelled; }
        catch (Exception unavailable) { return java.util.Collections.emptyMap(); }
    }

    static void checkCancelled() throws InterruptedException {
        if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
    }
}
