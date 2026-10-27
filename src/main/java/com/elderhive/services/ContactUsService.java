package com.elderhive.services;

import com.elderhive.dtos.ContactUs.ContactUsRequest;
import com.elderhive.dtos.ContactUs.ContactUsResponse;
import com.elderhive.enums.ContactUsStatusEnum;
import com.elderhive.exceptions.GenericException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface ContactUsService {

    /** Public, unauthenticated submission - always starts as NEW. */
    ContactUsResponse create(ContactUsRequest request);

    /** Admin triage queue - CONTACT_US_MANAGE-gated at the controller. */
    Page<ContactUsResponse> search(String search, ContactUsStatusEnum status, Pageable pageable);

    ContactUsResponse updateStatus(Long id, ContactUsStatusEnum status) throws GenericException;

    void delete(Long id) throws GenericException;
}
