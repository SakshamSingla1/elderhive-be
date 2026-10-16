package com.elderhive.entities;

import com.elderhive.audit.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Mirrors ElderSphere's PushSubscription exactly — one row per browser/device a user has
 * subscribed to Web Push on. Shape mirrors the standard {@code PushSubscription} object a
 * browser's Push API returns: {@code { endpoint, keys: { p256dh, auth } }}. {@code endpoint}
 * is globally unique — registering the same endpoint again (e.g. the browser re-registering
 * on page load) is idempotent, see services.impl.PushSubscriptionServiceImpl.
 */
@Entity
@Table(name = "push_subscriptions", uniqueConstraints =
        @UniqueConstraint(name = "uk_push_subscriptions_endpoint", columnNames = "endpoint"))
@Data
@EqualsAndHashCode(callSuper = false)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PushSubscription extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String endpoint;

    @Column(name = "p256dh_key", nullable = false, columnDefinition = "TEXT")
    private String p256dhKey;

    @Column(name = "auth_key", nullable = false, columnDefinition = "TEXT")
    private String authKey;
}
