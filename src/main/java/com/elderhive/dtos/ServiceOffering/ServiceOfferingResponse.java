package com.elderhive.dtos.ServiceOffering;

import com.elderhive.enums.ServiceOfferingStatusEnum;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServiceOfferingResponse {

    private Long id;
    private String name;
    private Long categoryId;
    private String categoryName;
    private String description;
    private BigDecimal basePrice;
    private Integer durationMinutes;
    private ServiceOfferingStatusEnum status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
