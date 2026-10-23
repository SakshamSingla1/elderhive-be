package com.elderhive.presence;

import java.util.List;
import java.util.Map;

/**
 * The business-facing façade over PresenceTracker (ElderSphere's own reference has no
 * equivalent - PresenceController there calls PresenceTracker directly - but every other
 * piece of logic in this codebase sits behind a Service interface, so this one does too,
 * matching ElderHive's own established layering convention).
 */
public interface PresenceService {

    boolean isOnline(Long userId);

    /** A snapshot for a client that's just connected and hasn't received any
     *  /topic/presence events yet - preserves insertion order of userIds. */
    Map<Long, Boolean> getStatus(List<Long> userIds);

    /** See {@link PresenceTracker#heartbeat}. */
    void heartbeat(Long userId);
}
