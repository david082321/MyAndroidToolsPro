package cn.wq.myandroidtoolspro.component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import eu.chainfire.libsuperuser.Shell;

final class RootShellQuery {
    static List<String> run(String command) throws InterruptedException {
        CountDownLatch done = new CountDownLatch(1);
        AtomicInteger status = new AtomicInteger(-1);
        AtomicReference<List<String>> output = new AtomicReference<>();
        AtomicBoolean launchFailed = new AtomicBoolean();
        Shell.Interactive shell = new Shell.Builder().setShell("su").setAutoHandler(false)
                .setWatchdogTimeout(10).addCommand(command, 0, (code, exit, lines) -> {
                    status.set(exit);
                    output.set(lines == null ? null : new ArrayList<>(lines));
                    done.countDown();
                }).open((code, exit, lines) -> {
                    if (exit != Shell.OnCommandResultListener.SHELL_RUNNING) {
                        launchFailed.set(exit == Shell.OnCommandResultListener.SHELL_EXEC_FAILED);
                        status.set(exit);
                        done.countDown();
                    }
                });
        try {
            return done.await(12, TimeUnit.SECONDS) && status.get() == 0 ? output.get() : null;
        } finally {
            // libsuperuser has no streams to close if starting the process itself failed.
            if (!launchFailed.get()) shell.kill();
        }
    }

    static String quote(String value) { return "'" + value.replace("'", "'\\''") + "'"; }
}
