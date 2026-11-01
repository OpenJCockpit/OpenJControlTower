package org.openjcontroltower.infrastructure;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CredentialCipherTest {
    static final String SECRET="unit-test-secret-at-least-32-characters";
    static final String SALT="0123456789abcdef";

    @Test void encryptsWithRandomIvAndDecrypts() {
        CredentialCipher cipher=new CredentialCipher(SECRET,SALT);
        String first=cipher.encrypt("ghp_token");
        assertNotEquals("ghp_token",first);
        assertNotEquals(first,cipher.encrypt("ghp_token"));
        assertEquals("ghp_token",cipher.decrypt(first));
    }
    @Test void refusesWeakKeyMaterial() {
        assertThrows(IllegalStateException.class,()->new CredentialCipher("short",SALT));
        assertThrows(IllegalStateException.class,()->new CredentialCipher(SECRET,"not-hex-salt-value"));
        assertThrows(IllegalStateException.class,()->new CredentialCipher(SECRET,"abcd"));
    }
}
