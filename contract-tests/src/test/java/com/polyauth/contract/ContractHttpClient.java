package com.polyauth.contract;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.StringJoiner;

/**
 * 共享契约测试的 HTTP 客户端，不依赖任何后端内部类型。
 */
final class ContractHttpClient {

    private final String baseUrl;
    private final HttpClient client;
    private final ObjectMapper objectMapper;

    /**
     * 使用目标后端 API 根地址创建客户端。
     */
    ContractHttpClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * 登录并返回访问令牌。
     */
    String login(String username, String password, String systemCode) throws Exception {
        String body = objectMapper.createObjectNode()
                .put("username", username)
                .put("password", password)
                .putPOJO("systems", java.util.List.of(systemCode))
                .toString();
        Response response = send("POST", "/auth/login", null, body, "application/json");
        if (response.status() != 200) {
            throw new AssertionError("登录失败: " + response.status() + " " + response.body());
        }
        return objectMapper.readTree(response.body()).path("accessToken").asText();
    }

    Response get(String path, String token) throws Exception {
        return send("GET", path, token, null, null);
    }

    Response postForm(String path, String token, Map<String, String> form) throws Exception {
        StringJoiner query = new StringJoiner("&");
        for (Map.Entry<String, String> entry : form.entrySet()) {
            if (entry.getValue() != null) {
                query.add(encode(entry.getKey()) + "=" + encode(entry.getValue()));
            }
        }
        return send("POST", path + "?" + query, token, null, null);
    }

    Response putJson(String path, String token, String json) throws Exception {
        return send("PUT", path, token, json, "application/json");
    }

    Response postJson(String path, String token, String json) throws Exception {
        return send("POST", path, token, json, "application/json");
    }

    JsonNode json(Response response) throws Exception {
        return objectMapper.readTree(response.body());
    }

    private Response send(String method, String path, String token, String body,
                          String contentType) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(20))
                .header("Accept", "application/json")
                .header("X-Request-Id", "contract-" + System.nanoTime());
        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }
        if (contentType != null) {
            builder.header("Content-Type", contentType);
        }
        if ("GET".equals(method)) {
            builder.GET();
        } else if ("POST".equals(method)) {
            builder.POST(body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        } else if ("PUT".equals(method)) {
            builder.PUT(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        } else {
            throw new IllegalArgumentException("不支持的 HTTP 方法: " + method);
        }
        HttpResponse<String> response = client.send(
                builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new Response(response.statusCode(), response.body());
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * HTTP 响应快照。
     *
     * @param status HTTP 状态码
     * @param body   响应体
     */
    record Response(int status, String body) {
    }
}
