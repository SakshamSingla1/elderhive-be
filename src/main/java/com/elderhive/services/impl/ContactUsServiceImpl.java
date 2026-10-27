package com.elderhive.services.impl;

import com.elderhive.dtos.ContactUs.ContactUsRequest;
import com.elderhive.dtos.ContactUs.ContactUsResponse;
import com.elderhive.entities.ContactUs;
import com.elderhive.enums.ContactUsStatusEnum;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.repositories.ContactUsRepository;
import com.elderhive.services.ContactUsService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ContactUsServiceImpl implements ContactUsService {

    private final ContactUsRepository contactUsRepository;

    @Override
    @Transactional
    public ContactUsResponse create(ContactUsRequest request) {
        ContactUs contactUs = ContactUs.builder()
                .name(request.getName())
                .email(request.getEmail())
                .phone(request.getPhone())
                .message(request.getMessage())
                .status(ContactUsStatusEnum.NEW)
                .build();
        return toResponse(contactUsRepository.save(contactUs));
    }

    @Override
    public Page<ContactUsResponse> search(String search, ContactUsStatusEnum status, Pageable pageable) {
        String pattern = (search != null && !search.isBlank()) ? "%" + search.trim().toLowerCase() + "%" : null;
        return contactUsRepository.findByCriteria(pattern, status, pageable).map(this::toResponse);
    }

    @Override
    @Transactional
    public ContactUsResponse updateStatus(Long id, ContactUsStatusEnum status) throws GenericException {
        ContactUs contactUs = findOrThrow(id);
        contactUs.setStatus(status);
        return toResponse(contactUsRepository.save(contactUs));
    }

    @Override
    @Transactional
    public void delete(Long id) throws GenericException {
        findOrThrow(id);
        contactUsRepository.deleteById(id);
    }

    private ContactUs findOrThrow(Long id) throws GenericException {
        return contactUsRepository.findById(id)
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.CONTACT_US_NOT_FOUND, "Contact request not found"));
    }

    private ContactUsResponse toResponse(ContactUs contactUs) {
        return ContactUsResponse.builder()
                .id(contactUs.getId())
                .name(contactUs.getName())
                .email(contactUs.getEmail())
                .phone(contactUs.getPhone())
                .message(contactUs.getMessage())
                .status(contactUs.getStatus())
                .createdAt(contactUs.getCreatedAt())
                .updatedAt(contactUs.getUpdatedAt())
                .build();
    }
}
