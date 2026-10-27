package com.elderhive.dtos.Landing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LandingTestimonialResponse {

    private Long id;
    private String authorName;
    private String authorRole;
    private String content;
    private String avatarUrl;
    private Integer rating;
    private Integer sortOrder;
    private boolean isActive;
}
