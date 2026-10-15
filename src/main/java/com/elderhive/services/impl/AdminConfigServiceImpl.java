package com.elderhive.services.impl;

import com.elderhive.dtos.MetaConfig.MetaConfigRequest;
import com.elderhive.dtos.MetaConfig.MetaConfigResponse;
import com.elderhive.entities.MetaConfig;
import com.elderhive.enums.ExceptionCodeEnum;
import com.elderhive.exceptions.GenericException;
import com.elderhive.repositories.MetaConfigRepository;
import com.elderhive.services.AdminConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class AdminConfigServiceImpl implements AdminConfigService {

    private final MetaConfigRepository metaConfigRepository;

    @Override
    public MetaConfigResponse getConfig(String context) throws GenericException {
        return toResponse(findOrThrow(context));
    }

    @Override
    public List<MetaConfigResponse> listConfigs() {
        return metaConfigRepository.findAllByOrderByContextAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public MetaConfigResponse upsertConfig(String context, MetaConfigRequest request) throws GenericException {
        if (context == null || context.isBlank()) {
            throw new GenericException(ExceptionCodeEnum.BAD_REQUEST, "context is required.");
        }

        MetaConfig config = metaConfigRepository.findByContext(context)
                .orElseGet(() -> MetaConfig.builder().context(context).build());
        config.setData(request.getData());
        return toResponse(metaConfigRepository.save(config));
    }

    @Override
    @Transactional
    public void deleteConfig(String context) throws GenericException {
        metaConfigRepository.delete(findOrThrow(context));
    }

    private MetaConfig findOrThrow(String context) throws GenericException {
        return metaConfigRepository.findByContext(context)
                .orElseThrow(() -> new GenericException(ExceptionCodeEnum.DATA_NOT_FOUND,
                        "No config with context '" + context + "'."));
    }

    private MetaConfigResponse toResponse(MetaConfig config) {
        return MetaConfigResponse.builder()
                .id(config.getId())
                .context(config.getContext())
                .data(config.getData())
                .updatedAt(config.getUpdatedAt())
                .updatedBy(config.getUpdatedBy())
                .build();
    }
}
