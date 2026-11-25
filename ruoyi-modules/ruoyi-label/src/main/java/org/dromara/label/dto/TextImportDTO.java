package org.dromara.label.dto;

import java.io.Serializable;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 文本导入的参数（配合 multipart 的文件部分一起提交）。
 *
 * <p>文件本身走 {@code MultipartFile}，其余参数收进这个实体：
 * 参数多于两个时统一用实体承载，避免接口签名随需求膨胀成一长串 {@code @RequestParam}。
 */
@Data
public class TextImportDTO implements Serializable {

  /** 项目ID */
  @NotNull(message = "projectId 不能为空")
  private Long projectId;

  /** 批次ID（可为空，为空时归入项目默认批次） */
  private Long batchId;
}
