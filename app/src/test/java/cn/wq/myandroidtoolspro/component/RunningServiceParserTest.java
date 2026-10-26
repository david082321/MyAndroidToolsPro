package cn.wq.myandroidtoolspro.component;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

public class RunningServiceParserTest {
    @Test public void acceptsClientMetadataAndIsolatedServiceNamesOnAndroid17() {
        ServiceSnapshot result = RunningServiceParser.parse(Arrays.asList(
                "ACTIVITY MANAGER SERVICES (dumpsys activity services)",
                "  * ServiceRecord{abc u0 example.app/.Live c:example.client}",
                "    app=ProcessRecord{def 123:example.app/u0a100}",
                "  * ServiceRecord{123 u0 example.browser/org.chromium.SandboxedProcessService0:0 c:example.browser}",
                "    app=ProcessRecord{456 456:example.browser/u0i1}"));
        assertTrue(result.available);
        assertEquals(1, result.count("example.app"));
        assertEquals(1, result.count("example.browser"));
    }

    @Test public void countsOnlyServicesWithLiveProcessesIncludingLastRecord() {
        ServiceSnapshot result = RunningServiceParser.parse(Arrays.asList(
                "ACTIVITY MANAGER SERVICES (dumpsys activity services)",
                "  * ServiceRecord{abc u0 example.app/.First}",
                "    app=ProcessRecord{def 123:example.app/u0a100}",
                "  * ServiceRecord{123 u0 example.app/.Stopped}", "    app=null",
                "  * ServiceRecord{456 u0 example.other/.Last}",
                "    app=ProcessRecord{789 456:example.other/u0a101}"));
        assertTrue(result.available);
        assertEquals(1, result.count("example.app"));
        assertEquals(1, result.count("example.other"));
    }

    @Test public void emptyValidDumpIsZeroButChangedFormatIsUnknown() {
        assertEquals(0, RunningServiceParser.parse(Collections.singletonList(
                "ACTIVITY MANAGER SERVICES (dumpsys activity services)")).count("example.app"));
        assertFalse(RunningServiceParser.parse(Collections.emptyList()).available);
        assertFalse(RunningServiceParser.parse(Collections.singletonList("Permission Denial")).available);
        assertFalse(RunningServiceParser.parse(Arrays.asList(
                "ACTIVITY MANAGER SERVICES (dumpsys activity services)",
                "* ServiceRecord{changed-format}")).available);
    }
}
