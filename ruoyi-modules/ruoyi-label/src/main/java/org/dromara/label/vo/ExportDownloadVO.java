package org.dromara.label.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 导出下载 VO（下载地址 + 数据集清单）
 */
@Data
public class ExportDownloadVO implements Serializable {

    private Long id;
    private String status;
    private String downloadUrl;
    private String fileName;
    private ExportManifestVO manifest;
}
