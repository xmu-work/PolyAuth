package com.iam.platform.interfaces;

import com.iam.platform.application.api.ApiService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 启动期 API 登记检查。
 *
 * <p>未登记到 iam_api、未映射前端权限或未受保护的管理端接口会进入告警列表，
 * 运行期由鉴权拦截器按 fail-closed 拒绝。
 */
@Component
@RequiredArgsConstructor
public class ApiRegistrationAudit {

    private static final Set<String> IGNORED_PREFIXES = Set.of("/open/", "/actuator", "/error", "/druid");
    private static final Set<String> IGNORED_PATTERNS =
            Set.of("/auth/login", "/auth/logout", "/auth/validate", "/auth/password");

    private final ApplicationContext applicationContext;
    private final ApiService apiService;

    @Value("${iam.api-registration.fail-fast:false}")
    private boolean failFast;

    @EventListener(ContextRefreshedEvent.class)
    public void auditUnregisteredApis() {
        RequestMappingHandlerMapping mapping;
        try {
            mapping = applicationContext.getBean("requestMappingHandlerMapping", RequestMappingHandlerMapping.class);
        } catch (Exception e) {
            return;
        }

        List<String> missing = new ArrayList<>();
        mapping.getHandlerMethods().forEach((info, handler) ->
                patternsOf(info).forEach(pattern -> {
                    if (isIgnored(pattern)) {
                        return;
                    }
                    for (String method : methodsOf(info)) {
                        if (apiService.findByRequest(pattern, method)
                                .filter(api -> api.getPermissionId() != null).isEmpty()) {
                            missing.add(method + " " + pattern);
                        }
                    }
                }));

        if (!missing.isEmpty() && failFast) {
            throw new IllegalStateException("存在未登记或未映射前端权限的管理端 API");
        }
    }

    private Set<String> patternsOf(RequestMappingInfo info) {
        if (info.getPathPatternsCondition() == null) {
            return Set.of();
        }
        return info.getPathPatternsCondition().getPatternValues();
    }

    private Set<String> methodsOf(RequestMappingInfo info) {
        Set<String> methods = new HashSet<>();
        for (RequestMethod method : info.getMethodsCondition().getMethods()) {
            methods.add(method.name());
        }
        return methods;
    }

    private boolean isIgnored(String pattern) {
        return IGNORED_PATTERNS.contains(pattern) || IGNORED_PREFIXES.stream().anyMatch(pattern::startsWith);
    }
}
