package cn.wq.myandroidtoolspro.component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import cn.wq.myandroidtoolspro.model.AppEntry;

interface CountHistory {
    Map<String, Entry> read(int type) throws Exception;
    void save(int type, boolean ifw, List<IndexedPackage> packages, List<AppEntry> apps) throws Exception;

    final class Entry {
        final String fingerprint;
        final int pmCount;
        final int ifwCount;

        Entry(String fingerprint, int pmCount, int ifwCount) {
            this.fingerprint = fingerprint;
            this.pmCount = pmCount;
            this.ifwCount = ifwCount;
        }
    }

    CountHistory NONE = new CountHistory() {
        @Override public Map<String, Entry> read(int type) { return Collections.emptyMap(); }
        @Override public void save(int type, boolean ifw, List<IndexedPackage> packages, List<AppEntry> apps) {}
    };
}
