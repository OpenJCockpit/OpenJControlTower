package org.openjcontroltower.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.junit.jupiter.api.Test;
import org.openjcontroltower.application.SettingsService;
import org.openjcontroltower.domain.Settings;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import com.sun.net.httpserver.HttpServer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class SettingsJwtDecoderTest {
    private final SettingsService settings=mock(SettingsService.class);

    @Test void rebuildsTheDecoderOnlyWhenIssuerOrKeysChange() {
        Settings.Keycloak a=new Settings.Keycloak("https://a.example","r","web",null);
        Settings.Keycloak b=new Settings.Keycloak("https://b.example","r","web",null);
        Settings.Keycloak bInternal=new Settings.Keycloak("https://b.example","r","web","http://keycloak");
        List<Settings.Keycloak> built=new ArrayList<>();
        Function<Settings.Keycloak,JwtDecoder> factory=keycloak->{
            built.add(keycloak);
            return token->Jwt.withTokenValue(token).header("alg","none").issuer(keycloak.issuer()).build();
        };
        SettingsJwtDecoder decoder=new SettingsJwtDecoder(settings,factory);
        when(settings.keycloak()).thenReturn(a,a,b,bInternal);
        assertEquals("https://a.example/realms/r",decoder.decode("t").getIssuer().toString());
        decoder.decode("t");
        assertEquals("https://b.example/realms/r",decoder.decode("t").getIssuer().toString());
        decoder.decode("t");
        assertEquals(List.of(a,b,bInternal),built);
    }

    @Test void validatesSignatureAndIssuerAgainstTheConfiguredKeycloak() throws Exception {
        RSAKey key=new RSAKeyGenerator(2048).keyID("k1").generate();
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        byte[] jwks=new JWKSet(key.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
        server.createContext("/realms/r/protocol/openid-connect/certs",exchange->{
            exchange.getResponseHeaders().add("Content-Type","application/json");
            exchange.sendResponseHeaders(200,jwks.length);
            exchange.getResponseBody().write(jwks);
            exchange.close();
        });
        server.start();
        try {
            String url="http://127.0.0.1:"+server.getAddress().getPort();
            when(settings.keycloak()).thenReturn(new Settings.Keycloak(url,"r","web",null));
            JwtEncoder encoder=new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(key)));
            SettingsJwtDecoder decoder=new SettingsJwtDecoder(settings);
            assertEquals("alice",decoder.decode(token(encoder,url+"/realms/r")).getSubject());
            assertThrows(JwtValidationException.class,()->decoder.decode(token(encoder,"https://other.example/realms/r")));
        } finally { server.stop(0); }
    }
    private static String token(JwtEncoder encoder,String issuer) {
        JwtClaimsSet claims=JwtClaimsSet.builder().issuer(issuer).subject("alice")
            .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).keyId("k1").build(),claims)).getTokenValue();
    }
}
