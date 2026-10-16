package com.elderhive.dtos.PushSubscription;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class PushSubscriptionKeysDTO {

    @NotBlank(message = "p256dh key is required")
    private String p256dh;

    @NotBlank(message = "auth key is required")
    private String auth;
}
