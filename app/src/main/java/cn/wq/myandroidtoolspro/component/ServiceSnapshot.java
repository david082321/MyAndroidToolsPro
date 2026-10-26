package cn.wq.myandroidtoolspro.component;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

final class ServiceSnapshot {
    final boolean available;
    private final Map<String, Integer> counts;

    private ServiceSnapshot(boolean available, Map<String, Integer> counts) {
        this.available = available;
        this.counts = Collections.unmodifiableMap(new HashMap<>(counts));
    }

    static ServiceSnapshot available(Map<String, Integer> counts) {
        return new ServiceSnapshot(true, counts);
    }

    static ServiceSnapshot unavailable() {
        return new ServiceSnapshot(false, Collections.emptyMap());
    }

    int count(String packageName) {
        Integer count = counts.get(packageName);
        return available ? (count == null ? 0 : count) : -1;
    }
}
