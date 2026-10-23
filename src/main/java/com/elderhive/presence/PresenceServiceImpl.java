package com.elderhive.presence;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PresenceServiceImpl implements PresenceService {

    private final PresenceTracker presenceTracker;

    @Override
    public boolean isOnline(Long userId) {
        return presenceTracker.isOnline(userId);
    }

    @Override
    public Map<Long, Boolean> getStatus(List<Long> userIds) {
        Map<Long, Boolean> status = new LinkedHashMap<>();
        userIds.forEach(userId -> status.put(userId, presenceTracker.isOnline(userId)));
        return status;
    }

    @Override
    public void heartbeat(Long userId) {
        presenceTracker.heartbeat(userId);
    }
}
