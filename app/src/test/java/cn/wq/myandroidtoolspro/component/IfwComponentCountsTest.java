package cn.wq.myandroidtoolspro.component;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.*;

public class IfwComponentCountsTest {
    @Test public void countsExistingComponentsOnceAndKeepsTypesSeparate() {
        IfwComponentCounts counts = new IfwComponentCounts(true);
        counts.add(0, "example", ".Service");
        counts.add(0, "example", "example.Service");
        counts.add(0, "example", "example.Removed");
        counts.add(0, "other", "other.Service");
        counts.add(1, "example", "example.Receiver");
        counts.add(2, "example", "example.Alias");
        assertEquals(1, counts.count(app(), 0));
        assertEquals(1, counts.count(app(), 1));
        assertEquals(1, counts.count(app(), 2));
        assertEquals(0, counts.count(app(), 3));
    }

    @Test public void unavailableRulesAreUnknownRatherThanZero() {
        IfwComponentCounts counts = new IfwComponentCounts(false);
        assertEquals(-1, counts.count(app(), 0));
        assertEquals(-1, counts.count(app(), 1));
        assertEquals(-1, counts.count(app(), 2));
        assertEquals(0, counts.count(app(), 3));
        assertEquals(0, new IfwComponentCounts(true).count(app(), 0));
    }

    static IndexedPackage app() {
        return new IndexedPackage("example", "Example", false, "fingerprint", "en", Arrays.asList(
                new IndexedComponent(0, "example.Service", true, Collections.emptyList()),
                new IndexedComponent(1, "example.Receiver", true, Collections.emptyList()),
                new IndexedComponent(2, "example.Alias", true, Collections.emptyList()),
                new IndexedComponent(3, "example.Provider", true, Collections.emptyList())));
    }
}
