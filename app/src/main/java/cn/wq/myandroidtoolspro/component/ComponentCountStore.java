package cn.wq.myandroidtoolspro.component;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteDatabaseCorruptException;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.wq.myandroidtoolspro.helper.Utils;
import cn.wq.myandroidtoolspro.model.AppEntry;

final class ComponentCountStore implements CountHistory {
    private final Context context;
    private File file;
    private Database helper;

    ComponentCountStore(Context context) {
        this.context = context.getApplicationContext();
    }

    private SQLiteDatabase database() {
        if (file == null) file = new File(context.getCacheDir(), "component-count-history.db");
        if (helper == null) helper = new Database(context, file);
        return helper.getWritableDatabase();
    }

    @Override public synchronized Map<String, Entry> read(int type) throws InterruptedException {
        ComponentListEngine.checkCancelled();
        Map<String, Entry> result = new HashMap<>();
        try (Cursor cursor = database().rawQuery(
                "SELECT package_name,fingerprint,pm_count,ifw_count FROM counts WHERE type=?",
                new String[]{Integer.toString(type)})) {
            while (cursor.moveToNext()) {
                ComponentListEngine.checkCancelled();
                result.put(cursor.getString(0), new Entry(cursor.getString(1), cursor.getInt(2), cursor.getInt(3)));
            }
        } catch (SQLiteException unavailable) {
            rebuild(unavailable);
            result.clear();
        }
        return result;
    }

    @Override public synchronized void save(int type, boolean ifw, List<IndexedPackage> packages,
                                          List<AppEntry> apps) throws InterruptedException {
        if (ifw && type == 3) return;
        Map<String, Entry> previous = read(type);
        Map<String, String> fingerprints = new HashMap<>();
        for (IndexedPackage app : packages) fingerprints.put(app.packageName, app.fingerprint);
        ComponentListEngine.checkCancelled();
        SQLiteDatabase db;
        try { db = database(); }
        catch (SQLiteException unavailable) { rebuild(unavailable); return; }
        try {
            db.beginTransaction();
            try (SQLiteStatement insert = db.compileStatement(
                    "INSERT OR REPLACE INTO counts(package_name,type,fingerprint,pm_count,ifw_count) VALUES(?,?,?,?,?)")) {
                for (AppEntry app : apps) {
                    ComponentListEngine.checkCancelled();
                    int current = ifw ? app.ifwNum : app.disabledNum;
                    String fingerprint = fingerprints.get(app.packageName);
                    if (current < 0 || fingerprint == null) continue;
                    Entry old = previous.get(app.packageName);
                    boolean matching = old != null && fingerprint.equals(old.fingerprint);
                    int pm = ifw ? matching ? old.pmCount : -1 : current;
                    int blocked = ifw ? current : matching ? old.ifwCount : -1;
                    insert.bindString(1, app.packageName);
                    insert.bindLong(2, type);
                    insert.bindString(3, fingerprint);
                    insert.bindLong(4, pm);
                    insert.bindLong(5, blocked);
                    insert.executeInsert();
                }
                db.setTransactionSuccessful();
            } finally { db.endTransaction(); }
        } catch (SQLiteException unavailable) { rebuild(unavailable); }
    }

    private void rebuild(SQLiteException failure) {
        Utils.err("ComponentCountStore", "Component count history unavailable", failure);
        if (!(failure instanceof SQLiteDatabaseCorruptException)
                && (failure.getMessage() == null || !failure.getMessage().contains("no such table: counts"))) return;
        try {
            if (helper != null) helper.close();
            helper = null;
            if (file != null) SQLiteDatabase.deleteDatabase(file);
        } catch (RuntimeException unavailable) {
            Utils.err("ComponentCountStore", "Cannot rebuild component count history", unavailable);
        }
    }

    private static final class Database extends SQLiteOpenHelper {
        Database(Context context, File file) { super(context, file.getAbsolutePath(), null, 1); }
        @Override public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE counts(package_name TEXT NOT NULL,type INTEGER NOT NULL,"
                    + "fingerprint TEXT NOT NULL,pm_count INTEGER NOT NULL DEFAULT -1,"
                    + "ifw_count INTEGER NOT NULL DEFAULT -1,PRIMARY KEY(package_name,type))");
        }
        @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            db.execSQL("DROP TABLE IF EXISTS counts");
            onCreate(db);
        }
    }
}
