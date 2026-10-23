package com.elderhive.repositories;

import com.elderhive.entities.Conversation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    // The pair can appear in either column order - whoever started the conversation first
    // becomes userA, so a lookup from the other side must check both arrangements.
    @Query("""
            SELECT c FROM Conversation c
            WHERE ((c.userAId = :userId1 AND c.userBId = :userId2)
                OR (c.userAId = :userId2 AND c.userBId = :userId1))
            AND (:bookingId IS NULL OR c.bookingId = :bookingId)
            """)
    Optional<Conversation> findBetweenUsers(@Param("userId1") Long userId1,
                                             @Param("userId2") Long userId2,
                                             @Param("bookingId") Long bookingId);

    @Query("""
            SELECT c FROM Conversation c
            WHERE c.userAId = :userId OR c.userBId = :userId
            ORDER BY COALESCE(c.lastMessageAt, c.createdAt) DESC
            """)
    Page<Conversation> findForUser(@Param("userId") Long userId, Pageable pageable);
}
