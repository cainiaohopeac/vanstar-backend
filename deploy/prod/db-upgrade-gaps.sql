ALTER TABLE `ry-vue`.`label_object_track`
    ADD COLUMN `gaps_json` json DEFAULT NULL
    COMMENT '虚框区间集合（资产ID区间数组；[{from,to,dir}]，dir=fwd/bwd）'
    AFTER `holes_json`;
