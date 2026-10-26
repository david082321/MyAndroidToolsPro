package cn.wq.myandroidtoolspro.component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class RunningServiceParser {
    private static final Pattern SERVICE = Pattern.compile(
            "\\* ServiceRecord\\{\\S+\\s+u\\d+\\s+([\\w.$]+)/[^\\s}]+(?:\\s+[^}]*)?\\}");
    private static final Pattern PROCESS = Pattern.compile("app=ProcessRecord\\{\\S+ (\\d+):.*");

    static ServiceSnapshot parse(List<String> lines) {
        boolean header = false;
        String currentPackage = null;
        boolean processSeen = true;
        Map<String, Integer> counts = new HashMap<>();
        for (String line : lines) {
            String text = line.trim();
            if (text.startsWith("ACTIVITY MANAGER SERVICES")) header = true;
            if (text.startsWith("* ServiceRecord{")) {
                if (!processSeen) return ServiceSnapshot.unavailable();
                Matcher matcher = SERVICE.matcher(text);
                if (!matcher.matches()) return ServiceSnapshot.unavailable();
                currentPackage = matcher.group(1);
                processSeen = false;
            } else if (currentPackage != null && text.startsWith("app=")) {
                if (text.equals("app=null")) {
                    processSeen = true;
                } else {
                    Matcher matcher = PROCESS.matcher(text);
                    if (!matcher.matches()) return ServiceSnapshot.unavailable();
                    if (Long.parseLong(matcher.group(1)) > 0) {
                        Integer count = counts.get(currentPackage);
                        counts.put(currentPackage, count == null ? 1 : count + 1);
                    }
                    processSeen = true;
                }
                currentPackage = null;
            }
        }
        return header && processSeen ? ServiceSnapshot.available(counts) : ServiceSnapshot.unavailable();
    }
}
