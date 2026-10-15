package com.elderhive.seed;

import com.elderhive.entities.MetaConfig;
import com.elderhive.repositories.MetaConfigRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Seeds two initial MetaConfig contexts (Prompt 36) on every boot — EMERGENCY_SETTINGS
 * (Phase 11's proximity matching will read {@code searchRadiusKm}/
 * {@code escalationTimeoutMinutes} from here) and PLATFORM_SETTINGS ({@code platformName}/
 * {@code supportEmail}/{@code supportPhone}, read wherever the platform needs to display
 * its own identity/contact info).
 *
 * <p><b>Runs via CommandLineRunner, not a Flyway migration</b> — deliberately, for the
 * same reason AdminBootstrapSeeder (Prompt 24) does: a raw SQL INSERT bypasses JPA
 * entirely, so these rows would never get a real createdAt/createdBy (Auditable's JPA
 * auditing only fires for saves that actually go through the EntityManager). Going
 * through MetaConfigRepository here gets that for free, and reuses the exact same
 * check-before-insert idempotency idiom AdminBootstrapSeeder already established in this
 * codebase, rather than inventing a second seeding convention.
 *
 * <p><b>Only ever INSERTS when a context is missing — never touches a context that
 * already exists.</b> These are *default* values, a starting point for the admin UI
 * (Phase 5/11) to edit later; a seeder that unconditionally overwrote them on every
 * restart would silently clobber every admin customization the moment the app redeploys.
 * This is exactly why MetaConfig exists as runtime-editable data in the first place
 * instead of hardcoded constants — seeding it must never re-hardcode it by the back door.
 *
 * <p><b>Every later phase MUST read these two contexts through AdminConfigService</b>
 * (e.g. {@code adminConfigService.getConfig("EMERGENCY_SETTINGS")}), never hardcode
 * searchRadiusKm/escalationTimeoutMinutes/platformName/supportEmail/supportPhone as a
 * literal constant anywhere else — that would defeat the entire point of these being
 * admin-editable MetaConfig rows instead of code.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class MetaConfigSeeder implements CommandLineRunner {

    public static final String EMERGENCY_SETTINGS_CONTEXT = "EMERGENCY_SETTINGS";
    public static final String PLATFORM_SETTINGS_CONTEXT = "PLATFORM_SETTINGS";

    private final MetaConfigRepository metaConfigRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void run(String... args) {
        ensureConfig(EMERGENCY_SETTINGS_CONTEXT, emergencySettingsDefaults());
        ensureConfig(PLATFORM_SETTINGS_CONTEXT, platformSettingsDefaults());
    }

    private Map<String, Object> emergencySettingsDefaults() {
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("searchRadiusKm", 10);
        defaults.put("escalationTimeoutMinutes", 15);
        return defaults;
    }

    private Map<String, Object> platformSettingsDefaults() {
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("platformName", "ElderHive");
        defaults.put("supportEmail", "support@elderhive.app");
        defaults.put("supportPhone", "+1-800-555-0100");
        return defaults;
    }

    private void ensureConfig(String context, Map<String, Object> defaults) {
        if (metaConfigRepository.findByContext(context).isPresent()) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(defaults);
            metaConfigRepository.save(MetaConfig.builder().context(context).data(json).build());
        } catch (JsonProcessingException e) {
            // Defaults above are a fixed, hand-written Map<String, Object> of primitives -
            // this can't actually fail at runtime; logged rather than rethrown so a
            // seeding hiccup here never prevents the whole application from starting.
            log.error("Failed to seed MetaConfig context '{}'", context, e);
        }
    }

    // Exposed for tests/other code that needs the exact seeded keys without re-parsing JSON.
    public static List<String> seededContexts() {
        return List.of(EMERGENCY_SETTINGS_CONTEXT, PLATFORM_SETTINGS_CONTEXT);
    }
}
