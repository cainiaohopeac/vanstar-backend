package org.dromara.label.service;

import org.dromara.label.dto.NuscenesArchiveRequest;
import org.dromara.label.dto.NuscenesMetaBundle;
import org.dromara.label.dto.NuscenesParseDTO;
import org.dromara.label.entity.LabelNuscenesMeta;
import org.dromara.label.vo.NuscenesParseResultVO;

/**
 * nuScenes 数据集导入域服务：
 * 元数据归档（上传链路）→ 解析建映射（点云-相机关联）→ 自动预标注。
 */
public interface NuscenesImportService {

    /**
     * 创建元数据流式收集器（ingestZip 解压扫描时逐条目喂入）。
     *
     * @param request 归档定位上下文（项目/批次/上传数据/来源包名/操作人）
     */
    NuscenesMetaCollector newCollector(NuscenesArchiveRequest request);

    /**
     * 解析一条上传数据的 nuScenes 元数据归档：
     * 读取 MinIO 归档 → 建立点云资产与同帧相机图关联 → 自动生成预标注。
     * 用于上传链路自动触发失败后的手动重试（POST /nuscenes/parse）。
     *
     * @param dto 解析请求（uploadId 必填）
     */
    NuscenesParseResultVO parseUpload(NuscenesParseDTO dto);

    /**
     * 读取一条上传数据的元数据归档并解析为元数据包（原格式导出用）。
     *
     * @param uploadId 上传数据ID
     */
    NuscenesMetaBundle loadBundle(Long uploadId);

    /**
     * 按上传数据ID查询元数据归档记录（原格式导出定位 MinIO 对象用）。
     *
     * @param uploadId 上传数据ID
     */
    LabelNuscenesMeta metaOfUpload(Long uploadId);
}
