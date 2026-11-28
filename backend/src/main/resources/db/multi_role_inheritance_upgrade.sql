-- 角色多继承升级脚本（可重复执行）
-- 1. 新建角色多继承边表；2. 把旧的 iam_role.parent_id 迁移为继承边。
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS iam_role_parent (
    id         BIGINT   NOT NULL AUTO_INCREMENT,
    role_id    BIGINT   NOT NULL COMMENT '子角色',
    parent_id  BIGINT   NOT NULL COMMENT '父角色',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_parent (role_id, parent_id),
    KEY idx_role_parent_parent (parent_id),
    CONSTRAINT fk_role_parent_child FOREIGN KEY (role_id) REFERENCES iam_role (id),
    CONSTRAINT fk_role_parent_ancestor FOREIGN KEY (parent_id) REFERENCES iam_role (id),
    CONSTRAINT chk_role_parent_not_self CHECK (role_id <> parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色多继承边';

INSERT IGNORE INTO iam_role_parent (role_id, parent_id)
SELECT id, parent_id FROM iam_role WHERE parent_id IS NOT NULL;
