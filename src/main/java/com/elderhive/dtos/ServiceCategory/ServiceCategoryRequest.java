package com.elderhive.dtos.ServiceCategory;

import com.elderhive.enums.ServiceCategoryStatusEnum;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceCategoryRequest {

    @NotBlank(message = "Name is required")
    private String name;

    private String description;

    private String icon;

    private ServiceCategoryStatusEnum status;
}
