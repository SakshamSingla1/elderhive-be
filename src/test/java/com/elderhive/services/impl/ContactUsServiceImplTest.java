package com.elderhive.services.impl;

import com.elderhive.dtos.ContactUs.ContactUsRequest;
import com.elderhive.dtos.ContactUs.ContactUsResponse;
import com.elderhive.entities.ContactUs;
import com.elderhive.enums.ContactUsStatusEnum;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.repositories.ContactUsRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

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
class ContactUsServiceImplTest {

    @Mock
    private ContactUsRepository contactUsRepository;

    @InjectMocks
    private ContactUsServiceImpl service;

    @Test
    void createAlwaysStartsAsNewRegardlessOfRequestContent() {
        when(contactUsRepository.save(any(ContactUs.class))).thenAnswer(invocation -> {
            ContactUs saved = invocation.getArgument(0);
            saved.setId(1L);
            return saved;
        });

        ContactUsResponse response = service.create(
                ContactUsRequest.builder().name("Jane").email("jane@example.com").message("Hello").build());

        assertThat(response.getStatus()).isEqualTo(ContactUsStatusEnum.NEW);
        assertThat(response.getName()).isEqualTo("Jane");
    }

    @Test
    void searchLowercasesAndWrapsTheSearchTermForTheLikePattern() {
        when(contactUsRepository.findByCriteria(any(), any(), any())).thenReturn(new PageImpl<>(java.util.List.of()));

        service.search("Jane", ContactUsStatusEnum.NEW, PageRequest.of(0, 20));

        ArgumentCaptor<String> patternCaptor = ArgumentCaptor.forClass(String.class);
        verify(contactUsRepository).findByCriteria(patternCaptor.capture(), eq(ContactUsStatusEnum.NEW), any());
        assertThat(patternCaptor.getValue()).isEqualTo("%jane%");
    }

    @Test
    void searchPassesNullPatternWhenNoSearchTermIsGiven() {
        when(contactUsRepository.findByCriteria(isNull(), any(), any())).thenReturn(new PageImpl<>(java.util.List.of()));

        Page<ContactUsResponse> result = service.search(null, null, PageRequest.of(0, 20));

        assertThat(result).isEmpty();
        verify(contactUsRepository).findByCriteria(isNull(), isNull(), any());
    }

    @Test
    void updateStatusThrowsWhenMissing() {
        when(contactUsRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateStatus(99L, ContactUsStatusEnum.CLOSED))
                .isInstanceOf(GenericException.class)
                .extracting(e -> ((GenericException) e).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.CONTACT_US_NOT_FOUND);
    }

    @Test
    void updateStatusChangesTheStatus() throws GenericException {
        ContactUs existing = ContactUs.builder().id(1L).name("Jane").status(ContactUsStatusEnum.NEW).build();
        when(contactUsRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(contactUsRepository.save(any(ContactUs.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ContactUsResponse response = service.updateStatus(1L, ContactUsStatusEnum.RESPONDED);

        assertThat(response.getStatus()).isEqualTo(ContactUsStatusEnum.RESPONDED);
    }

    @Test
    void deleteThrowsWhenMissingAndNeverDeletes() {
        when(contactUsRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(99L))
                .isInstanceOf(GenericException.class)
                .extracting(e -> ((GenericException) e).getExceptionCode())
                .isEqualTo(ExceptionCodeEnum.CONTACT_US_NOT_FOUND);

        verify(contactUsRepository, never()).deleteById(any());
    }
}
