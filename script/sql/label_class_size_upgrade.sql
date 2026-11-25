-- ======================================================================
-- 标签「3D 框初始尺寸」配置升级脚本
-- 背景：点云立体框（cuboid）类别可在「项目详情 - 设置标签」时预设初始长/宽/高，
--       标注下 3D 框时按该尺寸作为初始框（中心由拖拽决定），减少手动微调。
-- 说明：
--   default_size_l/w/h 对应立体框几何 size 的 x(长)/y(宽)/z(高)，单位米；
--   为 NULL 表示不预设，下框时沿用拖拽尺寸。仅 tool_type 为立体框/框选时生效。
--   执行一次即可，重复执行会因列已存在而报错（可忽略）。
-- ======================================================================

ALTER TABLE `label_class`
    ADD COLUMN `default_size_l` double DEFAULT NULL COMMENT '3D框初始尺寸-长(米) X',
    ADD COLUMN `default_size_w` double DEFAULT NULL COMMENT '3D框初始尺寸-宽(米) Y',
    ADD COLUMN `default_size_h` double DEFAULT NULL COMMENT '3D框初始尺寸-高(米) Z';
