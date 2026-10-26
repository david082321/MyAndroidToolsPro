package cn.wq.myandroidtoolspro.shizuku;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Process;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

public final class ShizukuUserService extends IPrivilegedService.Stub {
    public ShizukuUserService() { }

    @Override
    public void setComponentEnabledSetting(ComponentName component, int state, int flags, int userId) {
        Class<?> type = interfaceClass("android.content.pm.IPackageManager");
        SystemServiceCalls.setComponentEnabledSetting(systemService("package", type), type,
                component, state, flags, userId, callingPackage());
    }

    @Override
    public void setApplicationEnabledSetting(String packageName, int state, int flags, int userId) {
        Class<?> type = interfaceClass("android.content.pm.IPackageManager");
        Method method = method(type, "setApplicationEnabledSetting", String.class,
                int.class, int.class, int.class, String.class);
        SystemServiceCalls.invoke(systemService("package", type), method,
                packageName, state, flags, userId, callingPackage());
    }

    @Override
    @SuppressWarnings("unchecked")
    public Bundle getServices(int maxNum) {
        Class<?> type = interfaceClass("android.app.IActivityManager");
        Method method = method(type, "getServices", int.class, int.class);
        List<ActivityManager.RunningServiceInfo> services =
                (List<ActivityManager.RunningServiceInfo>) SystemServiceCalls.invoke(
                        systemService("activity", type), method, maxNum, 0);
        Bundle result = new Bundle();
        result.putParcelableArrayList("services", new ArrayList<>(services));
        return result;
    }

    @Override
    public void destroy() {
        System.exit(0);
    }

    private static String callingPackage() {
        return Process.myUid() == 0 ? "root" : "com.android.shell";
    }

    private static Class<?> interfaceClass(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException missing) {
            throw new IllegalStateException("Missing system interface: " + name, missing);
        }
    }

    private static Method method(Class<?> type, String name, Class<?>... parameters) {
        try {
            return type.getMethod(name, parameters);
        } catch (NoSuchMethodException missing) {
            throw new IllegalStateException("Missing system method: " + name, missing);
        }
    }

    private static Object systemService(String name, Class<?> type) {
        // Hidden framework APIs are accessed only inside Shizuku's privileged process.
        Class<?> manager = interfaceClass("android.os.ServiceManager");
        IBinder binder = (IBinder) SystemServiceCalls.invoke(null, method(manager, "getService", String.class), name);
        if (binder == null) throw new IllegalStateException("System service unavailable: " + name);
        Class<?> stub = interfaceClass(type.getName() + "$Stub");
        return SystemServiceCalls.invoke(null, method(stub, "asInterface", IBinder.class), binder);
    }
}
