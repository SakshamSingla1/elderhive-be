package com.elderhive.dao.push_subscription;

import com.elderhive.entities.PushSubscription;
import com.elderhive.repositories.PushSubscriptionRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public class PushSubscriptionDao {

    private final PushSubscriptionRepository pushSubscriptionRepository;

    public PushSubscriptionDao(PushSubscriptionRepository pushSubscriptionRepository) {
        this.pushSubscriptionRepository = pushSubscriptionRepository;
    }

    public PushSubscription save(PushSubscription subscription) {
        return pushSubscriptionRepository.save(subscription);
    }

    public List<PushSubscription> findByUserId(Long userId) {
        return pushSubscriptionRepository.findByUserId(userId);
    }

    public Optional<PushSubscription> findByEndpoint(String endpoint) {
        return pushSubscriptionRepository.findByEndpoint(endpoint);
    }

    @Transactional
    public void deleteByUserIdAndEndpoint(Long userId, String endpoint) {
        pushSubscriptionRepository.deleteByUserIdAndEndpoint(userId, endpoint);
    }

    @Transactional
    public void deleteById(Long id) {
        pushSubscriptionRepository.deleteById(id);
    }
}
