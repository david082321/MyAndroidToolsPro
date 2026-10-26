package cn.wq.myandroidtoolspro.component;

import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import cn.wq.myandroidtoolspro.helper.DBHelper;
import cn.wq.myandroidtoolspro.model.ActionEntry;
import cn.wq.myandroidtoolspro.model.AppEntry;

final class ReceiverActionIndex {
    private final Context context;
    private final ComponentIndex index;
    private final PackageComponentState state;
    private final ExecutorService workers;
    private static final String[] COMMON_ACTIONS = {
            Intent.ACTION_BOOT_COMPLETED, ConnectivityManager.CONNECTIVITY_ACTION,
            WifiManager.NETWORK_STATE_CHANGED_ACTION, WifiManager.WIFI_STATE_CHANGED_ACTION,
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_PACKAGE_ADDED, Intent.ACTION_PACKAGE_REMOVED,
            Intent.ACTION_SCREEN_ON, Intent.ACTION_SCREEN_OFF, Intent.ACTION_USER_PRESENT
    };

    ReceiverActionIndex(Context context, ComponentIndex index, PackageComponentState state, ExecutorService workers) {
        this.context = context;
        this.index = index;
        this.state = state;
        this.workers = workers;
    }

    void replaceApps(ComponentSnapshot snapshot) throws InterruptedException {
        ComponentListEngine.checkCancelled();
        SQLiteDatabase db = DBHelper.getInstance(context).getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete(DBHelper.APPS_TABLE_NAME, null, null);
            insertApps(db, snapshot.userApps, false);
            insertApps(db, snapshot.systemApps, true);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }

    private void insertApps(SQLiteDatabase db, List<AppEntry> entries, boolean system) throws InterruptedException {
        for (AppEntry entry : entries) {
            ComponentListEngine.checkCancelled();
            ContentValues values = new ContentValues();
            values.put("app_name", entry.label);
            values.put("package_name", entry.packageName);
            values.put("is_system", system ? 1 : 0);
            db.insertOrThrow(DBHelper.APPS_TABLE_NAME, null, values);
        }
    }

    synchronized List<ActionEntry> load(boolean all, ComponentListLoader.Cancellation cancellation) throws Exception {
        checkCancelled(cancellation);
        ComponentCatalog catalog = index.load();
        if (catalog.failures > 0) throw new IllegalStateException("Receiver index is incomplete");
        List<Future<List<ReceiverRow>>> tasks = new ArrayList<>();
        try {
            for (int worker = 0; worker < 2; worker++) {
                final int offset = worker;
                tasks.add(workers.submit(() -> {
                    List<ReceiverRow> rows = new ArrayList<>();
                    for (int i = offset; i < catalog.packages.size(); i += 2) {
                        IndexedPackage app = catalog.packages.get(i);
                        for (IndexedComponent component : app.componentsOfType(1)) {
                            checkCancelled(cancellation);
                            rows.add(new ReceiverRow(app, component, state.enabled(app.packageName, component)));
                        }
                    }
                    return rows;
                }));
            }
            List<ReceiverRow> rows = new ArrayList<>();
            for (Future<List<ReceiverRow>> task : tasks) rows.addAll(task.get());
            checkCancelled(cancellation);
            SQLiteDatabase db = DBHelper.getInstance(context).getWritableDatabase();
            db.beginTransaction();
            try {
                db.delete(DBHelper.A_R_TABLE_NAME, null, null);
                db.delete(DBHelper.RECEIVER_TABLE_NAME, null, null);
                db.delete(DBHelper.ACTION_TABLE_NAME, null, null);
                Map<String, Long> actionIds = new HashMap<>();
                for (ReceiverRow row : rows) {
                    checkCancelled(cancellation);
                    ContentValues values = new ContentValues();
                    values.put("app_name", row.app.label);
                    values.put("package_name", row.app.packageName);
                    values.put("class_name", row.component.className);
                    values.put("all_actions", android.text.TextUtils.join("\n", row.component.actions));
                    values.put("enabled", row.enabled ? 1 : 0);
                    long receiver = db.insertOrThrow(DBHelper.RECEIVER_TABLE_NAME, null, values);
                    for (String action : row.component.actions) {
                        Long actionId = actionIds.get(action);
                        if (actionId == null) {
                            values = new ContentValues();
                            values.put("action_name", action);
                            actionId = db.insertOrThrow(DBHelper.ACTION_TABLE_NAME, null, values);
                            actionIds.put(action, actionId);
                        }
                        values = new ContentValues();
                        values.put("action_id", actionId);
                        values.put("receiver_id", receiver);
                        db.insertOrThrow(DBHelper.A_R_TABLE_NAME, null, values);
                    }
                }
                checkCancelled(cancellation);
                List<ActionEntry> result = query(db, all);
                db.setTransactionSuccessful();
                return result;
            } finally { db.endTransaction(); }
        } finally {
            for (Future<?> task : tasks) if (!task.isDone()) task.cancel(true);
        }
    }

    private List<ActionEntry> query(SQLiteDatabase db, boolean all) {
        List<ActionEntry> result = new ArrayList<>();
        if (all) {
            Cursor cursor = db.rawQuery("SELECT action.action_name,count(receiver._id),sum(receiver.enabled) "
                    + "FROM receiver JOIN a_r ON receiver._id=a_r.receiver_id JOIN action ON action._id=a_r.action_id "
                    + "GROUP BY action._id ORDER BY count(receiver._id) DESC,count(receiver._id)-sum(receiver.enabled) DESC", null);
            try { while (cursor.moveToNext()) result.add(action(cursor.getString(0), cursor.getInt(1), cursor.getInt(2))); }
            finally { cursor.close(); }
        } else {
            for (String name : COMMON_ACTIONS) {
                Cursor cursor = db.rawQuery("SELECT count(receiver._id),sum(receiver.enabled) FROM receiver "
                        + "JOIN a_r ON receiver._id=a_r.receiver_id JOIN action ON action._id=a_r.action_id WHERE action.action_name=?",
                        new String[]{name});
                try { if (cursor.moveToFirst()) result.add(action(name, cursor.getInt(0), cursor.getInt(1))); }
                finally { cursor.close(); }
            }
        }
        return result;
    }

    private ActionEntry action(String name, int total, int enabled) {
        ActionEntry entry = new ActionEntry();
        entry.actionName = name;
        entry.totalNum = total;
        entry.disabledNum = total - enabled;
        return entry;
    }

    private static void checkCancelled(ComponentListLoader.Cancellation cancellation) throws InterruptedException {
        ComponentListEngine.checkCancelled();
        if (cancellation.isCancelled()) throw new InterruptedException();
    }

    private static final class ReceiverRow {
        final IndexedPackage app;
        final IndexedComponent component;
        final boolean enabled;
        ReceiverRow(IndexedPackage app, IndexedComponent component, boolean enabled) {
            this.app = app;
            this.component = component;
            this.enabled = enabled;
        }
    }
}
