package cn.wq.myandroidtoolspro.component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

final class IfwComponentCounts {
    final boolean available;
    private final Map<Integer, Map<String, Set<String>>> components = new HashMap<>();

    IfwComponentCounts(boolean available) { this.available = available; }

    void add(int type, String packageName, String className) {
        if (type < 0 || type > 2) return;
        if (className.startsWith(".")) className = packageName + className;
        Map<String, Set<String>> packages = components.get(type);
        if (packages == null) {
            packages = new HashMap<>();
            components.put(type, packages);
        }
        Set<String> names = packages.get(packageName);
        if (names == null) {
            names = new HashSet<>();
            packages.put(packageName, names);
        }
        names.add(className);
    }

    int count(IndexedPackage app, int type) {
        if (type == 3) return 0;
        if (!available) return -1;
        Map<String, Set<String>> packages = components.get(type);
        Set<String> blocked = packages == null ? null : packages.get(app.packageName);
        if (blocked == null) return 0;
        Set<String> matched = new HashSet<>();
        for (IndexedComponent component : app.componentsOfType(type)) {
            if (blocked.contains(component.className)) matched.add(component.className);
        }
        return matched.size();
    }
}
