package org.openjcontroltower.infrastructure;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Component;

/** Encrypts Git secrets at rest: AES-256-GCM with a PBKDF2-derived key, as in OpenJCockpit. */
@Component
public class CredentialCipher {
    private final TextEncryptor encryptor;
    public CredentialCipher(@Value("${controltower.git.credentials.secret}") String secret,
                            @Value("${controltower.git.credentials.salt}") String salt) {
        if (secret.length()<32) throw new IllegalStateException("GIT_CREDENTIALS_SECRET must be at least 32 characters");
        if (!salt.matches("([0-9a-fA-F]{2}){8,}")) throw new IllegalStateException("GIT_CREDENTIALS_SALT must be at least 16 hexadecimal characters");
        this.encryptor=Encryptors.delux(secret,salt);
    }
    public String encrypt(String plain) { return encryptor.encrypt(plain); }
    public String decrypt(String encrypted) { return encryptor.decrypt(encrypted); }
}
