package org.dromara.label.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 数据批次创建 / 重命名请求
 */
@Data
public class BatchSaveDTO {

    /** 批次名称 */
    @NotBlank(message = "批次名称不能为空")
    @Size(max = 200, message = "批次名称不能超过200个字符")
    private String batchName;

    /** 备注 */
    @Size(max = 500, message = "备注不能超过500个字符")
    private String remark;
}
