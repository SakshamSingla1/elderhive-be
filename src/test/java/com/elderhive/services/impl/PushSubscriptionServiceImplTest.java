package com.elderhive.services.impl;

import com.elderhive.dao.push_subscription.PushSubscriptionDao;
import com.elderhive.dtos.PushSubscription.PushSubscriptionKeysDTO;
import com.elderhive.dtos.PushSubscription.PushSubscriptionRequest;
import com.elderhive.entities.PushSubscription;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PushSubscriptionServiceImplTest {

    @Mock
    private PushSubscriptionDao pushSubscriptionDao;

    @InjectMocks
    private PushSubscriptionServiceImpl pushSubscriptionService;

    private PushSubscriptionRequest request(String endpoint, String p256dh, String auth) {
        PushSubscriptionKeysDTO keys = new PushSubscriptionKeysDTO();
        keys.setP256dh(p256dh);
        keys.setAuth(auth);
        PushSubscriptionRequest request = new PushSubscriptionRequest();
        request.setEndpoint(endpoint);
        request.setKeys(keys);
        return request;
    }

    @Test
    void registeringANewEndpointCreatesOneRow() {
        when(pushSubscriptionDao.findByEndpoint("https://push.example.com/ep/1")).thenReturn(Optional.empty());

        pushSubscriptionService.register(1L, request("https://push.example.com/ep/1", "p256dh", "auth"));

        ArgumentCaptor<PushSubscription> captor = ArgumentCaptor.forClass(PushSubscription.class);
        verify(pushSubscriptionDao, times(1)).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(1L);
        assertThat(captor.getValue().getEndpoint()).isEqualTo("https://push.example.com/ep/1");
        assertThat(captor.getValue().getP256dhKey()).isEqualTo("p256dh");
        assertThat(captor.getValue().getAuthKey()).isEqualTo("auth");
    }

    @Test
    void reRegisteringAnAlreadyKnownEndpointOverwritesTheExistingRowRatherThanDuplicatingIt() {
        PushSubscription existing = PushSubscription.builder()
                .id(99L).userId(1L).endpoint("https://push.example.com/ep/1")
                .p256dhKey("old-p256dh").authKey("old-auth").build();
        when(pushSubscriptionDao.findByEndpoint("https://push.example.com/ep/1")).thenReturn(Optional.of(existing));

        pushSubscriptionService.register(2L, request("https://push.example.com/ep/1", "new-p256dh", "new-auth"));

        ArgumentCaptor<PushSubscription> captor = ArgumentCaptor.forClass(PushSubscription.class);
        verify(pushSubscriptionDao, times(1)).save(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(99L);
        assertThat(captor.getValue().getUserId()).isEqualTo(2L);
        assertThat(captor.getValue().getP256dhKey()).isEqualTo("new-p256dh");
        assertThat(captor.getValue().getAuthKey()).isEqualTo("new-auth");
    }

    @Test
    void unsubscribeDelegatesStraightToTheDaoDeleteByUserIdAndEndpoint() {
        pushSubscriptionService.unsubscribe(1L, "https://push.example.com/ep/1");

        verify(pushSubscriptionDao, times(1)).deleteByUserIdAndEndpoint(1L, "https://push.example.com/ep/1");
    }

    @Test
    void unsubscribingAnUnknownEndpointIsANoOpRatherThanAnError() {
        pushSubscriptionService.unsubscribe(1L, "https://push.example.com/ep/unknown");

        verify(pushSubscriptionDao, times(1)).deleteByUserIdAndEndpoint(any(), any());
    }
}
