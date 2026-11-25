-- 标签类别新增「关键点模板」配置（骨架：点名列表 + 连线边）
-- 用于图片关键点标注：tool_type='keypoint' 时，可为该类别配置一组语义点与骨架连线，
-- 画布按模板逐点打点并绘制骨架；无模板时退化为单点标注。
-- 结构：{ "points": ["左眼","右眼","鼻尖",...], "edges": [[0,1],[1,2],...] }
ALTER TABLE `label_class`
    ADD COLUMN `keypoint_template_json` json DEFAULT NULL
        COMMENT '关键点模板({points:[名称],edges:[[i,j],...]})，tool_type=keypoint 时生效'
        AFTER `default_size_h`;
