package org.dromara.label.entity;

import java.io.Serializable;
import lombok.Data;

/**
 * 标注存档的「assetId → annotationId」对照行（label_annotation 的两列投影）。
 *
 * <p>批量保存时用它一次取回整作业的对照关系，避免每帧各查一次。
 * 用强类型而不是 {@code Map<String, Object>}：Map 的 key 大小写取决于
 * MyBatis 与 JDBC 驱动对列别名的处理，写错只有运行到那一行才炸，
 * 且泛型擦除让编译期完全看不出来。
 */
@Data
public class LabelAnnotationIdRow implements Serializable {

  /** 数据资产ID */
  private Long assetId;
  /** 该资产对应的标注存档ID */
  private Long annotationId;
}
