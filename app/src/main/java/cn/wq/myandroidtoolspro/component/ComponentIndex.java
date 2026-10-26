package cn.wq.myandroidtoolspro.component;

import android.content.ContentValues;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Build;
import android.os.SystemClock;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import cn.wq.myandroidtoolspro.helper.Utils;

final class ComponentIndex implements ComponentListEngine.IndexSource {
    private static final String ANDROID = "http://schemas.android.com/apk/res/android";
    private final Context context;
    private final Object invalidationLock = new Object();
    private final Map<String, Long> dirtyPackages = new HashMap<>();
    private long invalidationSequence;
    private CacheDatabase helper;
    private File cacheFile;
    private Map<String, IndexedPackage> memoryCache;

    ComponentIndex(Context context) {
        this.context = context.getApplicationContext();
    }

    void invalidate(String packageName) {
        if (packageName == null) return;
        synchronized (invalidationLock) { dirtyPackages.put(packageName, ++invalidationSequence); }
    }

    @Override public synchronized ComponentCatalog load() throws Exception {
        long started = SystemClock.elapsedRealtime();
        ComponentListEngine.checkCancelled();
        if (helper == null) {
            if (cacheFile == null) cacheFile = new File(context.getCacheDir(), "component-index.db");
            helper = new CacheDatabase(context, cacheFile);
        }
        PackageManager pm = context.getPackageManager();
        List<PackageInfo> installed = pm.getInstalledPackages(0);
        Map<String, IndexedPackage> cached = memoryCache == null ? readCache() : new HashMap<>(memoryCache);
        Map<String, Long> invalidations;
        synchronized (invalidationLock) {
            invalidations = new HashMap<>(dirtyPackages);
        }
        Set<String> invalidated = invalidations.keySet();
        for (String name : invalidated) cached.remove(name);
        String locale = context.getResources().getConfiguration().locale.toString();
        List<IndexedPackage> result = new ArrayList<>();
        List<IndexedPackage> changed = new ArrayList<>();
        Set<String> installedNames = new HashSet<>();
        int failures = 0;
        int parsed = 0;
        for (PackageInfo info : installed) {
            ComponentListEngine.checkCancelled();
            if (info == null || info.applicationInfo == null) continue;
            ApplicationInfo app = info.applicationInfo;
            installedNames.add(app.packageName);
            String fingerprint = fingerprint(info);
            IndexedPackage old = cached.get(app.packageName);
            try {
                IndexedPackage entry;
                boolean system = (app.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                if (old != null && old.fingerprint.equals(fingerprint)) {
                    entry = old;
                    if (!old.locale.equals(locale) || old.system != system) {
                        entry = new IndexedPackage(app.packageName, Utils.getAppLabel(pm, app), system,
                                fingerprint, locale, old.components);
                        changed.add(entry);
                    }
                } else {
                    entry = new IndexedPackage(app.packageName, Utils.getAppLabel(pm, app), system,
                            fingerprint, locale, parse(app));
                    parsed++;
                    changed.add(entry);
                }
                result.add(entry);
            } catch (InterruptedException cancelled) {
                throw cancelled;
            } catch (Exception failure) {
                failures++;
                // A failed parse must not become a persistent, apparently valid empty package.
                invalidate(app.packageName);
                Utils.err("ComponentIndex", "Cannot index " + app.packageName, failure);
            }
        }
        ComponentListEngine.checkCancelled();
        if (!changed.isEmpty() || !invalidated.isEmpty() || !installedNames.equals(cached.keySet())) {
            writeCache(changed, installedNames, invalidated);
        }
        memoryCache = new HashMap<>();
        for (IndexedPackage app : result) memoryCache.put(app.packageName, app);
        synchronized (invalidationLock) {
            for (Map.Entry<String, Long> entry : invalidations.entrySet()) {
                if (entry.getValue().equals(dirtyPackages.get(entry.getKey()))) dirtyPackages.remove(entry.getKey());
            }
        }
        Utils.debug("ComponentIndex", "packages=" + result.size() + " parsed=" + parsed
                + " failed=" + failures + " elapsedMs=" + (SystemClock.elapsedRealtime() - started));
        if (result.isEmpty() && failures > 0) throw new IllegalStateException("Cannot read component index");
        return new ComponentCatalog(result, failures);
    }

    private String fingerprint(PackageInfo info) {
        ApplicationInfo app = info.applicationInfo;
        StringBuilder key = new StringBuilder(Build.FINGERPRINT).append('|')
                .append(info.lastUpdateTime).append('|').append(info.versionCode);
        if (Build.VERSION.SDK_INT >= 28) key.append('|').append(info.getLongVersionCode());
        appendFile(key, app.sourceDir);
        if (Build.VERSION.SDK_INT >= 21 && app.splitSourceDirs != null) {
            for (String split : app.splitSourceDirs) appendFile(key, split);
        }
        return key.toString();
    }

    private void appendFile(StringBuilder key, String path) {
        key.append('|').append(path);
        if (path != null) {
            File file = new File(path);
            key.append(':').append(file.length()).append(':').append(file.lastModified());
        }
    }

    private List<IndexedComponent> parse(ApplicationInfo app) throws Exception {
        Context target = context.createPackageContext(app.packageName, 0);
        AssetManager assets = target.getAssets();
        Method addAssetPath = AssetManager.class.getMethod("addAssetPath", String.class);
        int cookie = (Integer) addAssetPath.invoke(assets, app.sourceDir);
        List<IndexedComponent> components = new ArrayList<>();
        XmlResourceParser parser = assets.openXmlResourceParser(cookie, "AndroidManifest.xml");
        try {
            Resources resources = new Resources(assets, target.getResources().getDisplayMetrics(), null);
            int event;
            while ((event = parser.next()) != XmlResourceParser.END_DOCUMENT) {
                ComponentListEngine.checkCancelled();
                if (event != XmlResourceParser.START_TAG) continue;
                int type = componentType(parser.getName());
                if (type < 0) continue;
                String name = Utils.getAttributeValueByName(parser, resources, "name");
                if (name == null) continue;
                if (!name.contains(".")) name = app.packageName + "." + name;
                else if (name.startsWith(".")) name = app.packageName + name;
                boolean enabled = parser.getAttributeBooleanValue(ANDROID, "enabled", true);
                List<String> actions = new ArrayList<>();
                if (type == 1) {
                    int depth = parser.getDepth();
                    while ((event = parser.next()) != XmlResourceParser.END_DOCUMENT
                            && (event != XmlResourceParser.END_TAG || parser.getDepth() > depth)) {
                        ComponentListEngine.checkCancelled();
                        if (event == XmlResourceParser.START_TAG && "action".equals(parser.getName())) {
                            String action = Utils.getAttributeValueByName(parser, resources, "name");
                            if (action != null) actions.add(action);
                        }
                    }
                }
                components.add(new IndexedComponent(type, name, enabled, actions));
            }
        } finally {
            parser.close();
        }
        return components;
    }

    private int componentType(String tag) {
        if ("service".equals(tag)) return 0;
        if ("receiver".equals(tag)) return 1;
        if ("activity".equals(tag) || "activity-alias".equals(tag)) return 2;
        if ("provider".equals(tag)) return 3;
        return -1;
    }

    private Map<String, IndexedPackage> readCache() {
        try {
            return readCache(helper.getWritableDatabase());
        } catch (SQLiteException | JSONException corrupt) {
            Utils.err("ComponentIndex", "Rebuilding component cache", corrupt);
            try {
                helper.close();
                SQLiteDatabase.deleteDatabase(cacheFile);
                helper = new CacheDatabase(context, cacheFile);
            } catch (RuntimeException unavailable) {
                Utils.err("ComponentIndex", "Cache storage unavailable", unavailable);
            }
            return Collections.emptyMap();
        }
    }

    private Map<String, IndexedPackage> readCache(SQLiteDatabase db) throws JSONException {
        Map<String, List<IndexedComponent>> components = new HashMap<>();
        Cursor cursor = db.query("components", null, null, null, null, null, "_id");
        try {
            while (cursor.moveToNext()) {
                String pkg = cursor.getString(cursor.getColumnIndexOrThrow("package_name"));
                List<IndexedComponent> list = components.get(pkg);
                if (list == null) { list = new ArrayList<>(); components.put(pkg, list); }
                JSONArray array = new JSONArray(cursor.getString(cursor.getColumnIndexOrThrow("actions")));
                List<String> actions = new ArrayList<>();
                for (int i = 0; i < array.length(); i++) actions.add(array.getString(i));
                list.add(new IndexedComponent(cursor.getInt(cursor.getColumnIndexOrThrow("type")),
                        cursor.getString(cursor.getColumnIndexOrThrow("class_name")),
                        cursor.getInt(cursor.getColumnIndexOrThrow("enabled")) != 0, actions));
            }
        } finally { cursor.close(); }
        Map<String, IndexedPackage> result = new HashMap<>();
        cursor = db.query("packages", null, null, null, null, null, null);
        try {
            while (cursor.moveToNext()) {
                String pkg = cursor.getString(cursor.getColumnIndexOrThrow("package_name"));
                List<IndexedComponent> list = components.get(pkg);
                if (list == null) list = Collections.emptyList();
                if (list.size() != cursor.getInt(cursor.getColumnIndexOrThrow("component_count"))) {
                    throw new SQLiteException("Incomplete component cache");
                }
                result.put(pkg, new IndexedPackage(pkg,
                        cursor.getString(cursor.getColumnIndexOrThrow("label")),
                        cursor.getInt(cursor.getColumnIndexOrThrow("is_system")) != 0,
                        cursor.getString(cursor.getColumnIndexOrThrow("fingerprint")),
                        cursor.getString(cursor.getColumnIndexOrThrow("locale")), list));
            }
        } finally { cursor.close(); }
        return result;
    }

    private void writeCache(List<IndexedPackage> changed, Set<String> installed, Set<String> invalidated)
            throws InterruptedException {
        ComponentListEngine.checkCancelled();
        try {
            SQLiteDatabase db = helper.getWritableDatabase();
            db.beginTransaction();
            try {
                Set<String> remove = new HashSet<>(invalidated);
                Cursor cursor = db.query("packages", new String[]{"package_name"}, null, null, null, null, null);
                try { while (cursor.moveToNext()) if (!installed.contains(cursor.getString(0))) remove.add(cursor.getString(0)); }
                finally { cursor.close(); }
                for (String name : remove) deletePackage(db, name);
                for (IndexedPackage app : changed) {
                    ComponentListEngine.checkCancelled();
                    deletePackage(db, app.packageName);
                    ContentValues values = new ContentValues();
                    values.put("package_name", app.packageName);
                    values.put("label", app.label);
                    values.put("is_system", app.system ? 1 : 0);
                    values.put("fingerprint", app.fingerprint);
                    values.put("locale", app.locale);
                    values.put("component_count", app.components.size());
                    db.insertOrThrow("packages", null, values);
                    for (IndexedComponent component : app.components) {
                        values = new ContentValues();
                        values.put("package_name", app.packageName);
                        values.put("type", component.type);
                        values.put("class_name", component.className);
                        values.put("enabled", component.enabledInManifest ? 1 : 0);
                        values.put("actions", new JSONArray(component.actions).toString());
                        db.insertOrThrow("components", null, values);
                    }
                }
                db.setTransactionSuccessful();
            } finally { db.endTransaction(); }
        } catch (SQLiteException unavailable) {
            Utils.err("ComponentIndex", "Cannot save component cache", unavailable);
        }
    }

    private void deletePackage(SQLiteDatabase db, String name) {
        db.delete("components", "package_name=?", new String[]{name});
        db.delete("packages", "package_name=?", new String[]{name});
    }

    private static final class CacheDatabase extends SQLiteOpenHelper {
        CacheDatabase(Context context, File file) {
            super(context, file.getAbsolutePath(), null, 1);
        }

        @Override public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE packages(package_name TEXT PRIMARY KEY,label TEXT NOT NULL,"
                    + "is_system INTEGER NOT NULL,fingerprint TEXT NOT NULL,locale TEXT NOT NULL,component_count INTEGER NOT NULL)");
            db.execSQL("CREATE TABLE components(_id INTEGER PRIMARY KEY,package_name TEXT NOT NULL,"
                    + "type INTEGER NOT NULL,class_name TEXT NOT NULL,enabled INTEGER NOT NULL,actions TEXT NOT NULL)");
            db.execSQL("CREATE INDEX components_package ON components(package_name)");
        }

        @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            db.execSQL("DROP TABLE IF EXISTS components");
            db.execSQL("DROP TABLE IF EXISTS packages");
            onCreate(db);
        }
    }
}
