package com.bookwheel.server.common.oauth2;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.regex.Pattern;

public final class OAuth2Pkce {

    public static final String SESSION_ATTRIBUTE = "OAUTH2_CODE_CHALLENGE";

    private static final Pattern CODE_CHALLENGE_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{43}$");
    private static final Pattern CODE_VERIFIER_PATTERN = Pattern.compile("^[A-Za-z0-9\\-._~]{43,128}$");

    private OAuth2Pkce() {
    }

    public static boolean isValidCodeChallenge(String codeChallenge) {
        return codeChallenge != null && CODE_CHALLENGE_PATTERN.matcher(codeChallenge).matches();
    }

    public static boolean isValidCodeVerifier(String codeVerifier) {
        return codeVerifier != null && CODE_VERIFIER_PATTERN.matcher(codeVerifier).matches();
    }

    public static String createS256CodeChallenge(String codeVerifier) {
        if (!isValidCodeVerifier(codeVerifier)) {
            throw new IllegalArgumentException("Invalid PKCE code verifier");
        }

        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is unavailable", e);
        }
    }
}
