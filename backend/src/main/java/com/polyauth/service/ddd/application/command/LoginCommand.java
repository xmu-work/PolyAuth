package com.polyauth.service.ddd.application.command;

import java.util.List;

/**
 * 本地登录用例命令。
 */
public record LoginCommand(String username, String password, List<String> systems,
                           String clientIp, String requestId) {
}
