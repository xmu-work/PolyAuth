package com.polyauth.infrastructure.crypto;

import com.polyauth.service.ddd.application.port.NameCipher;
import com.polyauth.service.ddd.application.port.AccountCrypto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 兼容既有 AES-256-GCM 姓名密文的读取器。
 */
@Component
public class AesNameCipher implements NameCipher, AccountCrypto {

    private static final String PREFIX = "v1:";
    private static final int TAG_BITS = 128;
    private static final int IV_BYTES = 12;
    private static final String PASSWORD_ALGORITHM = "pbkdf2_sha256";
    private static final int PASSWORD_ITERATIONS = 65536;
    private static final int PASSWORD_KEY_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    @Value("${crypto.aes-key}")
    private String aesKey;

    @Value("${crypto.hmac-key}")
    private String hmacKey;

    @Override
    public String decrypt(String encrypted) {
        try {
            boolean versioned = encrypted.startsWith(PREFIX);
            String payload = versioned ? encrypted.substring(PREFIX.length()) : encrypted;
            byte[] combined = Base64.getDecoder().decode(payload);
            byte[] iv = new byte[IV_BYTES];
            byte[] ciphertext = new byte[combined.length - IV_BYTES];
            System.arraycopy(combined, 0, iv, 0, IV_BYTES);
            System.arraycopy(combined, IV_BYTES, ciphertext, 0, ciphertext.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,
                    keySpec(versioned), new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalStateException("姓名解密失败", exception);
        }
    }

    @Override
    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, keySpec(true),
                    new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(
                    plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            return PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (Exception exception) {
            throw new IllegalStateException("加密失败", exception);
        }
    }

    @Override
    public String hash(String value) {
        return hmac(hmacKey, value);
    }

    @Override
    public String hmac(String secret, String value) {
        try {
            javax.crypto.Mac mac =
                    javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                result.append(String.format("%02x", item));
            }
            return result.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("HMAC 计算失败", exception);
        }
    }

    @Override
    public String hashPassword(String password) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return PASSWORD_ALGORITHM + "$" + PASSWORD_ITERATIONS + "$"
                + Base64.getEncoder().encodeToString(salt) + "$"
                + Base64.getEncoder().encodeToString(
                pbkdf2(password, salt, PASSWORD_ITERATIONS));
    }

    @Override
    public String maskName(String name) {
        if (name == null || name.isEmpty()) {
            return "";
        }
        return name.length() == 1
                ? name : name.charAt(0) + "*".repeat(name.length() - 1);
    }

    @Override
    public String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return phone;
        }
        return phone.substring(0, 3) + "****"
                + phone.substring(phone.length() - 4);
    }

    private byte[] pbkdf2(String password, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(
                    password.toCharArray(), salt, iterations, PASSWORD_KEY_BITS);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec)
                    .getEncoded();
        } catch (Exception exception) {
            throw new IllegalStateException("密码哈希失败", exception);
        }
    }

    private SecretKeySpec keySpec(boolean versioned) throws Exception {
        if (versioned) {
            return new SecretKeySpec(
                    MessageDigest.getInstance("SHA-256")
                            .digest(aesKey.getBytes(StandardCharsets.UTF_8)),
                    "AES");
        }
        byte[] weakKey = new byte[32];
        byte[] raw = aesKey.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(raw, 0, weakKey, 0, Math.min(raw.length, 32));
        return new SecretKeySpec(weakKey, "AES");
    }
}
