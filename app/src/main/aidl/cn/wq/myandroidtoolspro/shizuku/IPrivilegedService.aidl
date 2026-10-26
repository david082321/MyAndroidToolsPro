package cn.wq.myandroidtoolspro.shizuku;

import android.content.ComponentName;
import android.os.Bundle;

interface IPrivilegedService {
    void setComponentEnabledSetting(in ComponentName component, int state, int flags, int userId) = 0;
    void setApplicationEnabledSetting(String packageName, int state, int flags, int userId) = 1;
    Bundle getServices(int maxNum) = 2;
    void destroy() = 16777114;
}
