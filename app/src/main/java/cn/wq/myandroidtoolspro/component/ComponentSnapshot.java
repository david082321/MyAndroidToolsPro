package cn.wq.myandroidtoolspro.component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import cn.wq.myandroidtoolspro.model.AppEntry;

final class ComponentSnapshot {
    final List<AppEntry> userApps;
    final List<AppEntry> systemApps;
    final boolean partial;

    ComponentSnapshot(List<AppEntry> entries, boolean partial) {
        List<AppEntry> user = new ArrayList<>();
        List<AppEntry> system = new ArrayList<>();
        for (AppEntry entry : entries) {
            if (entry instanceof ClassifiedAppEntry && ((ClassifiedAppEntry) entry).system) {
                system.add(entry);
            } else {
                user.add(entry);
            }
        }
        userApps = Collections.unmodifiableList(user);
        systemApps = Collections.unmodifiableList(system);
        this.partial = partial;
    }

    static final class ClassifiedAppEntry extends AppEntry {
        final boolean system;
        ClassifiedAppEntry(boolean system) { this.system = system; }
    }
}
