package org.dromara.label.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 保存标注结果 VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SavedCountVO implements Serializable {

    private int savedCount;
    private Long annotationId;
}
