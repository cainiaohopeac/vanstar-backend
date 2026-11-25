package org.dromara.label.mapper;

import org.apache.ibatis.annotations.Param;
import org.dromara.label.vo.DeliveryRecordVO;
import org.dromara.label.vo.ExportAnnotationVO;
import org.dromara.label.vo.ExportAssetVO;
import org.dromara.label.vo.ExportBatchVO;
import org.dromara.label.vo.ExportJobVO;

import java.util.List;
import java.util.Map;

/**
 * 导出交付域数据访问（label_export_job/label_delivery_record/label_asset/label_annotation 等）
 * <p>
 * 「合格数据」定义：条目标注完成且通过全部审核节点（label_data_entry.label_status ∈ labeled/revised
 * 且 review_status=passed），即任务流转至「合格数据」节点（qualified）后沉淀的条目。
 * 导出粒度为批次：仅可导出「已完成批次」（批次内全部条目均合格）。
 * </p>
 */
public interface ExportMapper {

    List<ExportJobVO> selectExports();

    ExportJobVO selectExport(@Param("id") Long id);

    long countAssetsByProject(@Param("projectId") Long projectId);

    /** 校验项目存在且未删除 */
    int countProjectById(@Param("projectId") Long projectId);

    /** 查询项目名称（导出清单展示用） */
    String selectProjectName(@Param("projectId") Long projectId);

    /** 校验批次存在且属于指定项目 */
    int countBatchInProject(@Param("projectId") Long projectId, @Param("batchId") Long batchId);

    /** 批次名称（COALESCE 批次名/批次号） */
    String selectBatchName(@Param("batchId") Long batchId);

    /** 批次内未完成（未通过全部审核）的条目数：0 且有合格资产表示批次已完成可导出 */
    long countUnfinishedEntries(@Param("batchId") Long batchId);

    /** 项目下可导出（已完成）的批次列表 */
    List<ExportBatchVO> selectExportableBatches(@Param("projectId") Long projectId);

    /** 统计合格数据资产数（用于创建导出前的门槛校验；batchId 非空时限定批次范围） */
    long countQualifiedAssets(@Param("projectId") Long projectId, @Param("batchId") Long batchId);

    /** 统计多批次合格数据资产数（nuScenes 多批次合并导出门槛校验） */
    long countQualifiedAssetsMulti(@Param("projectId") Long projectId, @Param("batchIds") List<Long> batchIds);

    /** 创建导出任务（status 由调用方传入：processing/waiting） */
    int insertExportJob(Map<String, Object> p);

    /** 导出成功回写：状态、下载地址、实际资产数（仅 processing 状态可回写，防止覆盖取消结果） */
    int updateExportSuccess(@Param("id") Long id, @Param("resultUrl") String resultUrl,
                            @Param("assetCount") long assetCount, @Param("userId") Long userId);

    /** 导出失败回写：状态与失败原因（仅 processing 状态可回写） */
    int updateExportFailed(@Param("id") Long id, @Param("errorMessage") String errorMessage,
                           @Param("userId") Long userId);

    int insertDeliveryRecord(Map<String, Object> p);

    int updateExportCanceled(@Param("id") Long id, @Param("userId") Long userId);

    List<ExportAssetVO> selectExportAssets(@Param("projectId") Long projectId,
                                           @Param("batchId") Long batchId,
                                           @Param("batchIds") List<Long> batchIds);

    /** 查询导出范围内的全部帧状态（含无效帧），供平台属性 sidecar 记录帧有效性。 */
    List<ExportAssetVO> selectExportFrameMeta(@Param("projectId") Long projectId,
                                              @Param("batchId") Long batchId,
                                              @Param("batchIds") List<Long> batchIds);

    List<ExportAnnotationVO> selectExportAnnotations(@Param("projectId") Long projectId,
                                                     @Param("batchId") Long batchId,
                                                     @Param("batchIds") List<Long> batchIds);

    // ---------------- 文本导出（TEXT 格式）：resultType=map，key 即 JSON 字段名 ----------------

    List<Map<String, Object>> selectExportTextEntries(@Param("projectId") Long projectId,
                                                      @Param("batchIds") List<Long> batchIds);

    List<Map<String, Object>> selectExportTextEntities(@Param("projectId") Long projectId,
                                                       @Param("batchIds") List<Long> batchIds);

    List<Map<String, Object>> selectExportTextSentiments(@Param("projectId") Long projectId,
                                                         @Param("batchIds") List<Long> batchIds);

    /** 多批次合格资产/标注查询（nuScenes 多批次合并导出：资产取自指定批次集合） */
    List<ExportAssetVO> selectExportAssetsMulti(@Param("projectId") Long projectId,
                                                @Param("batchIds") List<Long> batchIds);

    List<ExportAnnotationVO> selectExportAnnotationsMulti(@Param("projectId") Long projectId,
                                                          @Param("batchIds") List<Long> batchIds);

    List<DeliveryRecordVO> selectDeliveryRecords();
}
