package com.ruoyi.interview.controller.rest.common;

import com.ruoyi.interview.domain.platform.AggregateVersion;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** OpenAPI `"vN"` ETag/If-Match 与领域 AggregateVersion 的唯一转换入口。 */
public final class HttpVersionPreconditions {

    private static final Pattern ETAG = Pattern.compile("^\\\"v([0-9]+)\\\"$");

    private HttpVersionPreconditions() {
    }

    public static AggregateVersion requireIfMatch(String value) {
        if (value == null) {
            throw new IllegalArgumentException("If-Match is required");
        }
        Matcher matcher = ETAG.matcher(value);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("If-Match must use the quoted vN format");
        }
        try {
            return new AggregateVersion(Long.parseLong(matcher.group(1)));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("If-Match version is out of range", exception);
        }
    }

    public static String etag(AggregateVersion version) {
        return "\"v" + java.util.Objects.requireNonNull(version).value() + "\"";
    }
}

