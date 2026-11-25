package org.dromara.label.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 续传导入返回 VO
 */
@Data
public class ImportResumeVO implements Serializable {

    /** 导入记录 */
    private ImportRecordVO record;
    /** 已上传的分片序号列表 */
    private List<Integer> uploadedPartNumbers;
}
