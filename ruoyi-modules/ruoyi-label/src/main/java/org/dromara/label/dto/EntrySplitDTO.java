package org.dromara.label.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.io.Serializable;

/**
 * 上传数据「分割数据」请求。
 *
 * <p>一次上传的数据是一个数据集合包，上传结束后可选择：</p>
 * <ul>
 *   <li>splitMode = whole：不分割，整包数据即为 1 个条目；</li>
 *   <li>splitMode = count：按「每份数据量」平均分割（每份 size 条，最后一组为余数），需传 size；</li>
 *   <li>splitMode = parts：按「份数」平均分割（余数优先分给前面的条目），需传 parts。</li>
 * </ul>
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EntrySplitDTO implements Serializable {

    /** 分割方式：whole/count/parts，为空按 whole 处理 */
    private String splitMode;
    /** 每份数据量（splitMode=count 时必填且 >=1） */
    private Integer size;
    /** 分割份数（splitMode=parts 时必填且 >=1） */
    private Integer parts;
}
