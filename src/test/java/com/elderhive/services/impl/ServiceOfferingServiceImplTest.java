package com.elderhive.services.impl;

import com.elderhive.dtos.ServiceOffering.ServiceOfferingRequest;
import com.elderhive.dtos.ServiceOffering.ServiceOfferingResponse;
import com.elderhive.entities.ServiceOffering;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.enums.ServiceOfferingStatusEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.repositories.ServiceCategoryRepository;
import com.elderhive.repositories.ServiceOfferingRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceOfferingServiceImplTest {

    @Mock
    private ServiceOfferingRepository serviceOfferingRepository;

    @Mock
    private ServiceCategoryRepository serviceCategoryRepository;

    @InjectMocks
    private ServiceOfferingServiceImpl service;

    private ServiceOffering offering(Long id, String name, Long categoryId) {
        return ServiceOffering.builder().id(id).name(name).categoryId(categoryId).status(ServiceOfferingStatusEnum.ACTIVE).build();
    }

    private ServiceOfferingRequest request(String name, Long categoryId) {
        return ServiceOfferingRequest.builder().name(name).categoryId(categoryId)
                .description("desc").basePrice(new BigDecimal("50.00")).durationMinutes(60).build();
    }

    @Test
    void creatingAnOfferingUnderAnExistingCategorySavesItDefaultingStatusToActive() throws GenericException {
        when(serviceCategoryRepository.existsById(1L)).thenReturn(true);
        when(serviceOfferingRepository.save(any())).thenAnswer(inv -> {
            ServiceOffering o = inv.getArgument(0);
            o.setId(1L);
            return o;
        });

        ServiceOfferingResponse response = service.create(request("Temple Visit Service", 1L));

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo("Temple Visit Service");
        assertThat(response.getCategoryId()).isEqualTo(1L);
        assertThat(response.getStatus()).isEqualTo(ServiceOfferingStatusEnum.ACTIVE);
    }

    @Test
    void creatingAnOfferingUnderAnUnknownCategoryThrowsServiceCategoryNotFound() {
        when(serviceCategoryRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> service.create(request("X", 999L)))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.SERVICE_CATEGORY_NOT_FOUND);

        verify(serviceOfferingRepository, never()).save(any());
    }

    @Test
    void updatingAnOfferingToADifferentExistingCategorySucceeds() throws GenericException {
        when(serviceOfferingRepository.findById(1L)).thenReturn(Optional.of(offering(1L, "Old Name", 1L)));
        when(serviceCategoryRepository.existsById(2L)).thenReturn(true);
        when(serviceOfferingRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ServiceOfferingResponse response = service.update(1L, request("New Name", 2L));

        assertThat(response.getName()).isEqualTo("New Name");
        assertThat(response.getCategoryId()).isEqualTo(2L);
    }

    @Test
    void updatingAnUnknownOfferingThrowsNotFound() {
        when(serviceOfferingRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(999L, request("X", 1L)))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.SERVICE_OFFERING_NOT_FOUND);
    }

    @Test
    void getByIdThrowsNotFoundForAnUnknownId() {
        when(serviceOfferingRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(999L))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.SERVICE_OFFERING_NOT_FOUND);
    }

    @Test
    void getAllReturnsEveryOffering() {
        when(serviceOfferingRepository.findAll(any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(offering(1L, "Temple Visit Service", 1L))));

        var page = service.getAll(PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);
    }

    @Test
    void deletingAKnownOfferingRemovesIt() throws GenericException {
        ServiceOffering existing = offering(1L, "Temple Visit Service", 1L);
        when(serviceOfferingRepository.findById(1L)).thenReturn(Optional.of(existing));

        service.delete(1L);

        verify(serviceOfferingRepository).delete(existing);
    }

    @Test
    void deletingAnUnknownOfferingThrowsNotFound() {
        when(serviceOfferingRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(999L))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.SERVICE_OFFERING_NOT_FOUND);
    }
}
