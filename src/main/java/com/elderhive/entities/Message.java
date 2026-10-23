package com.elderhive.entities;

import com.elderhive.audit.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Mirrors ~/ElderSphere/eldersphere-be's Message field-for-field. {@code fileUrl} is
 * resolved once (FileAssetService#getById) and persisted at send time rather than re-derived
 * on every read - unlike Review.photoFileAssetId (resolved fresh on every read via
 * FileAssetService), a chat history page can render hundreds of messages at once, so this
 * avoids N FileAssetService lookups per page load. {@code readAt} is a single column (not a
 * per-participant receipt) because Prompt 89's Conversation is strictly 1:1 - a message has
 * exactly one possible "other" reader.
 */
@Entity
@Table(name = "messages")
@Data
@EqualsAndHashCode(callSuper = false)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Message extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "conversation_id", nullable = false)
    private Long conversationId;

    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    // Nullable - an attachment-only message (fileAssetId set, no text) is valid, same
    // "content or attachment, not necessarily both" rule MessagingServiceImpl#sendMessage
    // enforces.
    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "sent_at", nullable = false)
    private LocalDateTime sentAt;

    @Column(name = "read_at")
    private LocalDateTime readAt;

    @Column(name = "file_asset_id")
    private Long fileAssetId;

    @Column(name = "file_url", length = 500)
    private String fileUrl;
}
