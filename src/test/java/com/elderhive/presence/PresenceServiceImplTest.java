package com.elderhive.presence;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PresenceServiceImplTest {

    @Mock
    private PresenceTracker presenceTracker;

    @InjectMocks
    private PresenceServiceImpl presenceService;

    @Test
    void isOnlineDelegatesToTheTracker() {
        when(presenceTracker.isOnline(42L)).thenReturn(true);

        assertThat(presenceService.isOnline(42L)).isTrue();
    }

    @Test
    void getStatusPreservesTheRequestedOrderAndLooksUpEachUserIndividually() {
        when(presenceTracker.isOnline(1L)).thenReturn(true);
        when(presenceTracker.isOnline(2L)).thenReturn(false);
        when(presenceTracker.isOnline(3L)).thenReturn(true);

        Map<Long, Boolean> status = presenceService.getStatus(List.of(3L, 1L, 2L));

        assertThat(status.keySet()).containsExactly(3L, 1L, 2L);
        assertThat(status).containsEntry(1L, true).containsEntry(2L, false).containsEntry(3L, true);
    }

    @Test
    void heartbeatDelegatesToTheTracker() {
        presenceService.heartbeat(42L);

        verify(presenceTracker).heartbeat(42L);
    }
}
