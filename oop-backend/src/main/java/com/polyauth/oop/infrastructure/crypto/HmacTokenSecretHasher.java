package com.polyauth.oop.infrastructure.crypto;

import com.polyauth.oop.application.port.TokenSecretHasher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * 使用独立 HMAC 密钥计算令牌不可逆摘要。
 */
@Component
public class HmacTokenSecretHasher implements TokenSecretHasher {

    @Value("${crypto.hmac-key}")
    private String hmacKey;

    @Override
    public String hash(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    hmacKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return toHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("令牌摘要计算失败", exception);
        }
    }

    private String toHex(byte[] value) {
        StringBuilder result = new StringBuilder(value.length * 2);
        for (byte item : value) {
            result.append(String.format("%02x", item));
        }
        return result.toString();
    }
}
