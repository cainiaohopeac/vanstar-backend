package org.dromara.label.service;

import java.util.List;
import org.dromara.label.dto.NuscenesMetaBundle;
import org.dromara.label.vo.PreAnnotationAssetVO;
import org.dromara.label.vo.PreAnnotationResultVO;
import org.dromara.label.vo.PreAnnotationVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 预标注域服务：解析外部标注文件，按文件名匹配数据资产后落库，供标注页作为初始框引入。
 *
 * <p>支持多格式，由 {@code format} 分派到对应解析器：
 * nuScenes 压缩包（3D 立体框）、COCO、YOLO、平台点位导出。</p>
 */
public interface PreAnnotationService {

    /**
     * 对整条上传数据执行预标注：解析标注文件，匹配该上传数据下所有数据资产。
     *
     * @param uploadId 上传数据ID
     * @param file     标注文件（nuScenes 压缩包 / COCO json / YOLO 压缩包 / 点位 json）
     * @param format   格式标识：nuscenes / coco / yolo / point；为空按 nuscenes 处理
     */
    PreAnnotationResultVO preAnnotateUpload(Long uploadId, MultipartFile file, String format);

    /**
     * 对单个数据资产执行预标注：解析标注文件，仅匹配该数据资产。
     *
     * @param assetId 数据资产ID
     * @param file    标注文件（nuScenes 压缩包 / COCO json / YOLO 压缩包 / 点位 json）
     * @param format  格式标识：nuscenes / coco / yolo / point；为空按 nuscenes 处理
     */
    PreAnnotationResultVO preAnnotateAsset(Long assetId, MultipartFile file, String format);

    /**
     * 查询数据资产的预标注结果（含预标注框）。
     *
     * @param assetId 数据资产ID
     */
    PreAnnotationVO preAnnotation(Long assetId);

    /**
     * 基于已解析的元数据包对资产集合执行预标注（nuScenes 导入链路自动触发）。
     *
     * @param assets 待预标注的数据资产集合（通常为一整条上传数据下的全部资产）
     * @param bundle 已解析的 nuScenes 元数据包（导入归档或压缩包解析结果）
     */
    PreAnnotationResultVO preAnnotateAssets(List<PreAnnotationAssetVO> assets, NuscenesMetaBundle bundle);
}
