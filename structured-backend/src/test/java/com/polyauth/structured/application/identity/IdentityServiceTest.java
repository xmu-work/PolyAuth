package com.polyauth.structured.application.identity;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.polyauth.structured.TestFixtures;
import com.polyauth.structured.application.decision.DecisionService;
import com.polyauth.structured.common.enums.ErrorCode;
import com.polyauth.structured.common.exception.BusinessException;
import com.polyauth.structured.infrastructure.crypto.CryptoComponent;
import com.polyauth.structured.infrastructure.persistence.entity.IamApi;
import com.polyauth.structured.infrastructure.persistence.entity.IamLog;
import com.polyauth.structured.infrastructure.persistence.entity.IamOrg;
import com.polyauth.structured.infrastructure.persistence.entity.IamPermission;
import com.polyauth.structured.infrastructure.persistence.entity.IamSystem;
import com.polyauth.structured.infrastructure.persistence.entity.IamUser;
import com.polyauth.structured.infrastructure.persistence.repository.IamApiRepository;
import com.polyauth.structured.infrastructure.persistence.repository.IamLogRepository;
import com.polyauth.structured.infrastructure.persistence.repository.IamOrgRepository;
import com.polyauth.structured.infrastructure.persistence.repository.IamPermissionRepository;
import com.polyauth.structured.infrastructure.persistence.repository.IamSystemRepository;
import com.polyauth.structured.infrastructure.persistence.repository.IamUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdentityServiceTest {

    @Mock
    private IamUserRepository userRepository;
    @Mock
    private IamOrgRepository orgRepository;
    @Mock
    private IamApiRepository apiRepository;
    @Mock
    private IamSystemRepository systemRepository;
    @Mock
    private IamPermissionRepository permissionRepository;
    @Mock
    private DecisionService decisionService;
    @Mock
    private IamLogRepository logRepository;

    private final CryptoComponent crypto = TestFixtures.crypto();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private IdentityService identityService;
    private IamSystem system;

    @BeforeEach
    void setUp() {
        identityService = new IdentityService(userRepository, orgRepository, apiRepository,
                systemRepository, permissionRepository, decisionService, crypto,
                logRepository, objectMapper);
        system = new IamSystem();
        system.setId(10L);
        system.setSystemCode("course-project-management");
        system.setStatus(IamSystem.STATUS_ACTIVE);
        system.setIsDeleted(0);
    }

    @Test
    @DisplayName("FR-38 当前身份返回有效角色与前端权限码")
    void currentUserMapsRolesAndPermissionCodes() {
        IamUser user = TestFixtures.user(7L, "NORMAL", crypto, "pwd");
        IamPermission permission = new IamPermission();
        permission.setId(100L);
        permission.setSystemId(10L);
        permission.setPermissionCode("CV");
        permission.setIsDeleted(0);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(systemRepository.findBySystemCode("course-project-management"))
                .thenReturn(Optional.of(system));
        when(decisionService.getUserPermissions(7L, "course-project-management"))
                .thenReturn(Set.of(100L));
        when(decisionService.getUserRoleCodes(7L)).thenReturn(Set.of("teacher", "admin"));
        when(permissionRepository.findAllById(Set.of(100L))).thenReturn(List.of(permission));

        IdentityProfile profile = identityService.getCurrentUser(7L, "course-project-management");

        assertThat(profile.subject()).isEqualTo("u7");
        assertThat(profile.no()).isEqualTo("u7");
        assertThat(profile.name()).isEqualTo("张三");
        assertThat(profile.roles()).containsExactly("admin", "teacher");
        assertThat(profile.permissions()).containsExactly("CV");
    }

    @Test
    @DisplayName("FR-09 部门查询只返回直接下级并带 hasChildren")
    void departmentPageReturnsDirectChildren() {
        IamOrg child = org(2L, 1L, "CS", "计算机学院");
        IamOrg nested = org(3L, 2L, "SE", "软件工程");
        when(orgRepository.findById(1L)).thenReturn(Optional.of(org(1L, null, "ROOT", "学校")));
        when(orgRepository.findByParentId(1L)).thenReturn(List.of(child));
        when(orgRepository.findByParentId(2L)).thenReturn(List.of(nested));

        IdentityPageSlice<IdentityDepartmentData> page =
                identityService.pageDepartments(null, 1L, 1, 20);

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items()).containsExactly(
                new IdentityDepartmentData(2L, "CS", "计算机学院", 1L, true));
    }

    @Test
    @DisplayName("FR-37 operationId 映射到 API 权限并 fail-closed")
    void apiOperationChecksMappedPermission() {
        IamApi api = new IamApi();
        api.setId(30L);
        api.setSystemId(10L);
        api.setApiCode("listAdminCourses");
        api.setPermissionId(100L);
        api.setStatus(IamApi.STATUS_ACTIVE);
        api.setIsDeleted(0);
        when(userRepository.findById(7L)).thenReturn(Optional.of(
                TestFixtures.user(7L, "NORMAL", crypto, "pwd")));
        when(systemRepository.findBySystemCode("course-project-management"))
                .thenReturn(Optional.of(system));
        when(apiRepository.findBySystemIdAndApiCode(10L, "listAdminCourses"))
                .thenReturn(Optional.of(api));
        when(decisionService.hasPermission(7L, "course-project-management", 100L))
                .thenReturn(true);

        assertThat(identityService.hasApiOperation(7L, "course-project-management",
                "listAdminCourses")).isTrue();
    }

    @Test
    @DisplayName("FR-02 无 TEC 权限不能查询统一身份用户")
    void userLookupRequiresTeacherManagementPermission() {
        when(userRepository.findById(7L)).thenReturn(Optional.of(
                TestFixtures.user(7L, "NORMAL", crypto, "pwd")));
        when(systemRepository.findBySystemCode("course-project-management"))
                .thenReturn(Optional.of(system));
        when(permissionRepository.findBySystemIdAndPermissionCode(10L, "TEC"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> identityService.lookupUser(
                7L, "course-project-management", "T1008"))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getCode())
                .isEqualTo(ErrorCode.NO_FUNCTION_PERM.getCode());
    }

    @Test
    @DisplayName("FR-44 写入外部审计日志保留完整对象摘要与业务标识并支持 requestId 幂等")
    void writeAuditLogPersistsAndDeduplicatesByRequestId() {
        IamUser student = TestFixtures.user(7L, "NORMAL", crypto, "pwd");
        when(userRepository.findById(7L)).thenReturn(Optional.of(student));
        when(systemRepository.findBySystemCode("course-project-management"))
                .thenReturn(Optional.of(system));
        when(decisionService.getUserRoleCodes(7L)).thenReturn(Set.of("student"));
        String longTarget = "班级-" + "长摘要".repeat(120);
        when(logRepository.findFirstBySystemCodeAndDecisionIdAndRequestId(
                "course-project-management", "EXT_AUDIT", "req-uuid-1"))
                .thenReturn(Optional.empty());
        when(logRepository.save(any(IamLog.class))).thenAnswer(invocation -> {
            IamLog entity = invocation.getArgument(0);
            entity.setLogId(101L);
            return entity;
        });

        IdentityAuditLogData created = identityService.writeAuditLog(
                7L,
                "course-project-management",
                "req-uuid-1",
                "roster_changed",
                "class",
                "cpm-class-123",
                longTarget,
                Map.of("imported", 0),
                null,
                "127.0.0.1");

        ArgumentCaptor<IamLog> captor = ArgumentCaptor.forClass(IamLog.class);
        verify(logRepository).save(captor.capture());
        IamLog savedEntity = captor.getValue();
        assertThat(savedEntity.getLogType()).isEqualTo("ACTION");
        assertThat(savedEntity.getDecisionId()).isEqualTo("EXT_AUDIT");
        assertThat(savedEntity.getTarget()).hasSize(255);
        assertThat(savedEntity.getAfterSnapshot()).isNull();

        assertThat(created.id()).isEqualTo(101L);
        assertThat(created.target()).isEqualTo(longTarget);
        assertThat(created.classId()).isEqualTo("cpm-class-123");
        assertThat(created.termId()).isNull();
        assertThat(created.courseId()).isNull();
        assertThat(created.before()).containsEntry("imported", 0);
        assertThat(created.after()).isEmpty();
        assertThat(created.actor().roles()).containsExactly("student");

        when(logRepository.findFirstBySystemCodeAndDecisionIdAndRequestId(
                "course-project-management", "EXT_AUDIT", "req-uuid-1"))
                .thenReturn(Optional.of(savedEntity));
        IdentityAuditLogData replayed = identityService.writeAuditLog(
                7L,
                "course-project-management",
                "req-uuid-1",
                "roster_changed",
                "class",
                "cpm-class-123",
                longTarget,
                Map.of("imported", 0),
                null,
                "127.0.0.1");

        assertThat(replayed.id()).isEqualTo(101L);
        verify(logRepository).save(any(IamLog.class));
    }

    @Test
    @DisplayName("FR-45 外部审计日志查询支持按解密操作人姓名、目标模糊过滤与已删除操作人保留")
    @SuppressWarnings("unchecked")
    void pageAuditLogsFiltersByActorAndTargetAndMarksDeletedActor() {
        IamUser operator = TestFixtures.user(1L, "NORMAL", crypto, "pwd");
        IamUser deletedActor = TestFixtures.user(9L, "SEALED", crypto, "pwd");
        deletedActor.setUserNo("T1009");
        deletedActor.setNameEnc(crypto.encrypt("离职教师"));
        deletedActor.setIsDeleted(1);
        when(userRepository.findById(1L)).thenReturn(Optional.of(operator));
        when(userRepository.findById(9L)).thenReturn(Optional.of(deletedActor));
        when(systemRepository.findBySystemCode("course-project-management"))
                .thenReturn(Optional.of(system));
        when(permissionRepository.findBySystemIdAndPermissionCode(10L, "AV"))
                .thenReturn(Optional.empty());
        when(permissionRepository.findBySystemIdAndPermissionCode(10L, "MLG"))
                .thenReturn(Optional.empty());
        when(decisionService.getUserRoleCodes(1L)).thenReturn(Set.of("teacher"));

        IamLog match = new IamLog();
        match.setLogId(201L);
        match.setLogType("ACTION");
        match.setUserId(9L);
        match.setUsername("T1009");
        match.setAction("score_changed");
        match.setOperateTime(LocalDateTime.of(2026, 11, 24, 18, 0));
        match.setSuccess(1);
        match.setSystemCode("course-project-management");
        match.setDecisionId("EXT_AUDIT");
        match.setTarget("1-01 星火队");
        match.setDetail("{\"objectType\":\"course\",\"objectId\":\"cpm-course-88\","
                + "\"target\":\"1-01 星火队 · 最终答辩\",\"courseId\":\"cpm-course-88\"}");

        IamLog nonMatch = new IamLog();
        nonMatch.setLogId(200L);
        nonMatch.setLogType("ACTION");
        nonMatch.setUserId(1L);
        nonMatch.setUsername("u1");
        nonMatch.setAction("milestone_changed");
        nonMatch.setOperateTime(LocalDateTime.of(2026, 11, 24, 17, 0));
        nonMatch.setSuccess(1);
        nonMatch.setSystemCode("course-project-management");
        nonMatch.setDecisionId("EXT_AUDIT");
        nonMatch.setTarget("里程碑一");

        when(logRepository.findAll(any(Specification.class), any(Sort.class)))
                .thenReturn(List.of(match, nonMatch));

        IdentityPageSlice<IdentityAuditLogData> page = identityService.pageAuditLogs(
                1L, "course-project-management", "离职", null, "答辩", null, null, 1, 20);

        assertThat(page.total()).isEqualTo(1L);
        assertThat(page.items()).hasSize(1);
        IdentityAuditLogData item = page.items().get(0);
        assertThat(item.id()).isEqualTo(201L);
        assertThat(item.courseId()).isEqualTo("cpm-course-88");
        assertThat(item.target()).isEqualTo("1-01 星火队 · 最终答辩");
        assertThat(item.at()).isEqualTo(OffsetDateTime.of(2026, 11, 24, 18, 0, 0, 0, ZoneOffset.ofHours(8)));
        assertThat(item.actor().name()).isEqualTo("离职教师");
        assertThat(item.actor().deleted()).isTrue();
        assertThat(item.actor().roles()).isEmpty();
    }

    @Test
    @DisplayName("FR-45 纯学生无权查询统一身份审计日志且时间倒挂返回参数错误")
    void pageAuditLogsRejectsStudentAndInvertedTimeRange() {
        IamUser student = TestFixtures.user(7L, "NORMAL", crypto, "pwd");
        when(userRepository.findById(7L)).thenReturn(Optional.of(student));
        when(systemRepository.findBySystemCode("course-project-management"))
                .thenReturn(Optional.of(system));
        when(permissionRepository.findBySystemIdAndPermissionCode(10L, "AV"))
                .thenReturn(Optional.empty());
        when(permissionRepository.findBySystemIdAndPermissionCode(10L, "MLG"))
                .thenReturn(Optional.empty());
        when(decisionService.getUserRoleCodes(7L)).thenReturn(Set.of("student"));

        assertThatThrownBy(() -> identityService.pageAuditLogs(
                7L, "course-project-management", null, null, null, null, null, 1, 20))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getCode())
                .isEqualTo(ErrorCode.NO_FUNCTION_PERM.getCode());

        verify(logRepository, never()).findAll(any(Specification.class), any(Sort.class));

        IamPermission av = new IamPermission();
        av.setId(500L);
        av.setSystemId(10L);
        av.setPermissionCode("AV");
        av.setStatus(IamPermission.STATUS_ACTIVE);
        av.setIsDeleted(0);
        when(permissionRepository.findBySystemIdAndPermissionCode(10L, "AV"))
                .thenReturn(Optional.of(av));
        when(decisionService.hasPermission(7L, "course-project-management", 500L))
                .thenReturn(true);

        OffsetDateTime from = OffsetDateTime.parse("2026-11-25T10:00:00+08:00");
        OffsetDateTime to = OffsetDateTime.parse("2026-11-24T10:00:00+08:00");
        assertThatThrownBy(() -> identityService.pageAuditLogs(
                7L, "course-project-management", null, null, null, from, to, 1, 20))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getCode())
                .isEqualTo(ErrorCode.PARAM_INVALID.getCode());
    }

    private IamOrg org(Long id, Long parentId, String code, String name) {
        IamOrg org = new IamOrg();
        org.setId(id);
        org.setParentId(parentId);
        org.setOrgCode(code);
        org.setOrgName(name);
        org.setSortNo(0);
        org.setStatus(IamOrg.STATUS_ACTIVE);
        org.setIsDeleted(0);
        return org;
    }
}
