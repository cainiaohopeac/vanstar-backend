package org.dromara.label.dto;

import java.io.Serializable;
import lombok.Data;

/**
 * nuScenes 元数据解析请求（POST /nuscenes/parse 入参）。
 *
 * <p>对一条上传数据执行：读取 MinIO 中的元数据归档 -&gt; 建立点云资产与相机图关联
 * -&gt; 自动生成预标注。用于上传链路自动触发失败后的手动重试。</p>
 */
@Data
public class NuscenesParseDTO implements Serializable {

    /** 上传数据ID（必填，定位元数据归档与数据资产范围） */
    private Long uploadId;
}
