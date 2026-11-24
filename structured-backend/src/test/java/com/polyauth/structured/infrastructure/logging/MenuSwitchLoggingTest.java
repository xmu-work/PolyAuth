package com.polyauth.structured.infrastructure.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.polyauth.structured.application.auth.AuthService;
import com.polyauth.structured.application.identity.IdentityService;
import com.polyauth.structured.infrastructure.persistence.repository.IamUserRepository;
import com.polyauth.structured.interfaces.AuthInterceptor;
import com.polyauth.structured.interfaces.RequestIdFilter;
import com.polyauth.structured.interfaces.identity.IdentityAuthController;
import com.polyauth.structured.interfaces.identity.vo.PermissionCheckVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 验证结构化后端菜单切换时的 HTTP 摘要、权限码判定与分层 Logger 解析日志。
 */
class MenuSwitchLoggingTest {

    private final List<Runnable> cleanups = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (int i = cleanups.size() - 1; i >= 0; i--) {
            cleanups.get(i).run();
        }
        cleanups.clear();
    }

    @Test
    @DisplayName("RequestIdFilter 输出带 requestId 与用户上下文的 INFO HTTP 摘要且不泄露敏感查询串")
    void requestIdFilterLogsHttpSummaryWithoutQueryString() throws Exception {
        ListAppender<ILoggingEvent> appender = attachAppender(RequestIdFilter.class, Level.INFO);
        RequestIdFilter filter = new RequestIdFilter();

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/auth/permissions/MENU_HOME");
        request.setContextPath("/api/v1");
        request.setQueryString("token=secret-jwt&phone=13800138000");
        request.addHeader(RequestIdFilter.HEADER, "req-menu-home");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            req.setAttribute(AuthInterceptor.ATTR_USERNAME, "admin");
            req.setAttribute(AuthInterceptor.ATTR_SYSTEM_CODE, "ADMIN");
            ((MockHttpServletResponse) res).setStatus(200);
        });

        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(event.getMDCPropertyMap()).containsEntry("requestId", "req-menu-home");
        assertThat(event.getFormattedMessage())
                .contains("http method=GET")
                .contains("uri=/api/v1/auth/permissions/MENU_HOME")
                .contains("status=200")
                .contains("user=admin")
                .contains("system=ADMIN")
                .contains("elapsedMs=")
                .doesNotContain("secret-jwt")
                .doesNotContain("13800138000")
                .doesNotContain("token=");
    }

    @Test
    @DisplayName("RequestIdFilter 跳过健康检查路径且 OPTIONS 预检不输出 INFO")
    void requestIdFilterSkipsActuatorAndOptionsInfoLogs() throws Exception {
        ListAppender<ILoggingEvent> appender = attachAppender(RequestIdFilter.class, Level.INFO);
        RequestIdFilter filter = new RequestIdFilter();

        MockHttpServletRequest actuator = new MockHttpServletRequest("GET", "/api/v1/actuator/health");
        actuator.setContextPath("/api/v1");
        filter.doFilter(actuator, new MockHttpServletResponse(), (req, res) -> {
        });

        MockHttpServletRequest preflight = new MockHttpServletRequest("OPTIONS", "/api/v1/auth/permissions/MENU_HOME");
        preflight.setContextPath("/api/v1");
        filter.doFilter(preflight, new MockHttpServletResponse(), (req, res) -> {
        });

        assertThat(appender.list).isEmpty();
    }

    @Test
    @DisplayName("IdentityAuthController 校验菜单权限码时输出明确 permission-check INFO 日志")
    void checkPermissionLogsMenuCodeAndResult() {
        ListAppender<ILoggingEvent> appender = attachAppender(IdentityAuthController.class, Level.INFO);
        IdentityService identityService = mock(IdentityService.class);
        AuthService authService = mock(AuthService.class);
        when(identityService.hasPermissionCode(1L, "ADMIN", "MENU_HOME")).thenReturn(true);
        when(identityService.hasPermissionCode(1L, "ADMIN", "MENU_LOG")).thenReturn(false);

        IdentityAuthController controller = new IdentityAuthController(identityService, authService);
        PermissionCheckVO allowedResult = controller.checkPermission(1L, "ADMIN", "MENU_HOME");
        PermissionCheckVO deniedResult = controller.checkPermission(1L, "ADMIN", "MENU_LOG");

        assertThat(allowedResult.allowed()).isTrue();
        assertThat(deniedResult.allowed()).isFalse();
        assertThat(appender.list).hasSize(2);
        assertThat(appender.list.get(0).getLevel()).isEqualTo(Level.INFO);
        assertThat(appender.list.get(0).getFormattedMessage())
                .isEqualTo("permission-check system=ADMIN userId=1 permission=MENU_HOME allowed=true");
        assertThat(appender.list.get(1).getFormattedMessage())
                .isEqualTo("permission-check system=ADMIN userId=1 permission=MENU_LOG allowed=false");
    }

    @Test
    @DisplayName("LayerLoggingAspect 按目标业务类及 JPA 接口解析 Logger，而非固定归属切面类")
    void layerLoggingAspectUsesTargetLogger() {
        ListAppender<ILoggingEvent> controllerAppender = attachAppender(IdentityAuthController.class, Level.DEBUG);
        ListAppender<ILoggingEvent> aspectAppender = attachAppender(LayerLoggingAspect.class, Level.DEBUG);
        ListAppender<ILoggingEvent> repoAppender = attachAppender(IamUserRepository.class, Level.DEBUG);

        IdentityService identityService = mock(IdentityService.class);
        AuthService authService = mock(AuthService.class);
        when(identityService.hasPermissionCode(1L, "ADMIN", "MENU_USER")).thenReturn(true);

        AspectJProxyFactory controllerFactory = new AspectJProxyFactory(
                new IdentityAuthController(identityService, authService));
        controllerFactory.addAspect(new LayerLoggingAspect());
        IdentityAuthController proxiedController = controllerFactory.getProxy();

        proxiedController.checkPermission(1L, "ADMIN", "MENU_USER");

        assertThat(aspectAppender.list).isEmpty();
        assertThat(controllerAppender.list)
                .extracting(ILoggingEvent::getLoggerName)
                .containsOnly(IdentityAuthController.class.getName());
        assertThat(controllerAppender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .anySatisfy(msg -> assertThat(msg).contains("layer=api class=IdentityAuthController method=checkPermission phase=start"))
                .anySatisfy(msg -> assertThat(msg).contains("permission-check system=ADMIN userId=1 permission=MENU_USER allowed=true"))
                .anySatisfy(msg -> assertThat(msg).contains("layer=api class=IdentityAuthController method=checkPermission phase=complete"));

        IamUserRepository repoProxyTarget = (IamUserRepository) Proxy.newProxyInstance(
                IamUserRepository.class.getClassLoader(),
                new Class<?>[]{IamUserRepository.class},
                (proxy, method, args) -> "count".equals(method.getName()) ? 1L : null);
        AspectJProxyFactory repoFactory = new AspectJProxyFactory(repoProxyTarget);
        repoFactory.setProxyTargetClass(false);
        repoFactory.addAspect(new LayerLoggingAspect());
        IamUserRepository proxiedRepo = repoFactory.getProxy();

        assertThat(proxiedRepo.count()).isEqualTo(1L);
        assertThat(aspectAppender.list).isEmpty();
        assertThat(repoAppender.list)
                .extracting(ILoggingEvent::getLoggerName)
                .containsOnly(IamUserRepository.class.getName());
        assertThat(repoAppender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .anySatisfy(msg -> assertThat(msg).contains("layer=infrastructure class=IamUserRepository method=count phase=complete"));
    }

    private ListAppender<ILoggingEvent> attachAppender(Class<?> loggerClass, Level level) {
        Logger logger = (Logger) LoggerFactory.getLogger(loggerClass);
        Level previousLevel = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.setLevel(level);
        logger.addAppender(appender);
        cleanups.add(() -> {
            logger.detachAppender(appender);
            appender.stop();
            logger.setLevel(previousLevel);
        });
        return appender;
    }
}
