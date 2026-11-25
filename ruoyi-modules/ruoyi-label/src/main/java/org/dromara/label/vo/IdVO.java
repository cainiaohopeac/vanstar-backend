package org.dromara.label.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 通用单值结果 VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IdVO implements Serializable {

    private Long id;
}
