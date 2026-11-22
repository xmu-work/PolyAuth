package com.polyauth.oop.infrastructure.crypto;

import com.polyauth.oop.application.port.PasswordVerifier;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * 兼容既有 PBKDF2 密码哈希格式的校验器。
 */
@Component
public class Pbkdf2PasswordVerifier implements PasswordVerifier {

    private static final String ALGORITHM = "pbkdf2_sha256";
    private static final int LEGACY_ITERATIONS = 65536;
    private static final int KEY_LENGTH = 256;

    @Override
    public boolean verify(String rawPassword, String storedHash) {
        if (rawPassword == null || storedHash == null) {
            return false;
        }
        if (storedHash.contains("$")) {
            String[] parts = storedHash.split("\\$");
            if (parts.length != 4 || !ALGORITHM.equals(parts[0])) {
                return false;
            }
            byte[] salt = Base64.getDecoder().decode(parts[2]);
            byte[] expected = Base64.getDecoder().decode(parts[3]);
            return MessageDigest.isEqual(
                    expected, derive(rawPassword, salt, Integer.parseInt(parts[1])));
        }
        if (!storedHash.contains(":")) {
            return false;
        }
        String[] parts = storedHash.split(":", 2);
        byte[] salt = Base64.getDecoder().decode(parts[0]);
        byte[] expected = Base64.getDecoder().decode(parts[1]);
        return MessageDigest.isEqual(
                expected, derive(rawPassword, salt, LEGACY_ITERATIONS));
    }

    private byte[] derive(String password, byte[] salt, int iterations) {
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            PBEKeySpec spec = new PBEKeySpec(
                    password.toCharArray(), salt, iterations, KEY_LENGTH);
            return factory.generateSecret(spec).getEncoded();
        } catch (Exception exception) {
            throw new IllegalStateException("密码校验失败", exception);
        }
    }
}
