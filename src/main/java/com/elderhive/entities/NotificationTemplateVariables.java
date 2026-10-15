package com.elderhive.entities;

import com.elderhive.audit.Auditable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/**
 * Named, reusable HTML blocks scoped to one NotificationTemplate (templateId — a plain FK
 * column, not a JPA relationship, matching the rest of this codebase's convention, e.g.
 * User.roleId / UserThemeMapping.themeId) — e.g. a shared email header/footer/button
 * snippet a template author can compose into messageBody by name. Mirrors Portfolio's/
 * sb_backend's NotificationTemplateVariables shape (variableName/htmlContent/templateId)
 * minus whatsappVariable, which has no equivalent here (no WhatsApp channel).
 *
 * <p>Genuinely checked against both reference implementations before building this:
 * neither Portfolio's nor sb_backend's actual render/send code path (see Portfolio's
 * NTServiceImpl#sendNotification) ever reads these rows back in to splice into a
 * send — they're a separately CRUD-managed catalog only (Portfolio's NTService exposes
 * getVariablesByCriteria for browsing/management, nothing more). TemplateRenderer
 * (Prompt 52) deliberately does the same: it only substitutes the caller-supplied
 * variables map, never auto-merges these rows in. No repository exists yet for this
 * entity — deferred until a later phase actually needs to query it (the same call Prompt
 * 40 made for ColorTheme, whose own repository didn't land until Prompt 42).
 */
@Entity
@Table(name = "notification_template_variables")
@Data
@EqualsAndHashCode(callSuper = false)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationTemplateVariables extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "template_id", nullable = false)
    private Long templateId;

    @Column(name = "variable_name", nullable = false)
    private String variableName;

    @Column(name = "html_content", columnDefinition = "TEXT")
    private String htmlContent;
}
