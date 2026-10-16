package com.elderhive.dtos.PushSubscription;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VapidPublicKeyResponse {

    private String publicKey;
}
