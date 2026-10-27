package com.elderhive.dtos.Landing;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LandingTestimonialRequest {

    @NotBlank(message = "Author name is required")
    private String authorName;

    private String authorRole;

    @NotBlank(message = "Content is required")
    private String content;

    private String avatarUrl;
    private Integer rating;
    private Integer sortOrder;
    private boolean isActive;
}
