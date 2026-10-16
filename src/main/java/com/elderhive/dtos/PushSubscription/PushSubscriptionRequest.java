package com.elderhive.dtos.PushSubscription;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PushSubscriptionRequest {

    @NotBlank(message = "Endpoint is required")
    private String endpoint;

    @NotNull(message = "Keys are required")
    @Valid
    private PushSubscriptionKeysDTO keys;
}
