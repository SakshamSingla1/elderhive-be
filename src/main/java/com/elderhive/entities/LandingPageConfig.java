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
 * A single-row table - the public landing page's hero/CTA copy (Prompt 99). No natural
 * key beyond "whichever row exists"; LandingPageRepository#findFirstByOrderByIdAsc plus
 * LandingPageServiceImpl#updateConfig's own upsert-on-first-save logic are what actually
 * enforce there only ever being one, not a DB constraint.
 */
@Entity
@Table(name = "landing_page_config")
@Data
@EqualsAndHashCode(callSuper = false)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LandingPageConfig extends Auditable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "hero_headline")
    private String heroHeadline;

    @Column(name = "hero_subheadline", columnDefinition = "TEXT")
    private String heroSubheadline;

    @Column(name = "hero_image_url")
    private String heroImageUrl;

    @Column(name = "cta_headline")
    private String ctaHeadline;

    @Column(name = "cta_description", columnDefinition = "TEXT")
    private String ctaDescription;

    @Column(name = "cta_button_text")
    private String ctaButtonText;
}
