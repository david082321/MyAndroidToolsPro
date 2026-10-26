package cn.wq.myandroidtoolspro.helper;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import cn.wq.myandroidtoolspro.BuildConfig;
import cn.wq.myandroidtoolspro.shizuku.IPrivilegedService;
import cn.wq.myandroidtoolspro.shizuku.ShizukuUserService;
import rikka.shizuku.Shizuku;

public final class ShizukuUtil {
    private static final Object LOCK = new Object();
    private static IPrivilegedService service;
    private static CountDownLatch connectionReady;
    private static boolean binding;
    private static Shizuku.UserServiceArgs serviceArgs;

    private static final ServiceConnection CONNECTION = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            synchronized (LOCK) {
                service = IPrivilegedService.Stub.asInterface(binder);
                binding = false;
                if (connectionReady != null) connectionReady.countDown();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            resetConnection();
        }
    };

    private ShizukuUtil() { }

    public static boolean isSupported() {
        return Build.VERSION.SDK_INT >= 24;
    }

    public static void initialize() {
        if (!isSupported()) return;
        serviceArgs = new Shizuku.UserServiceArgs(new ComponentName(
                BuildConfig.APPLICATION_ID, ShizukuUserService.class.getName()))
                .tag("myandroidtools-privileged")
                .processNameSuffix("shizuku")
                .daemon(false)
                .debuggable(BuildConfig.DEBUG)
                .version(BuildConfig.VERSION_CODE);
        Shizuku.addBinderDeadListener(new Shizuku.OnBinderDeadListener() {
            @Override public void onBinderDead() { resetConnection(); }
        });
    }

    public static boolean hasPermission() {
        if (!isSupported()) return false;
        try {
            return Shizuku.pingBinder() && !Shizuku.isPreV11()
                    && Shizuku.getVersion() >= 12
                    && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
        } catch (RuntimeException unavailable) {
            return false;
        }
    }

    public static void setComponentEnabledSetting(ComponentName component, int state, int flags, int userId)
            throws RemoteException {
        getService().setComponentEnabledSetting(component, state, flags, userId);
    }

    public static void setApplicationEnabledSetting(String packageName, int state, int flags, int userId)
            throws RemoteException {
        getService().setApplicationEnabledSetting(packageName, state, flags, userId);
    }

    public static List<ActivityManager.RunningServiceInfo> getServices(int maxNum) throws RemoteException {
        Bundle result = getService().getServices(maxNum);
        return result.getParcelableArrayList("services");
    }

    private static IPrivilegedService getService() {
        if (!hasPermission()) throw new SecurityException("Shizuku is not running or permission is not granted");
        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new IllegalStateException("Privileged operations must run on a worker thread");
        }
        CountDownLatch ready;
        synchronized (LOCK) {
            if (service != null && service.asBinder().pingBinder()) return service;
            if (!binding) {
                connectionReady = new CountDownLatch(1);
                binding = true;
                try {
                    Shizuku.bindUserService(serviceArgs, CONNECTION);
                } catch (RuntimeException failure) {
                    binding = false;
                    connectionReady.countDown();
                    throw failure;
                }
            }
            ready = connectionReady;
        }
        try {
            if (!ready.await(15, TimeUnit.SECONDS)) {
                resetConnection();
                throw new IllegalStateException("Timed out starting Shizuku user service");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while connecting to Shizuku", interrupted);
        }
        synchronized (LOCK) {
            if (service == null || !service.asBinder().pingBinder()) {
                throw new IllegalStateException("Shizuku user service disconnected");
            }
            return service;
        }
    }

    private static void resetConnection() {
        synchronized (LOCK) {
            service = null;
            binding = false;
            if (connectionReady != null) connectionReady.countDown();
        }
    }
}
