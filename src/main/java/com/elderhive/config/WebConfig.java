package com.elderhive.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.File;

/**
 * Serves locally-stored uploads directly off disk at /uploads/** when Cloudinary isn't
 * configured (see FileStorageServiceImpl). Unlike ElderSphere's equivalent handler, this
 * path is deliberately left OUT of SecurityConfig's permitAll allowlist - verification
 * documents are sensitive PII, so a static /uploads/xyz.pdf request still has to pass
 * through the normal JWT cookie auth (Spring resource handlers are dispatched through the
 * same filter chain, not bypassed by it) rather than being world-readable.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.file-storage.upload-dir}")
    private String uploadDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = "file:" + new File(uploadDir).getAbsolutePath() + File.separator;
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(location);
    }
}
