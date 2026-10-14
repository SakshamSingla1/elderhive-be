package com.elderhive.services;

import com.elderhive.dtos.NavLink.NavLinkRequest;
import com.elderhive.dtos.NavLink.NavLinkResponse;
import com.elderhive.entities.User;
import com.elderhive.exceptions.GenericException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface NavLinkService {

    NavLinkResponse create(NavLinkRequest request) throws GenericException;

    NavLinkResponse update(Long id, NavLinkRequest request) throws GenericException;

    NavLinkResponse getById(Long id) throws GenericException;

    Page<NavLinkResponse> getAll(Pageable pageable);

    void delete(Long id) throws GenericException;

    /** Resolves the caller's own visible sidebar — see NavLinkServiceImpl#getMine for
     *  exactly how SUPER_ADMIN/ADMIN/everyone-else differ. */
    List<NavLinkResponse> getMine(User caller);
}
