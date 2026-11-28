package com.polyauth.infrastructure.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.polyauth.service.port.identity.IdentityUseCase;
import com.polyauth.service.port.auth.AuthUseCase;
import com.polyauth.infrastructure.crypto.AesNameCipher;
import com.polyauth.api.identity.IdentityAuthController;
import com.polyauth.api.shared.RequestIdFilter;
import com.polyauth.api.shared.AuthInterceptor;
import com.polyauth.api.identity.vo.PermissionCheckVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 验证 OOP 后端菜单切换时的 HTTP 摘要、权限码判定与分层 Logger 解析日志。
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
        request.addHeader(RequestIdFilter.HEADER, "req-oop-menu-home");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {
            req.setAttribute(AuthInterceptor.ATTR_USERNAME, "admin");
            req.setAttribute(AuthInterceptor.ATTR_SYSTEM_CODE, "ADMIN");
            ((MockHttpServletResponse) res).setStatus(200);
        });

        assertThat(appender.list).hasSize(1);
        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.INFO);
        assertThat(event.getMDCPropertyMap()).containsEntry("requestId", "req-oop-menu-home");
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
        IdentityUseCase useCase = mock(IdentityUseCase.class);
        when(useCase.hasPermissionCode(1L, "ADMIN", "MENU_HOME")).thenReturn(true);
        when(useCase.hasPermissionCode(1L, "ADMIN", "MENU_LOG")).thenReturn(false);

        IdentityAuthController controller = new IdentityAuthController(useCase, mock(AuthUseCase.class));
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
    @DisplayName("DddLayerLoggingAspect 按目标业务类解析 Logger 且消息体不含重复 requestId")
    void layerLoggingAspectUsesTargetLoggerWithoutDuplicateRequestId() {
        ListAppender<ILoggingEvent> controllerAppender = attachAppender(IdentityAuthController.class, Level.DEBUG);
        ListAppender<ILoggingEvent> aspectAppender = attachAppender(DddLayerLoggingAspect.class, Level.DEBUG);
        ListAppender<ILoggingEvent> infraAppender = attachAppender(AesNameCipher.class, Level.DEBUG);

        IdentityUseCase useCase = mock(IdentityUseCase.class);
        when(useCase.hasPermissionCode(1L, "ADMIN", "MENU_USER")).thenReturn(true);

        AspectJProxyFactory controllerFactory = new AspectJProxyFactory(
                new IdentityAuthController(useCase, mock(AuthUseCase.class)));
        controllerFactory.addAspect(new DddLayerLoggingAspect());
        IdentityAuthController proxiedController = controllerFactory.getProxy();

        proxiedController.checkPermission(1L, "ADMIN", "MENU_USER");

        assertThat(aspectAppender.list).isEmpty();
        assertThat(controllerAppender.list)
                .extracting(ILoggingEvent::getLoggerName)
                .containsOnly(IdentityAuthController.class.getName());
        assertThat(controllerAppender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .anySatisfy(msg -> assertThat(msg)
                        .contains("layer=interface target=IdentityAuthController.checkPermission success=true")
                        .doesNotContain("requestId="))
                .anySatisfy(msg -> assertThat(msg)
                        .isEqualTo("permission-check system=ADMIN userId=1 permission=MENU_USER allowed=true"));

        AspectJProxyFactory infraFactory = new AspectJProxyFactory(new AesNameCipher());
        infraFactory.setProxyTargetClass(true);
        infraFactory.addAspect(new DddLayerLoggingAspect());
        AesNameCipher proxiedCipher = infraFactory.getProxy();

        assertThat(proxiedCipher.maskName("张三")).isEqualTo("张*");
        assertThat(aspectAppender.list).isEmpty();
        assertThat(infraAppender.list)
                .extracting(ILoggingEvent::getLoggerName)
                .containsOnly(AesNameCipher.class.getName());
        assertThat(infraAppender.list)
                .extracting(ILoggingEvent::getFormattedMessage)
                .anySatisfy(msg -> assertThat(msg)
                        .contains("layer=infrastructure target=AesNameCipher.maskName success=true")
                        .doesNotContain("requestId="));
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
