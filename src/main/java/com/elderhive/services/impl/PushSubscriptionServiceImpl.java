package com.elderhive.services.impl;

import com.elderhive.dao.push_subscription.PushSubscriptionDao;
import com.elderhive.dtos.PushSubscription.PushSubscriptionRequest;
import com.elderhive.entities.PushSubscription;
import com.elderhive.services.PushSubscriptionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ported close to verbatim from ElderSphere's PushSubscriptionServiceImpl.
 */
@Service
@RequiredArgsConstructor
public class PushSubscriptionServiceImpl implements PushSubscriptionService {

    private final PushSubscriptionDao pushSubscriptionDao;

    @Override
    @Transactional
    public void register(Long userId, PushSubscriptionRequest request) {
        PushSubscription subscription = pushSubscriptionDao.findByEndpoint(request.getEndpoint())
                .orElseGet(() -> PushSubscription.builder().endpoint(request.getEndpoint()).build());
        subscription.setUserId(userId);
        subscription.setP256dhKey(request.getKeys().getP256dh());
        subscription.setAuthKey(request.getKeys().getAuth());
        pushSubscriptionDao.save(subscription);
    }

    @Override
    @Transactional
    public void unsubscribe(Long userId, String endpoint) {
        pushSubscriptionDao.deleteByUserIdAndEndpoint(userId, endpoint);
    }
}
