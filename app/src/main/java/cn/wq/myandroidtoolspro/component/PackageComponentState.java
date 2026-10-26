package cn.wq.myandroidtoolspro.component;

import android.content.pm.PackageManager;

import cn.wq.myandroidtoolspro.helper.Utils;
import cn.wq.myandroidtoolspro.model.ComponentModel;

final class PackageComponentState implements ComponentListEngine.StateSource {
    private final PackageManager pm;

    PackageComponentState(PackageManager pm) { this.pm = pm; }

    @Override public int disabledCount(IndexedPackage app, int type) throws Exception {
        int count = 0;
        for (IndexedComponent component : app.componentsOfType(type)) {
            ComponentListEngine.checkCancelled();
            if (!enabled(app.packageName, component)) count++;
        }
        return count;
    }

    boolean enabled(String packageName, IndexedComponent component) {
        ComponentModel model = new ComponentModel();
        model.packageName = packageName;
        model.className = component.className;
        model.enabledInManifest = component.enabledInManifest;
        return Utils.isComponentEnabled(model, pm);
    }
}
