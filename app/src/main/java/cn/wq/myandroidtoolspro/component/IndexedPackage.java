package cn.wq.myandroidtoolspro.component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class IndexedPackage {
    final String packageName;
    final String label;
    final boolean system;
    final String fingerprint;
    final String locale;
    final List<IndexedComponent> components;

    IndexedPackage(String packageName, String label, boolean system, String fingerprint,
                   String locale, List<IndexedComponent> components) {
        this.packageName = packageName;
        this.label = label;
        this.system = system;
        this.fingerprint = fingerprint;
        this.locale = locale;
        this.components = Collections.unmodifiableList(new ArrayList<>(components));
    }

    List<IndexedComponent> componentsOfType(int type) {
        List<IndexedComponent> result = new ArrayList<>();
        for (IndexedComponent component : components) {
            if (component.type == type) result.add(component);
        }
        return result;
    }
}

final class IndexedComponent {
    final int type;
    final String className;
    final boolean enabledInManifest;
    final List<String> actions;

    IndexedComponent(int type, String className, boolean enabledInManifest, List<String> actions) {
        this.type = type;
        this.className = className;
        this.enabledInManifest = enabledInManifest;
        this.actions = actions.isEmpty() ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(actions));
    }
}
