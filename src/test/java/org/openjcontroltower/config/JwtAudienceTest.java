package org.openjcontroltower.config;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/** Real token validation (no mocked JWT): the Cockpit realm's issuer and the {@code openjcontroltower} audience. */
@SpringBootTest(properties={
    "spring.datasource.url=jdbc:h2:mem:audience;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "controltower.git.credentials.secret=integration-test-secret-of-32-chars!",
    "controltower.git.credentials.salt=0123456789abcdef"})
class JwtAudienceTest {
    private static final String ISSUER="http://localhost:8080/realms/openjcockpit";
    private static RSAKey key;
    private static HttpServer jwks;
    @Autowired WebApplicationContext context;

    @BeforeAll static void startKeyServer() throws Exception {
        key=new RSAKeyGenerator(2048).keyID("k1").generate();
        byte[] body=new JWKSet(key.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
        jwks=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        jwks.createContext("/certs",exchange->{
            exchange.getResponseHeaders().add("Content-Type","application/json");
            exchange.sendResponseHeaders(200,body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        jwks.start();
    }
    @AfterAll static void stopKeyServer() { jwks.stop(0); }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",()->ISSUER);
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",()->"http://127.0.0.1:"+jwks.getAddress().getPort()+"/certs");
    }

    private static String token(String issuer,List<String> audience) throws Exception {
        JWTClaimsSet.Builder claims=new JWTClaimsSet.Builder().subject("user").issuer(issuer)
            .expirationTime(new Date(System.currentTimeMillis()+60_000));
        if (audience!=null) claims.audience(audience);
        SignedJWT jwt=new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("k1").build(),claims.build());
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }
    private int statusOf(String token) throws Exception {
        MockMvc mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        return mvc.perform(get("/api/projects").header("Authorization","Bearer "+token)).andReturn().getResponse().getStatus();
    }

    @Test void acceptsTheControlTowerAudience() throws Exception {
        org.junit.jupiter.api.Assertions.assertEquals(200,statusOf(token(ISSUER,List.of("account","openjcontroltower"))));
    }
    @Test void rejectsAnotherAudience() throws Exception {
        org.junit.jupiter.api.Assertions.assertEquals(401,statusOf(token(ISSUER,List.of("account"))));
    }
    @Test void rejectsATokenWithoutAudience() throws Exception {
        org.junit.jupiter.api.Assertions.assertEquals(401,statusOf(token(ISSUER,null)));
    }
    @Test void rejectsAnotherIssuer() throws Exception {
        org.junit.jupiter.api.Assertions.assertEquals(401,statusOf(token("http://localhost:8081/realms/openjcontroltower",List.of("openjcontroltower"))));
    }
}
