package com.elderhive.services.impl;

import com.elderhive.dtos.ServiceCategory.ServiceCategoryRequest;
import com.elderhive.dtos.ServiceCategory.ServiceCategoryResponse;
import com.elderhive.entities.ServiceCategory;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.enums.ServiceCategoryStatusEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.repositories.ServiceCategoryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceCategoryServiceImplTest {

    @Mock
    private ServiceCategoryRepository serviceCategoryRepository;

    @InjectMocks
    private ServiceCategoryServiceImpl service;

    private ServiceCategory category(Long id, String name, ServiceCategoryStatusEnum status) {
        return ServiceCategory.builder().id(id).name(name).status(status).build();
    }

    private ServiceCategoryRequest request(String name, ServiceCategoryStatusEnum status) {
        return ServiceCategoryRequest.builder().name(name).description("desc").icon("icon").status(status).build();
    }

    @Test
    void creatingANewCategorySavesItDefaultingStatusToActiveWhenOmitted() throws GenericException {
        when(serviceCategoryRepository.findByName("Temple Visit")).thenReturn(Optional.empty());
        when(serviceCategoryRepository.save(any())).thenAnswer(inv -> {
            ServiceCategory c = inv.getArgument(0);
            c.setId(1L);
            return c;
        });

        ServiceCategoryResponse response = service.create(request("Temple Visit", null));

        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getName()).isEqualTo("Temple Visit");
        assertThat(response.getStatus()).isEqualTo(ServiceCategoryStatusEnum.ACTIVE);
    }

    @Test
    void creatingACategoryWithAnAlreadyTakenNameThrowsConflict() {
        when(serviceCategoryRepository.findByName("Temple Visit")).thenReturn(Optional.of(category(1L, "Temple Visit", ServiceCategoryStatusEnum.ACTIVE)));

        assertThatThrownBy(() -> service.create(request("Temple Visit", null)))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.SERVICE_CATEGORY_ALREADY_EXISTS);

        verify(serviceCategoryRepository, never()).save(any());
    }

    @Test
    void updatingACategoryKeepingItsOwnNameDoesNotConflictWithItself() throws GenericException {
        ServiceCategory existing = category(1L, "Temple Visit", ServiceCategoryStatusEnum.ACTIVE);
        when(serviceCategoryRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(serviceCategoryRepository.findByName("Temple Visit")).thenReturn(Optional.of(existing));
        when(serviceCategoryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ServiceCategoryResponse response = service.update(1L, request("Temple Visit", ServiceCategoryStatusEnum.INACTIVE));

        assertThat(response.getStatus()).isEqualTo(ServiceCategoryStatusEnum.INACTIVE);
    }

    @Test
    void renamingACategoryToAnotherCategorysNameThrowsConflict() {
        ServiceCategory existing = category(1L, "Temple Visit", ServiceCategoryStatusEnum.ACTIVE);
        ServiceCategory other = category(2L, "Hospital Visit", ServiceCategoryStatusEnum.ACTIVE);
        when(serviceCategoryRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(serviceCategoryRepository.findByName("Hospital Visit")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.update(1L, request("Hospital Visit", null)))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.SERVICE_CATEGORY_ALREADY_EXISTS);

        verify(serviceCategoryRepository, never()).save(any());
    }

    @Test
    void updatingAnUnknownCategoryThrowsNotFound() {
        when(serviceCategoryRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(999L, request("X", null)))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.SERVICE_CATEGORY_NOT_FOUND);
    }

    @Test
    void getByIdThrowsNotFoundForAnUnknownId() {
        when(serviceCategoryRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getById(999L))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.SERVICE_CATEGORY_NOT_FOUND);
    }

    @Test
    void getAllReturnsEveryCategoryWhenNoStatusFilterIsGiven() {
        when(serviceCategoryRepository.findByCriteria(isNull(), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(category(1L, "Temple Visit", ServiceCategoryStatusEnum.ACTIVE))));

        var page = service.getAll(null, PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);
    }

    @Test
    void getAllFiltersByStatusWhenGiven() {
        when(serviceCategoryRepository.findByCriteria(eq(ServiceCategoryStatusEnum.ACTIVE), any(org.springframework.data.domain.Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(category(1L, "Temple Visit", ServiceCategoryStatusEnum.ACTIVE))));

        var page = service.getAll(ServiceCategoryStatusEnum.ACTIVE, PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);
        verify(serviceCategoryRepository).findByCriteria(eq(ServiceCategoryStatusEnum.ACTIVE), any());
    }

    @Test
    void deletingAKnownCategoryRemovesIt() throws GenericException {
        ServiceCategory existing = category(1L, "Temple Visit", ServiceCategoryStatusEnum.ACTIVE);
        when(serviceCategoryRepository.findById(1L)).thenReturn(Optional.of(existing));

        service.delete(1L);

        verify(serviceCategoryRepository).delete(existing);
    }

    @Test
    void deletingAnUnknownCategoryThrowsNotFound() {
        when(serviceCategoryRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(999L))
                .isInstanceOf(GenericException.class)
                .extracting(ex -> ((GenericException) ex).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.SERVICE_CATEGORY_NOT_FOUND);
    }
}
