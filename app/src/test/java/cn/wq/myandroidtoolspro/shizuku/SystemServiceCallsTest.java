package cn.wq.myandroidtoolspro.shizuku;

import android.content.ComponentName;
import org.junit.Test;
import static org.junit.Assert.assertArrayEquals;

public class SystemServiceCallsTest {
    @Test
    public void oldPlatformReceivesStateFlagsAndUserUnchanged() {
        OldPackageManager manager = new OldPackageManager();
        SystemServiceCalls.setComponentEnabledSetting(manager, OldPackageManager.class, null, 2, 1, 10, "com.android.shell");
        assertArrayEquals(new Object[]{null, 2, 1, 10}, manager.received);
    }

    @Test
    public void currentPlatformReceivesTheCallingPackageToo() {
        NewPackageManager manager = new NewPackageManager();
        SystemServiceCalls.setComponentEnabledSetting(manager, NewPackageManager.class, null, 1, 0, 0, "com.android.shell");
        assertArrayEquals(new Object[]{null, 1, 0, 0, "com.android.shell"}, manager.received);
    }

    @Test(expected = SecurityException.class)
    public void permissionFailureIsPropagatedToTheCaller() {
        SystemServiceCalls.setComponentEnabledSetting(new DeniedPackageManager(), DeniedPackageManager.class,
                null, 2, 0, 0, "com.android.shell");
    }

    public static class OldPackageManager {
        Object[] received;
        public void setComponentEnabledSetting(ComponentName component, int state, int flags, int user) {
            received = new Object[]{component, state, flags, user};
        }
    }

    public static class NewPackageManager {
        Object[] received;
        public void setComponentEnabledSetting(ComponentName component, int state, int flags, int user, String caller) {
            received = new Object[]{component, state, flags, user, caller};
        }
    }

    public static class DeniedPackageManager {
        public void setComponentEnabledSetting(ComponentName component, int state, int flags, int user, String caller) {
            throw new SecurityException("Denied by the system");
        }
    }
}
