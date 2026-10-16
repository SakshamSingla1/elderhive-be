package com.elderhive.webpush;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.Utils;
import org.bouncycastle.jce.ECNamedCurveTable;
import org.bouncycastle.jce.interfaces.ECPrivateKey;
import org.bouncycastle.jce.interfaces.ECPublicKey;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.jce.spec.ECNamedCurveParameterSpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Security;
import java.util.Base64;

/**
 * Ported close to verbatim from ElderSphere's VapidKeyProvider (only the default subject
 * changes, eldersphere.app -&gt; elderhive.app). Resolves the VAPID (RFC 8292) key pair Web
 * Push uses to authenticate this server to browser push services.
 *
 * <p>In a real deployment, set app.web-push.vapid.public-key/private-key (a base64url,
 * unpadded-encoded P-256 key pair) and app.web-push.vapid.subject (a mailto: or https: URL
 * identifying the operator, required by the spec). For local dev, when those are absent, a
 * fresh key pair is generated on every startup and logged at WARN — the public key changes
 * every time one isn't configured, so any subscription registered against a previous
 * ephemeral key becomes unusable after a restart.
 */
@Slf4j
@Component
public class VapidKeyProvider {

    static {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @Getter
    private final String publicKey;

    @Getter
    private final String privateKey;

    @Getter
    private final String subject;

    public VapidKeyProvider(
            @Value("${app.web-push.vapid.public-key:}") String configuredPublicKey,
            @Value("${app.web-push.vapid.private-key:}") String configuredPrivateKey,
            @Value("${app.web-push.vapid.subject:mailto:admin@elderhive.app}") String configuredSubject) {
        this.subject = configuredSubject;

        if (StringUtils.hasText(configuredPublicKey) && StringUtils.hasText(configuredPrivateKey)) {
            this.publicKey = configuredPublicKey;
            this.privateKey = configuredPrivateKey;
            log.info("Web Push: using the configured VAPID key pair (app.web-push.vapid.public-key/private-key).");
            return;
        }

        try {
            KeyPair keyPair = generateKeyPair();
            ECPublicKey ecPublicKey = (ECPublicKey) keyPair.getPublic();
            ECPrivateKey ecPrivateKey = (ECPrivateKey) keyPair.getPrivate();
            Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
            this.publicKey = encoder.encodeToString(Utils.encode(ecPublicKey));
            this.privateKey = encoder.encodeToString(Utils.encode(ecPrivateKey));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Failed to generate a fallback VAPID key pair for Web Push", e);
        }

        log.warn("Web Push: no VAPID public/private key configured - generated an ephemeral " +
                        "key pair for THIS RUN ONLY (fine for local dev, not for production - " +
                        "subscriptions registered now will stop working after a restart since the " +
                        "public key changes). To persist it, set:\nVAPID public key={}\nVAPID private key={}",
                this.publicKey, this.privateKey);
    }

    private static KeyPair generateKeyPair() throws GeneralSecurityException {
        ECNamedCurveParameterSpec parameterSpec = ECNamedCurveTable.getParameterSpec("prime256v1");
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("ECDH", "BC");
        keyPairGenerator.initialize(parameterSpec);
        return keyPairGenerator.generateKeyPair();
    }
}
