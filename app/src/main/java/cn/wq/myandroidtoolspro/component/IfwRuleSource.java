package cn.wq.myandroidtoolspro.component;

import android.content.ComponentName;
import android.content.Context;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParser;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

import cn.wq.myandroidtoolspro.helper.IfwUtil;
import cn.wq.myandroidtoolspro.helper.Utils;

final class IfwRuleSource implements ComponentListEngine.IfwSource {
    private final Context context;

    IfwRuleSource(Context context) { this.context = context.getApplicationContext(); }

    @Override public IfwComponentCounts load() throws Exception {
        ComponentListEngine.checkCancelled();
        File cache = context.getExternalCacheDir();
        if (cache == null) throw new IOException("External cache unavailable for IFW query");
        File directory = new File(cache, "component-ifw-" + UUID.randomUUID());
        if (!directory.mkdir()) throw new IOException("Cannot create IFW query directory");
        try {
            String command = "cp -fR " + RootShellQuery.quote(IfwUtil.getSystemIfwDir() + "/.")
                    + " " + RootShellQuery.quote(directory.getAbsolutePath());
            if (RootShellQuery.run(command) == null) throw new IOException("Cannot read system IFW rules");
            ComponentListEngine.checkCancelled();
            File[] files = directory.listFiles();
            if (files == null) throw new IOException("Cannot list IFW rules");
            IfwComponentCounts counts = new IfwComponentCounts(true);
            for (File file : files) {
                ComponentListEngine.checkCancelled();
                if (!file.isFile() || !file.getName().endsWith(".xml")) continue;
                try (FileInputStream input = new FileInputStream(file)) { readRules(input, counts); }
            }
            return counts;
        } catch (InterruptedException cancelled) {
            throw cancelled;
        } catch (Exception unavailable) {
            Utils.err("IfwRuleSource", "Cannot load IFW component counts", unavailable);
            throw unavailable;
        } finally {
            clearTemporary(directory, directory.getCanonicalPath());
        }
    }

    static void readRules(InputStream input, IfwComponentCounts counts) throws Exception {
        XmlPullParser parser = Xml.newPullParser();
        parser.setInput(input, null);
        boolean rootSeen = false;
        boolean rootClosed = false;
        int componentType = -1;
        boolean blocking = false;
        int event;
        while ((event = parser.next()) != XmlPullParser.END_DOCUMENT) {
            ComponentListEngine.checkCancelled();
            if (event == XmlPullParser.START_TAG) {
                int depth = parser.getDepth();
                if (depth == 1) {
                    if (rootSeen || !"rules".equals(parser.getName())) throw new IOException("Invalid IFW root");
                    rootSeen = true;
                } else if (depth == 2) {
                    String name = parser.getName();
                    componentType = "service".equals(name) ? 0 : "broadcast".equals(name) ? 1
                            : "activity".equals(name) ? 2 : -1;
                    blocking = Boolean.parseBoolean(parser.getAttributeValue(null, "block"));
                } else if (depth == 3 && blocking && componentType >= 0
                        && "component-filter".equals(parser.getName())) {
                    String name = parser.getAttributeValue(null, "name");
                    ComponentName component = name == null ? null : ComponentName.unflattenFromString(name);
                    if (component == null) throw new IOException("Invalid IFW component filter");
                    counts.add(componentType, component.getPackageName(), component.getClassName());
                }
            } else if (event == XmlPullParser.END_TAG) {
                if (parser.getDepth() == 1 && "rules".equals(parser.getName())) rootClosed = true;
                if (parser.getDepth() == 2) {
                    componentType = -1;
                    blocking = false;
                }
            }
        }
        if (!rootSeen || !rootClosed) throw new IOException("Incomplete IFW rules");
    }

    private static void clearTemporary(File file, String rootPath) {
        try {
            String path = file.getCanonicalPath();
            if ((path.equals(rootPath) || path.startsWith(rootPath + File.separator)) && file.isDirectory()) {
                File[] children = file.listFiles();
                if (children != null) for (File child : children) clearTemporary(child, rootPath);
            }
            file.delete();
        } catch (IOException ignored) {
            file.delete();
        }
    }
}
