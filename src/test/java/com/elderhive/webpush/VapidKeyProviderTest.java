package com.elderhive.webpush;

import nl.martijndwars.webpush.Utils;
import org.junit.jupiter.api.Test;

import java.security.PrivateKey;
import java.security.PublicKey;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Ported close to verbatim from ElderSphere's VapidKeyProviderTest.
 */
class VapidKeyProviderTest {

    @Test
    void configuredKeysAreUsedVerbatimRatherThanGenerated() {
        VapidKeyProvider provider = new VapidKeyProvider(
                "configured-public-key", "configured-private-key", "mailto:ops@elderhive.app");

        assertThat(provider.getPublicKey()).isEqualTo("configured-public-key");
        assertThat(provider.getPrivateKey()).isEqualTo("configured-private-key");
        assertThat(provider.getSubject()).isEqualTo("mailto:ops@elderhive.app");
    }

    @Test
    void blankConfiguredKeysFallsBackToGeneratingAUsableKeyPair() throws Exception {
        VapidKeyProvider provider = new VapidKeyProvider("", "", "mailto:admin@elderhive.app");

        assertThat(provider.getPublicKey()).isNotBlank();
        assertThat(provider.getPrivateKey()).isNotBlank();
        assertThat(provider.getPublicKey()).doesNotContain("=", "+", "/");
        assertThat(provider.getPrivateKey()).doesNotContain("=", "+", "/");

        PublicKey publicKey = Utils.loadPublicKey(provider.getPublicKey());
        PrivateKey privateKey = Utils.loadPrivateKey(provider.getPrivateKey());
        assertThat(Utils.verifyKeyPair(privateKey, publicKey))
                .as("the generated public/private halves must actually correspond to the same EC key pair")
                .isTrue();
    }

    @Test
    void nullConfiguredKeysAlsoFallsBackToGenerating() {
        VapidKeyProvider provider = new VapidKeyProvider(null, null, "mailto:admin@elderhive.app");

        assertThat(provider.getPublicKey()).isNotBlank();
        assertThat(provider.getPrivateKey()).isNotBlank();
    }

    @Test
    void noConfiguredKeysEachInstanceGetsItsOwnFreshKeyPair() {
        VapidKeyProvider first = new VapidKeyProvider(null, null, "mailto:admin@elderhive.app");
        VapidKeyProvider second = new VapidKeyProvider(null, null, "mailto:admin@elderhive.app");

        assertThat(first.getPublicKey()).isNotEqualTo(second.getPublicKey());
        assertThat(first.getPrivateKey()).isNotEqualTo(second.getPrivateKey());
    }
}
