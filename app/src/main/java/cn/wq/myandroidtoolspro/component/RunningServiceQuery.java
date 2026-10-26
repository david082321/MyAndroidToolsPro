package cn.wq.myandroidtoolspro.component;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.wq.myandroidtoolspro.helper.ShizukuUtil;
import cn.wq.myandroidtoolspro.helper.Utils;

final class RunningServiceQuery implements ComponentListEngine.RunningSource {
    private final Context context;

    RunningServiceQuery(Context context) { this.context = context.getApplicationContext(); }

    @Override public ServiceSnapshot load() throws Exception {
        ComponentListEngine.checkCancelled();
        if (Build.VERSION.SDK_INT < 26) {
            ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            return fromInfos(manager.getRunningServices(500));
        }
        if (Utils.isPmByShizuku(context) && ShizukuUtil.hasPermission()) {
            try { return fromInfos(ShizukuUtil.getServices(500)); }
            catch (Exception failure) {
                ComponentListEngine.checkCancelled();
                Utils.err("RunningServiceQuery", "Shizuku service query failed", failure);
            }
        }
        List<String> output = RootShellQuery.run("dumpsys activity s");
        return output == null ? ServiceSnapshot.unavailable() : RunningServiceParser.parse(output);
    }

    private ServiceSnapshot fromInfos(List<ActivityManager.RunningServiceInfo> infos) {
        if (infos == null) return ServiceSnapshot.unavailable();
        Map<String, Integer> counts = new HashMap<>();
        for (ActivityManager.RunningServiceInfo info : infos) {
            if (info == null || info.service == null || info.pid == 0 || info.restarting != 0) continue;
            String pkg = info.service.getPackageName();
            Integer count = counts.get(pkg);
            counts.put(pkg, count == null ? 1 : count + 1);
        }
        return ServiceSnapshot.available(counts);
    }
}
