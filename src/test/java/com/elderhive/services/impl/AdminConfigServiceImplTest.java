package com.elderhive.services.impl;

import com.elderhive.dtos.MetaConfig.MetaConfigRequest;
import com.elderhive.dtos.MetaConfig.MetaConfigResponse;
import com.elderhive.entities.MetaConfig;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.repositories.MetaConfigRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminConfigServiceImplTest {

    @Mock
    private MetaConfigRepository metaConfigRepository;

    @InjectMocks
    private AdminConfigServiceImpl adminConfigService;

    @Test
    void getConfigReturnsTheMatchingRow() throws GenericException {
        MetaConfig config = MetaConfig.builder().id(1L).context("ANNOUNCEMENT_BANNER").data("{\"text\":\"hi\"}").build();
        when(metaConfigRepository.findByContext("ANNOUNCEMENT_BANNER")).thenReturn(Optional.of(config));

        MetaConfigResponse response = adminConfigService.getConfig("ANNOUNCEMENT_BANNER");

        assertThat(response.getContext()).isEqualTo("ANNOUNCEMENT_BANNER");
        assertThat(response.getData()).isEqualTo("{\"text\":\"hi\"}");
    }

    @Test
    void getConfigThrowsDataNotFoundForAnUnknownContext() {
        when(metaConfigRepository.findByContext("UNKNOWN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminConfigService.getConfig("UNKNOWN"))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.DATA_NOT_FOUND);
    }

    @Test
    void listConfigsReturnsEveryRowOrderedByContext() {
        when(metaConfigRepository.findAllByOrderByContextAsc()).thenReturn(List.of(
                MetaConfig.builder().id(1L).context("A_CONTEXT").data("{}").build(),
                MetaConfig.builder().id(2L).context("B_CONTEXT").data("{}").build()));

        List<MetaConfigResponse> result = adminConfigService.listConfigs();

        assertThat(result).extracting(MetaConfigResponse::getContext).containsExactly("A_CONTEXT", "B_CONTEXT");
    }

    @Test
    void upsertCreatesANewRowWhenTheContextDoesNotExistYet() throws GenericException {
        when(metaConfigRepository.findByContext("NEW_CONTEXT")).thenReturn(Optional.empty());
        when(metaConfigRepository.save(any(MetaConfig.class))).thenAnswer(invocation -> {
            MetaConfig saved = invocation.getArgument(0);
            saved.setId(5L);
            return saved;
        });

        MetaConfigResponse response = adminConfigService.upsertConfig("NEW_CONTEXT", MetaConfigRequest.builder().data("{\"a\":1}").build());

        assertThat(response.getId()).isEqualTo(5L);
        assertThat(response.getContext()).isEqualTo("NEW_CONTEXT");
        assertThat(response.getData()).isEqualTo("{\"a\":1}");

        ArgumentCaptor<MetaConfig> captor = ArgumentCaptor.forClass(MetaConfig.class);
        verify(metaConfigRepository).save(captor.capture());
        assertThat(captor.getValue().getContext()).isEqualTo("NEW_CONTEXT");
    }

    @Test
    void upsertUpdatesTheExistingRowInPlaceRatherThanCreatingADuplicate() throws GenericException {
        MetaConfig existing = MetaConfig.builder().id(7L).context("EXISTING_CONTEXT").data("{\"old\":true}").build();
        when(metaConfigRepository.findByContext("EXISTING_CONTEXT")).thenReturn(Optional.of(existing));
        when(metaConfigRepository.save(any(MetaConfig.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MetaConfigResponse response = adminConfigService.upsertConfig("EXISTING_CONTEXT", MetaConfigRequest.builder().data("{\"new\":true}").build());

        assertThat(response.getId()).isEqualTo(7L); // same row, not a new id
        assertThat(response.getData()).isEqualTo("{\"new\":true}");
    }

    @Test
    void upsertRejectsABlankContext() {
        assertThatThrownBy(() -> adminConfigService.upsertConfig("   ", MetaConfigRequest.builder().data("{}").build()))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.BAD_REQUEST);

        verify(metaConfigRepository, never()).save(any());
    }

    @Test
    void deleteRemovesTheMatchingRow() throws GenericException {
        MetaConfig config = MetaConfig.builder().id(1L).context("TO_DELETE").data("{}").build();
        when(metaConfigRepository.findByContext("TO_DELETE")).thenReturn(Optional.of(config));

        adminConfigService.deleteConfig("TO_DELETE");

        verify(metaConfigRepository).delete(config);
    }

    @Test
    void deleteThrowsDataNotFoundForAnUnknownContext() {
        when(metaConfigRepository.findByContext("UNKNOWN")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminConfigService.deleteConfig("UNKNOWN"))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.DATA_NOT_FOUND);

        verify(metaConfigRepository, never()).delete(any(MetaConfig.class));
    }
}
