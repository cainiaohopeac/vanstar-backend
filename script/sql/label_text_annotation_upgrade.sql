-- ============================================================================
-- 文本标注类型（text）建表脚本
--
-- 与 image / point_cloud 并列为第三种数据类型，独立一套标签与标注存储：
--   · 文本标签独立建表（label_text_class_*），不与视觉标签 label_class 混用，
--     避免「同一个 class_id 指向两张不同含义的表」；
--   · 实体用字符偏移（全文级）表达，不复用 label_annotation_object 的
--     geometry_json / attr_json —— 文本没有几何概念，塞进 JSON 反而难以检索与统计。
--
-- 索引说明：务必保留。此前 label_annotation_object 缺少 (annotation_id, deleted)
-- 索引，导致保存时每次软删都要全表扫描，加载同样受影响。这里建表即带上。
-- ============================================================================

-- ---------------- 文本标签集 ----------------
CREATE TABLE IF NOT EXISTS label_text_class_set (
  id          bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '标签集ID',
  project_id  bigint(20)   NOT NULL COMMENT '项目ID',
  set_name    varchar(100) NOT NULL COMMENT '标签集名称',
  description varchar(500)          DEFAULT NULL COMMENT '标签集描述',
  status      varchar(20)           DEFAULT '0' COMMENT '状态（0正常 1停用）',
  created_by  bigint(20)            DEFAULT NULL COMMENT '创建人',
  updated_by  bigint(20)            DEFAULT NULL COMMENT '更新人',
  create_time datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted     tinyint(4)            DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (id),
  KEY idx_tcs_project (project_id, deleted)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT ='文本标签集表';

-- ---------------- 文本标签（支持层级 + 节点类型） ----------------
CREATE TABLE IF NOT EXISTS label_text_class (
  id            bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '标签ID',
  project_id    bigint(20)   NOT NULL COMMENT '项目ID',
  class_set_id  bigint(20)            DEFAULT NULL COMMENT '所属标签集ID（label_text_class_set.id）',
  parent_id     bigint(20)            DEFAULT NULL COMMENT '父标签ID，NULL 表示顶层（层级标签体系）',
  node_type     varchar(20)  NOT NULL DEFAULT 'entity' COMMENT '节点类型：entity实体 / relation关系 / classification情感分类',
  label_name    varchar(100) NOT NULL COMMENT '标签名称',
  color         varchar(20)           DEFAULT NULL COMMENT '标签颜色（前端高亮用）',
  sort_order    int(11)               DEFAULT '0' COMMENT '排序（同时决定 0-9 快捷键顺序）',
  status        varchar(20)           DEFAULT '0' COMMENT '状态（0正常 1停用）',
  created_by    bigint(20)            DEFAULT NULL COMMENT '创建人',
  updated_by    bigint(20)            DEFAULT NULL COMMENT '更新人',
  create_time   datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time   datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted       tinyint(4)            DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (id),
  KEY idx_tc_set (class_set_id, deleted),
  KEY idx_tc_project (project_id, deleted),
  KEY idx_tc_parent (parent_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT ='文本标签表';

-- ---------------- 文本实体标注 ----------------
CREATE TABLE IF NOT EXISTS label_text_entity (
  id             bigint(20)    NOT NULL AUTO_INCREMENT COMMENT '实体ID',
  annotation_id  bigint(20)    NOT NULL COMMENT '所属标注（label_annotation.id）',
  entity_key     varchar(80)   NOT NULL COMMENT '实体编号（前端生成，同一标注内唯一）',
  class_id       bigint(20)    NOT NULL COMMENT '标签ID（label_text_class.id）',
  start_offset   int(11)       NOT NULL COMMENT '起始字符偏移（全文级，闭区间）',
  end_offset     int(11)       NOT NULL COMMENT '结束字符偏移（全文级，开区间）',
  entity_text    varchar(1000)          DEFAULT NULL COMMENT '实体文本快照（列表展示与导出用，避免回读全文截取）',
  sentence_index int(11)                DEFAULT NULL COMMENT '所属句序号（当前约束：实体不跨句）',
  created_by     bigint(20)             DEFAULT NULL COMMENT '创建人',
  updated_by     bigint(20)             DEFAULT NULL COMMENT '更新人',
  create_time    datetime               DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time    datetime               DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted        tinyint(4)             DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (id),
  KEY idx_te_annotation (annotation_id, deleted),
  KEY idx_te_sentence (annotation_id, sentence_index, deleted)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT ='文本实体标注表';

-- ---------------- 文本情感分类（句级 + 条目级共用一张表） ----------------
CREATE TABLE IF NOT EXISTS label_text_sentiment (
  id             bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '情感标注ID',
  annotation_id  bigint(20)   NOT NULL COMMENT '所属标注（label_annotation.id）',
  level_type     varchar(20)  NOT NULL COMMENT '粒度：entry条目级 / sentence句级',
  sentence_index int(11)               DEFAULT NULL COMMENT '句序号（条目级为 NULL）',
  class_id       bigint(20)            DEFAULT NULL COMMENT '情感标签ID（label_text_class.id，node_type=classification）',
  remark         varchar(500)          DEFAULT NULL COMMENT '备注',
  created_by     bigint(20)            DEFAULT NULL COMMENT '创建人',
  updated_by     bigint(20)            DEFAULT NULL COMMENT '更新人',
  create_time    datetime              DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  update_time    datetime              DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  deleted        tinyint(4)            DEFAULT '0' COMMENT '删除标记',
  PRIMARY KEY (id),
  KEY idx_ts_annotation (annotation_id, deleted),
  KEY idx_ts_level (annotation_id, level_type, sentence_index, deleted)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT ='文本情感分类表（条目级与句级）';
