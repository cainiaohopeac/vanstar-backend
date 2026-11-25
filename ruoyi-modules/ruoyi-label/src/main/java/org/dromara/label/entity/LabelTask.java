package org.dromara.label.entity;

import java.io.Serializable;
import lombok.Data;

/**
 * 任务（label_task）写入参数实体：创建/更新任务时 Service 与 Mapper 的对接载体。
 * userId 为当前操作人，落库到 created_by / updated_by。
 */
@Data
public class LabelTask implements Serializable {

  /** 任务ID（更新时必传；创建时由自增主键回填） */
  private Long id;
  /** 所属项目ID */
  private Long projectId;
  /** 标签集ID */
  private Long classSetId;
  /** 任务号（创建时由服务端生成） */
  private String taskNo;
  /** 任务名称 */
  private String taskName;
  /** 数据类型（image/point_cloud） */
  private String dataType;
  /** 创建人ID（仅创建时写入） */
  private Long creatorId;
  /** 主承接团队ID（多团队下发时取第一个） */
  private Long teamId;
  /** 条目总数 */
  private Integer assetCount;
  /** 是否引入预标注（0 关闭 / 1 开启）：开启后标注工作台把预标注框注入画布 */
  private Integer preAnnotationEnabled;
  /** 操作人ID（写 created_by / updated_by） */
  private Long userId;
}
