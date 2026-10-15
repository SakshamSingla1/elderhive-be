package com.elderhive.repositories;

import com.elderhive.entities.NotificationTemplate;
import com.elderhive.entities.NotificationTemplateVariables;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs against the real local Postgres (docker-compose.yml) — confirms V11's migration
 * actually matches the entity mapping (ddl-auto=validate would fail fast if not), the
 * unique constraint on template_key, JPA auditing populating createdAt/updatedAt for
 * real, and the template_id FK's ON DELETE CASCADE onto NotificationTemplateVariables.
 * No repository exists yet for NotificationTemplateVariables (deliberately deferred, see
 * its own javadoc), so this uses the EntityManager directly for it — same approach
 * UserThemeMappingRepositoryTest used for ColorTheme before Prompt 42 gave it one.
 */
@SpringBootTest
@Transactional
class NotificationTemplateRepositoryTest {

    @Autowired
    private NotificationTemplateRepository notificationTemplateRepository;

    @Autowired
    private EntityManager entityManager;

    private NotificationTemplate persistTemplate(String templateKey) {
        return notificationTemplateRepository.save(NotificationTemplate.builder()
                .templateKey(templateKey)
                .subject("Hi {{name}}")
                .messageBody("Hello {{name}}, welcome!")
                .isInApp(true)
                .isEmail(true)
                .build());
    }

    @Test
    void persistingAndReloadingATemplatePopulatesAuditFieldsAndEveryColumn() {
        NotificationTemplate saved = persistTemplate("round-trip-template");
        entityManager.flush();
        entityManager.clear();

        NotificationTemplate reloaded = notificationTemplateRepository.findById(saved.getId()).orElseThrow();

        assertThat(reloaded.getTemplateKey()).isEqualTo("round-trip-template");
        assertThat(reloaded.getSubject()).isEqualTo("Hi {{name}}");
        assertThat(reloaded.getMessageBody()).isEqualTo("Hello {{name}}, welcome!");
        assertThat(reloaded.isInApp()).isTrue();
        assertThat(reloaded.isEmail()).isTrue();
        assertThat(reloaded.isSms()).isFalse();
        assertThat(reloaded.isWebPush()).isFalse();
        assertThat(reloaded.getCreatedAt()).isNotNull();
        assertThat(reloaded.getUpdatedAt()).isNotNull();
    }

    @Test
    void findByTemplateKeyFindsAnExistingTemplateAndIsEmptyForAnUnknownOne() {
        persistTemplate("findable-template");
        entityManager.flush();

        assertThat(notificationTemplateRepository.findByTemplateKey("findable-template")).isPresent();
        assertThat(notificationTemplateRepository.findByTemplateKey("no-such-template")).isEmpty();
    }

    @Test
    void templateKeyMustBeUnique() {
        persistTemplate("duplicate-key");

        assertThatThrownBy(() -> {
            notificationTemplateRepository.save(NotificationTemplate.builder().templateKey("duplicate-key").build());
            notificationTemplateRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void deletingATemplateCascadesToItsVariableBlocks() {
        NotificationTemplate template = persistTemplate("template-with-variables");
        NotificationTemplateVariables variable = NotificationTemplateVariables.builder()
                .templateId(template.getId())
                .variableName("header")
                .htmlContent("<div>Header</div>")
                .build();
        entityManager.persist(variable);
        entityManager.flush();
        Long variableId = variable.getId();

        notificationTemplateRepository.delete(template);
        entityManager.flush();
        entityManager.clear();

        assertThat(entityManager.find(NotificationTemplateVariables.class, variableId)).isNull();
    }
}
