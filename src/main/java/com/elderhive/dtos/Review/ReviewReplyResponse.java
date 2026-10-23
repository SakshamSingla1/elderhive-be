package com.elderhive.dtos.Review;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewReplyResponse {

    private Long id;
    private Long reviewId;
    private Long caretakerId;
    private String content;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
