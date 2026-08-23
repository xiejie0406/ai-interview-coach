package com.aiinterviewcoach.domain.interview;

import java.util.EnumSet;
import java.util.Set;

/** Session 状态到允许命令的唯一确定性映射。 */
public final class SessionPolicy {

    private SessionPolicy() {
    }

    public static Set<SessionCommandType> allowedCommands(SessionState state) {
        return switch (state) {
            case READY -> immutable(SessionCommandType.START, SessionCommandType.CANCEL);
            case IN_PROGRESS -> immutable(
                    SessionCommandType.PAUSE,
                    SessionCommandType.SKIP,
                    SessionCommandType.SUBMIT_ANSWER,
                    SessionCommandType.COMPLETE,
                    SessionCommandType.CANCEL);
            case PAUSED -> immutable(SessionCommandType.RESUME, SessionCommandType.COMPLETE, SessionCommandType.CANCEL);
            case FAILED_RECOVERABLE -> immutable(SessionCommandType.RECOVER, SessionCommandType.COMPLETE);
            case COMPLETING, COMPLETED, CANCELLED, FAILED_FINAL -> Set.of();
        };
    }

    public static boolean permits(SessionState state, SessionCommandType command) {
        return allowedCommands(state).contains(command);
    }

    private static Set<SessionCommandType> immutable(SessionCommandType first, SessionCommandType... rest) {
        EnumSet<SessionCommandType> result = EnumSet.of(first, rest);
        return Set.copyOf(result);
    }
}
