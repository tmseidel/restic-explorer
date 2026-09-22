package org.remus.resticexplorer.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class AuthPropertiesTest {

    @Autowired
    private AuthProperties authProperties;

    @Test
    void testDefaultAuthModeIsLocal() {
        assertEquals(AuthProperties.AuthMode.LOCAL, authProperties.getMode());
    }

    @Test
    void testOAuth2ModeAllowedProvidersDefaultsToEmpty() {
        assertTrue(authProperties.getAllowedProviders().isBlank());
    }
}
