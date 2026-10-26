package cn.wq.myandroidtoolspro.component;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.SQLException;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.text.Collator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import cn.wq.myandroidtoolspro.helper.Utils;
import cn.wq.myandroidtoolspro.model.ActionEntry;
import cn.wq.myandroidtoolspro.model.AppEntry;

public final class ComponentListLoader {
    public interface Host { Session getComponentSession(); }
    public interface Callback { void onResult(Result result); }
    public interface Cancellation { boolean isCancelled(); }

    public static final class Result {
        private final ComponentSnapshot snapshot;
        public final Exception error;
        public final boolean partial;

        private Result(ComponentSnapshot snapshot, Exception error) {
            this.snapshot = snapshot;
            this.error = error;
            partial = snapshot != null && snapshot.partial;
        }

        public List<AppEntry> apps(boolean system) {
            return snapshot == null ? Collections.emptyList()
                    : new ArrayList<>(system ? snapshot.systemApps : snapshot.userApps);
        }
    }

    public final class Session {
        private final SharedLoad<ComponentSnapshot> shared;

        private Session(int type) {
            shared = new SharedLoad<>(loads, main::post, () -> loadSnapshot(type));
        }

        public Runnable observe(Callback callback) {
            return shared.observe((snapshot, error) -> callback.onResult(new Result(snapshot, error)));
        }

        public void refresh() { shared.refresh(); }

        public void close() {
            shared.close();
            synchronized (sessions) { sessions.remove(this); }
        }
    }

    private static ComponentListLoader instance;
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService loads = Executors.newSingleThreadExecutor();
    private final ExecutorService states = Executors.newFixedThreadPool(2);
    private final ExecutorService services = Executors.newSingleThreadExecutor();
    private final Set<Session> sessions = Collections.newSetFromMap(new WeakHashMap<Session, Boolean>());
    private final ComponentIndex index;
    private final ComponentListEngine engine;
    private final ReceiverActionIndex actions;

    private ComponentListLoader(Context context) {
        this.context = context.getApplicationContext();
        index = new ComponentIndex(this.context);
        PackageComponentState state = new PackageComponentState(this.context.getPackageManager());
        engine = new ComponentListEngine(index, state, new RunningServiceQuery(this.context), states, services,
                new IfwRuleSource(this.context), new ComponentCountStore(this.context));
        actions = new ReceiverActionIndex(this.context, index, state, states);
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        filter.addDataScheme("package");
        this.context.registerReceiver(new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent intent) {
                if (intent.getData() != null) index.invalidate(intent.getData().getSchemeSpecificPart());
                List<Session> active;
                synchronized (sessions) { active = new ArrayList<>(sessions); }
                for (Session session : active) session.refresh();
            }
        }, filter);
    }

    public static synchronized ComponentListLoader get(Context context) {
        if (instance == null) instance = new ComponentListLoader(context);
        return instance;
    }

    public Session newSession(int type) {
        if (type < 0 || type > 3) throw new IllegalArgumentException("Unknown component type");
        Session session = new Session(type);
        synchronized (sessions) { sessions.add(session); }
        return session;
    }

    public List<ActionEntry> loadActions(boolean all, Cancellation cancellation) throws Exception {
        if (Looper.myLooper() == Looper.getMainLooper()) throw new IllegalStateException("Background thread required");
        return actions.load(all, cancellation);
    }

    private ComponentSnapshot loadSnapshot(int type) throws Exception {
        if (Looper.myLooper() == Looper.getMainLooper()) throw new IllegalStateException("Background thread required");
        long started = SystemClock.elapsedRealtime();
        ComponentSnapshot snapshot = engine.load(type, Utils.isPmByIfw(context));
        boolean partial = snapshot.partial;
        if (type == 1) {
            try { actions.replaceApps(snapshot); }
            catch (SQLException unavailable) {
                partial = true;
                Utils.err("ComponentListLoader", "Cannot update receiver app table", unavailable);
            }
        }
        List<AppEntry> sorted = new ArrayList<>(snapshot.userApps);
        sorted.addAll(snapshot.systemApps);
        sortApps(context, sorted, type);
        snapshot = new ComponentSnapshot(sorted, partial);
        Utils.debug("ComponentListLoader", "type=" + type + " apps="
                + (snapshot.userApps.size() + snapshot.systemApps.size())
                + " elapsedMs=" + (SystemClock.elapsedRealtime() - started));
        return snapshot;
    }

    public static void sortApps(Context context, List<AppEntry> apps, int type) {
        final int sort = type == 0 ? PreferenceManager.getDefaultSharedPreferences(context).getInt("sort_service", 0) : 0;
        final Collator collator = Collator.getInstance(context.getResources().getConfiguration().locale);
        Collections.sort(apps, (left, right) -> {
            int a = sort == 1 ? left.runningNum : left.disabledNum;
            int b = sort == 1 ? right.runningNum : right.disabledNum;
            if (sort != 0 && a != b) return a < b ? 1 : -1;
            return collator.compare(left.label, right.label);
        });
    }
}
