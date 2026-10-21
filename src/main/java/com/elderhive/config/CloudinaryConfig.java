package com.elderhive.config;

import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Ported from ElderSphere's own CloudinaryConfig. Builds the Cloudinary SDK client at
 * startup (if configured) and exposes {@link #isConfigured()} so FileStorageServiceImpl can
 * fall back to local-disk storage - rather than throwing an opaque Cloudinary SDK error -
 * when Cloudinary isn't set up.
 */
@Slf4j
@Component
public class CloudinaryConfig {

    @Getter
    private final String cloudName;

    private final String apiKey;
    private final String apiSecret;

    @Getter
    private Cloudinary cloudinary;

    public CloudinaryConfig(
            @Value("${cloudinary.cloud-name:}") String cloudName,
            @Value("${cloudinary.api-key:}") String apiKey,
            @Value("${cloudinary.api-secret:}") String apiSecret) {
        this.cloudName = cloudName;
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
    }

    @PostConstruct
    public void init() {
        if (!isConfigured()) {
            log.warn("Cloudinary: no CLOUDINARY_CLOUD_NAME/API_KEY/API_SECRET configured - " +
                    "file uploads will fall back to local disk storage until it's set " +
                    "(get credentials at https://console.cloudinary.com).");
            return;
        }
        cloudinary = new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cloudName,
                "api_key", apiKey,
                "api_secret", apiSecret,
                "secure", true
        ));
        log.info("Cloudinary: configured for cloud '{}'.", cloudName);
    }

    public boolean isConfigured() {
        return StringUtils.hasText(cloudName) && StringUtils.hasText(apiKey) && StringUtils.hasText(apiSecret);
    }
}
