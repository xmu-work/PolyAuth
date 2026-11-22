package com.polyauth.oop.interfaces.vo;

import com.polyauth.oop.application.result.IdentityDepartment;
import com.polyauth.oop.application.result.IdentityDepartmentUser;
import com.polyauth.oop.application.result.IdentityProfile;
import com.polyauth.oop.application.result.IdentityUser;

import java.util.List;

/**
 * 统一身份契约的响应视图集合。
 */
public final class IdentityVOs {

    private IdentityVOs() {
    }

    public record CurrentUser(String subject, List<String> roles,
                              List<String> permissions, String name, String no) {
        public static CurrentUser from(IdentityProfile profile) {
            return new CurrentUser(profile.subject(), profile.roles(),
                    profile.permissions(), profile.name(), profile.no());
        }
    }

    public record PermissionCheck(boolean allowed) {
    }

    public record Department(Long id, String code, String name,
                             Long parentId, boolean hasChildren) {
        public static Department from(IdentityDepartment item) {
            return new Department(item.id(), item.code(), item.name(),
                    item.parentId(), item.hasChildren());
        }
    }

    public record DepartmentPage(int page, int pageSize, long total,
                                 List<Department> items) {
    }

    public record DepartmentUser(String userId, String no, String name,
                                 Long departmentId, String departmentName) {
        public static DepartmentUser from(IdentityDepartmentUser item) {
            return new DepartmentUser(item.userId(), item.no(), item.name(),
                    item.departmentId(), item.departmentName());
        }
    }

    public record DepartmentUserPage(int page, int pageSize, long total,
                                     List<DepartmentUser> items) {
    }

    public record User(String userId, String no, String name) {
        public static User from(IdentityUser user) {
            return new User(user.userId(), user.no(), user.name());
        }
    }
}
