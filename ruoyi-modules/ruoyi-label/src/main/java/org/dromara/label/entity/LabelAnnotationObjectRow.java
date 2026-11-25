package org.dromara.label.entity;

import java.io.Serializable;
import lombok.Data;

/**
 * 标注对象批量写入行（label_annotation_object）。
 *
 * <p>保存草稿时对象数是「帧数 × 轨迹数」——关键帧插值会把每个轨迹铺满中间帧，
 * 上千行很常见。这些行原本用 {@code Map<String, Object>} 当 MyBatis 入参：
 * 每行都要 new 一个 HashMap、按字符串 key 取值，拼错 key 也只有运行到那一行才报错。
 * 改成强类型实体后，字段在编译期就固定，批量插入时 MyBatis 直接走 Bean 的属性缓存，
 * 也省掉上千个 HashMap 的分配。
 *
 * <p>注意只用于写入，不含主键：多行 VALUES 的 INSERT 只会回填最后一行的自增 id，
 * 本表的调用方不需要 id。
 */
@Data
public class LabelAnnotationObjectRow implements Serializable {

  /** 所属标注存档ID（label_annotation.id） */
  private Long annotationId;
  /** 对象唯一标识（前端生成，同帧内不重复） */
  private String objectKey;
  /** 对象类型：立体框 / 多边形 / 点云实例等 */
  private String objectType;
  /** 标签类别ID */
  private Long classId;
  /** 合组键，NULL 表示未分组 */
  private String groupKey;
  /** 几何 JSON 文本 */
  private String geometryJson;
  /** 属性 JSON 文本（含 assetId、trackId、interpolated 等） */
  private String attrJson;
  /** 轨迹 Token（跨帧实例关联），NULL 表示每帧独立 */
  private String trackToken;
  /** 操作人（同时写入 created_by / updated_by） */
  private Long userId;
}
