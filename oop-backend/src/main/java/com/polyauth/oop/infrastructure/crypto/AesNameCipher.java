package com.polyauth.oop.infrastructure.crypto;

import com.polyauth.oop.application.port.NameCipher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * 兼容既有 AES-256-GCM 姓名密文的读取器。
 */
@Component
public class AesNameCipher implements NameCipher {

    private static final String PREFIX = "v1:";
    private static final int TAG_BITS = 128;
    private static final int IV_BYTES = 12;

    @Value("${crypto.aes-key}")
    private String aesKey;

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
