package com.elderhive.dtos.MetaConfig;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MetaConfigResponse {

    private Long id;
    private String context;
    private String data;
    private LocalDateTime updatedAt;
    private Long updatedBy;
}
