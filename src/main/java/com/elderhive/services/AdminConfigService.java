package com.elderhive.services;

import com.elderhive.dtos.MetaConfig.MetaConfigRequest;
import com.elderhive.dtos.MetaConfig.MetaConfigResponse;
import com.elderhive.exceptions.GenericException;

import java.util.List;

public interface AdminConfigService {

    MetaConfigResponse getConfig(String context) throws GenericException;

    List<MetaConfigResponse> listConfigs();

    /** Upsert: creates the row the first time a context is saved, updates it every time
     *  after — never throws a conflict, the whole point of upsert-by-context semantics. */
    MetaConfigResponse upsertConfig(String context, MetaConfigRequest request) throws GenericException;

    void deleteConfig(String context) throws GenericException;
}
