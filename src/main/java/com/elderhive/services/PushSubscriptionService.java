package com.elderhive.services;

import com.elderhive.dtos.PushSubscription.PushSubscriptionRequest;

/**
 * Mirrors ElderSphere's PushSubscriptionService contract exactly: both operations are
 * idempotent since a browser re-registers the same endpoint on every page load.
 */
public interface PushSubscriptionService {

    /**
     * Registers a subscription for the given user. If {@code request.endpoint} is already
     * known, the existing row is overwritten in place (userId/keys refreshed) rather than
     * duplicated — re-registering the same endpoint is a no-op from the caller's point of
     * view, not an error.
     */
    void register(Long userId, PushSubscriptionRequest request);

    /**
     * Removes the subscription for the given user+endpoint. A no-op if no such row exists
     * (the browser may unsubscribe twice, e.g. a retried request).
     */
    void unsubscribe(Long userId, String endpoint);
}
