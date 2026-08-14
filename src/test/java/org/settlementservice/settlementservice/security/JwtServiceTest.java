package org.settlementservice.settlementservice.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import javax.crypto.SecretKey;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SECRET = "test-only-jwt-secret-that-is-long-enough-for-hs512-signing-1234567890";

    private JwtService jwtService;
    private SecretKey signingKey;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(SECRET, 60);
        signingKey = Keys.hmacShaKeyFor(SECRET.getBytes());
    }

    private Authentication authenticationFor(String username, String... authorities) {
        List<SimpleGrantedAuthority> granted = List.of(authorities).stream().map(SimpleGrantedAuthority::new).toList();
        return new UsernamePasswordAuthenticationToken(username, null, granted);
    }

    @Test
    void generateToken_thenExtractUsername_returnsTheOriginalUsername() {
        // Regression test: generateToken used to build the "sub" claim from
        // authentication.getPrincipal().toString(), which for a UserDetails principal produces a
        // multi-field debug dump, not the username. It must use authentication.getName() instead.
        Authentication authentication = authenticationFor("admin", "ROLE_ADMIN");

        String token = jwtService.generateToken(authentication);

        assertThat(jwtService.extractUsername(token)).isEqualTo("admin");
    }

    @Test
    void generateToken_includesRoleClaimWithoutRolePrefix() {
        Authentication authentication = authenticationFor("admin", "ROLE_ADMIN");

        String token = jwtService.generateToken(authentication);

        assertThat(claimsOf(token).get("role", String.class)).isEqualTo("ADMIN");
    }

    @Test
    void generateToken_includesAllAuthoritiesInAuthoritiesClaim() {
        Authentication authentication = authenticationFor("admin", "ROLE_ADMIN", "FACTOR_PASSWORD");

        String token = jwtService.generateToken(authentication);

        List<Object> authorities = claimsOf(token).get("authorities", List.class);
        assertThat(authorities).containsExactlyInAnyOrder("ROLE_ADMIN", "FACTOR_PASSWORD");
    }

    @Test
    void validateToken_freshlyGeneratedToken_isValid() {
        String token = jwtService.generateToken(authenticationFor("admin", "ROLE_ADMIN"));

        assertThat(jwtService.validateToken(token)).isTrue();
    }

    @Test
    void validateToken_expiredToken_isInvalid() {
        JwtService shortLivedJwtService = new JwtService(SECRET, 0);
        String token = shortLivedJwtService.generateToken(authenticationFor("admin", "ROLE_ADMIN"));

        assertThat(shortLivedJwtService.validateToken(token)).isFalse();
    }

    @Test
    void validateToken_tamperedToken_isInvalid() {
        String token = jwtService.generateToken(authenticationFor("admin", "ROLE_ADMIN"));
        // JWT has 3 parts: header.payload.signature
        // Tamper with the signature part by replacing characters in the middle
        String[] parts = token.split("\\.");
        String signature = parts[2];
        // Replace middle characters of signature to ensure tampering is detected
        String tamperedSignature = signature.substring(0, signature.length() / 2)
                + "TAMPERED"
                + signature.substring(signature.length() / 2 + 7);
        String tampered = parts[0] + "." + parts[1] + "." + tamperedSignature;

        assertThat(jwtService.validateToken(tampered)).isFalse();
    }

    @Test
    void validateToken_garbageString_isInvalid() {
        assertThat(jwtService.validateToken("not-a-real-token")).isFalse();
    }

    @Test
    void differentSigningSecret_producesTokenThatFailsValidation() {
        String token = jwtService.generateToken(authenticationFor("admin", "ROLE_ADMIN"));
        JwtService otherJwtService = new JwtService("a-completely-different-secret-that-is-also-long-enough-here", 60);

        assertThat(otherJwtService.validateToken(token)).isFalse();
    }

    private Claims claimsOf(String token) {
        return Jwts.parser().verifyWith(signingKey).build().parseSignedClaims(token).getPayload();
    }
}
