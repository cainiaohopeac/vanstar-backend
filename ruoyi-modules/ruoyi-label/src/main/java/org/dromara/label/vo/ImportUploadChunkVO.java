package org.dromara.label.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 分片上传结果 VO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImportUploadChunkVO implements Serializable {

    /** 已上传分片数 */
    private Integer uploadedChunks;
    /** 已上传字节数 */
    private Long uploadedBytes;
    /** 总分片数 */
    private Integer totalChunks;
}
