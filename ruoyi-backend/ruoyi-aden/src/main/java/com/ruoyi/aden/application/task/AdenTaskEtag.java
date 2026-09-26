package com.ruoyi.aden.application.task;

import com.ruoyi.aden.application.error.AdenApplicationException;
import com.ruoyi.aden.domain.task.AdenTaskId;
import com.ruoyi.aden.domain.task.AdenTaskVersion;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Task 强 ETag 编解码；不接受 weak tag、通配符或其他聚合的版本。 */
public final class AdenTaskEtag {
    private static final Pattern VALUE = Pattern.compile("^\"task-([0-9a-f-]{36})-v([0-9]+)\"$");

    private AdenTaskEtag() { }

    public static String encode(String taskId, String version) {
        new AdenTaskId(taskId);
        long parsed = parseVersion(version);
        return "\"task-" + taskId + "-v" + parsed + "\"";
    }

    public static AdenTaskVersion decode(String value, AdenTaskId expectedTaskId) {
        if (value == null || value.isBlank()) {
            throw new AdenApplicationException("ADEN_PRECONDITION_REQUIRED", "Task 命令必须提供 If-Match");
        }
        Matcher matcher = VALUE.matcher(value);
        if (!matcher.matches() || !expectedTaskId.value().equals(matcher.group(1))) {
            throw new AdenApplicationException("ADEN_VERSION_CONFLICT", "Task ETag 已失效");
        }
        return new AdenTaskVersion(parseVersion(matcher.group(2)));
    }

    private static long parseVersion(String value) {
        try {
            long parsed = Long.parseLong(value);
            if (parsed < 0 || !Long.toString(parsed).equals(value)) throw new NumberFormatException();
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Task ETag version 不合法");
        }
    }
}
