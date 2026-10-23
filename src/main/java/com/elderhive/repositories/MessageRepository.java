package com.elderhive.repositories;

import com.elderhive.entities.Message;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface MessageRepository extends JpaRepository<Message, Long> {

    Page<Message> findByConversationIdOrderBySentAtAsc(Long conversationId, Pageable pageable);

    long countByConversationIdAndSenderIdNotAndReadAtIsNull(Long conversationId, Long senderId);

    // Marks everything the OTHER participant sent as read - never the caller's own messages.
    @Modifying
    @Query("""
            UPDATE Message m SET m.readAt = :now
            WHERE m.conversationId = :conversationId AND m.senderId <> :userId AND m.readAt IS NULL
            """)
    int markConversationRead(@Param("conversationId") Long conversationId,
                              @Param("userId") Long userId,
                              @Param("now") LocalDateTime now);
}
