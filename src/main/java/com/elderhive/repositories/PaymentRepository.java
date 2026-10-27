package com.elderhive.repositories;

import com.elderhive.entities.Payment;
import com.elderhive.enums.PaymentStatusEnum;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Optional<Payment> findByStripePaymentIntentId(String stripePaymentIntentId);

    // Backs createIntent()'s "reuse an existing PENDING attempt rather than create a
    // duplicate PaymentIntent" rule - a booking can have several FAILED retry rows plus at
    // most one PENDING/SUCCEEDED one at a time (enforced by the partial unique index in
    // V34, not here).
    Optional<Payment> findFirstByBookingIdAndStatusOrderByCreatedAtDesc(Long bookingId, PaymentStatusEnum status);

    // Backs GET /payments/booking/{bookingId} - the most recent attempt is what a caller
    // actually wants to see, not every historical FAILED retry.
    List<Payment> findByBookingIdOrderByCreatedAtDesc(Long bookingId);
}
