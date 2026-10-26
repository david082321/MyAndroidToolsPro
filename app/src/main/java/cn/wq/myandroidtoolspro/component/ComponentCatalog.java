package cn.wq.myandroidtoolspro.component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class ComponentCatalog {
    final List<IndexedPackage> packages;
    final int failures;

    ComponentCatalog(List<IndexedPackage> packages, int failures) {
        this.packages = Collections.unmodifiableList(new ArrayList<>(packages));
        this.failures = failures;
    }
}
