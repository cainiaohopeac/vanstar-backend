package org.dromara.label.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 保存序列标注请求
 */
@Data
public class SequenceAnnotationSaveDTO implements Serializable {

    @NotNull(message = "缺少 frameId")
    private Long frameId;

    private List<AnnotationObjectDTO> objects;
}
