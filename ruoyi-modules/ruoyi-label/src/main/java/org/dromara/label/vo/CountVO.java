package org.dromara.label.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 通用计数 VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CountVO implements Serializable {

    private long count;
}
