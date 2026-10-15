package com.elderhive.seed;

import com.elderhive.entities.MetaConfig;
import com.elderhive.repositories.MetaConfigRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs against the real local Postgres (docker-compose.yml) — the seeder already ran once
 * automatically as a CommandLineRunner during this test class's own context startup (same
 * caveat AdminBootstrapSeederTest documents), so the explicit run() calls below are IN
 * ADDITION to that — exactly the point, proving idempotency regardless of how many times
 * it's actually invoked.
 */
@SpringBootTest
@Transactional
class MetaConfigSeederTest {

    @Autowired
    private MetaConfigSeeder metaConfigSeeder;

    @Autowired
    private MetaConfigRepository metaConfigRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void seedsBothContextsWithTheirExpectedDefaultKeys() throws Exception {
        metaConfigSeeder.run();

        MetaConfig emergencySettings = metaConfigRepository.findByContext(MetaConfigSeeder.EMERGENCY_SETTINGS_CONTEXT).orElseThrow();
        JsonNode emergencyData = objectMapper.readTree(emergencySettings.getData());
        assertThat(emergencyData.get("searchRadiusKm").asInt()).isEqualTo(10);
        assertThat(emergencyData.get("escalationTimeoutMinutes").asInt()).isEqualTo(15);

        MetaConfig platformSettings = metaConfigRepository.findByContext(MetaConfigSeeder.PLATFORM_SETTINGS_CONTEXT).orElseThrow();
        JsonNode platformData = objectMapper.readTree(platformSettings.getData());
        assertThat(platformData.get("platformName").asText()).isEqualTo("ElderHive");
        assertThat(platformData.get("supportEmail").asText()).isEqualTo("support@elderhive.app");
        assertThat(platformData.get("supportPhone").asText()).isEqualTo("+1-800-555-0100");
    }

    @Test
    void runningTheSeederRepeatedlyNeverDuplicatesEitherContext() {
        metaConfigSeeder.run();
        long countAfterFirst = metaConfigRepository.count();

        metaConfigSeeder.run();
        metaConfigSeeder.run();

        assertThat(metaConfigRepository.count()).isEqualTo(countAfterFirst);
        assertThat(metaConfigRepository.findByContext(MetaConfigSeeder.EMERGENCY_SETTINGS_CONTEXT)).isPresent();
        assertThat(metaConfigRepository.findByContext(MetaConfigSeeder.PLATFORM_SETTINGS_CONTEXT)).isPresent();
    }

    @Test
    void neverOverwritesAContextThatAnAdminAlreadyCustomized() {
        metaConfigSeeder.run();
        MetaConfig emergencySettings = metaConfigRepository.findByContext(MetaConfigSeeder.EMERGENCY_SETTINGS_CONTEXT).orElseThrow();

        // Simulates an admin editing this context via AdminConfigService after the first boot.
        emergencySettings.setData("{\"searchRadiusKm\":25,\"escalationTimeoutMinutes\":5}");
        metaConfigRepository.save(emergencySettings);

        metaConfigSeeder.run();

        MetaConfig reloaded = metaConfigRepository.findByContext(MetaConfigSeeder.EMERGENCY_SETTINGS_CONTEXT).orElseThrow();
        assertThat(reloaded.getData()).isEqualTo("{\"searchRadiusKm\":25,\"escalationTimeoutMinutes\":5}");
    }
}
