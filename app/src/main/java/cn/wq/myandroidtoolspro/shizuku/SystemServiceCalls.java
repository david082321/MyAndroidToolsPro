package cn.wq.myandroidtoolspro.shizuku;

import android.content.ComponentName;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

final class SystemServiceCalls {
    private SystemServiceCalls() { }

    static void setComponentEnabledSetting(Object manager, Class<?> type, ComponentName component,
                                           int state, int flags, int userId, String caller) {
        Method method;
        Object[] args;
        try {
            method = type.getMethod("setComponentEnabledSetting", ComponentName.class,
                    int.class, int.class, int.class, String.class);
            args = new Object[]{component, state, flags, userId, caller};
        } catch (NoSuchMethodException oldPlatform) {
            try {
                method = type.getMethod("setComponentEnabledSetting", ComponentName.class,
                        int.class, int.class, int.class);
                args = new Object[]{component, state, flags, userId};
            } catch (NoSuchMethodException missing) {
                throw new IllegalStateException("Unsupported package manager interface", missing);
            }
        }
        invoke(manager, method, args);
    }

    static Object invoke(Object service, Method method, Object... args) {
        try {
            return method.invoke(service, args);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException) throw (RuntimeException) cause;
            throw new IllegalStateException("System service call failed", cause);
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Cannot access system service", failure);
        }
    }
}
