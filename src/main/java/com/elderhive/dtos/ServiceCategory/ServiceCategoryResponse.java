package com.elderhive.dtos.ServiceCategory;

import com.elderhive.enums.ServiceCategoryStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceCategoryResponse {

    private Long id;
    private String name;
    private String description;
    private String icon;
    private ServiceCategoryStatusEnum status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
