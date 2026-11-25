-- =====================================================================
-- 标签属性表 label_class_attribute 升级
--
-- 1) 新增 max_length INT DEFAULT NULL
--    仅 attr_type='text' 时使用，记录用户输入的最大字符数上限（默认 200）
-- 2) 新增 uk_class_attr_name(class_id, attr_name) 唯一键
--    让同一个标签下"按属性名"幂等 upsert；
--    ProjectMapper.xml 中 insertClassAttribute 改为
--    INSERT ... ON DUPLICATE KEY UPDATE ... ，前端重复保存同名属性不会插出多条
--    本升级幂等可重复执行（Information_Schema 判重 + 失败重试）
-- =====================================================================

-- 1) max_length 列（仅当不存在时加）
SET @add_col = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM Information_Schema.COLUMNS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'label_class_attribute'
              AND COLUMN_NAME = 'max_length'
        ),
        'SELECT 1',
        'ALTER TABLE label_class_attribute ADD COLUMN max_length INT DEFAULT NULL COMMENT ''文本最大字符数（仅 attr_type=text 时生效，默认上限 200）'''
    )
);
PREPARE stmt FROM @add_col;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 2) uk_class_attr_name 唯一键（仅当不存在时加）
SET @add_idx = (
    SELECT IF(
        EXISTS (
            SELECT 1 FROM Information_Schema.STATISTICS
            WHERE TABLE_SCHEMA = DATABASE()
              AND TABLE_NAME = 'label_class_attribute'
              AND INDEX_NAME = 'uk_class_attr_name'
        ),
        'SELECT 1',
        'ALTER TABLE label_class_attribute ADD UNIQUE KEY uk_class_attr_name (class_id, attr_name)'
    )
);
PREPARE stmt FROM @add_idx;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 3) 存量回填：给所有 attr_type='text' 的属性 max_length 默认填 200（NULL → 200）
UPDATE label_class_attribute
SET max_length = 200
WHERE deleted = 0 AND attr_type = 'text' AND max_length IS NULL;

SELECT 'class_attribute max_length upgraded' AS info;