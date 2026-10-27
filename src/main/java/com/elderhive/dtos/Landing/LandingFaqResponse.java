package com.elderhive.dtos.Landing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LandingFaqResponse {

    private Long id;
    private String question;
    private String answer;
    private Integer sortOrder;
    private boolean isActive;
}
