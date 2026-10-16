package com.elderhive.dtos.PushSubscription;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PushUnsubscribeRequest {

    @NotBlank(message = "Endpoint is required")
    private String endpoint;
}
