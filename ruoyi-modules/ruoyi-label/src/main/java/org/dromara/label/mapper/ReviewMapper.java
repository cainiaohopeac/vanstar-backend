package org.dromara.label.mapper;

import org.apache.ibatis.annotations.Param;
import org.dromara.label.dto.AssetReviewContextDTO;
import org.dromara.label.dto.EntryReviewTargetDTO;
import org.dromara.label.vo.ReviewRecordVO;

import java.util.List;
import java.util.Map;

/**
 * 审核域数据访问（label_job/label_review_record/label_annotation/label_task）
 */
public interface ReviewMapper {

    Long selectJobTaskId(@Param("jobId") Long jobId);

    Long selectLatestAnnotationIdByJob(@Param("jobId") Long jobId);

    int updateJobRejected(@Param("jobId") Long jobId, @Param("userId") Long userId);

    int updateJobReviewState(@Param("jobId") Long jobId,
                             @Param("status") String status,
                             @Param("qcStatus") String qcStatus,
                             @Param("userId") Long userId);

    int insertReviewRecord(Map<String, Object> p);

    /**
     * 单个数据资产的审核上下文（reviewerId + reviewStatus + entryId），
     * 用于审核员修订模式校验与审计写入。
     *
     * <p>仅按主键 {@code label_asset.id} 查询，无额外索引开销；资产不存在或已删除时返回 null。</p>
     */
    AssetReviewContextDTO selectAssetReviewContext(@Param("assetId") Long assetId);

    int updateTaskCompletedCountByJob(@Param("jobId") Long jobId);

    /**
     * 作业级审核/验收后同步作业内条目的审核维度。
     *
     * <p>审核通过不修改标注状态（labelStatus 传 null）；审核驳回时把 labelStatus 置为未修订并累加不通过次数。</p>
     */
    int updateAssetsReviewByJob(@Param("jobId") Long jobId,
                                @Param("reviewStatus") String reviewStatus,
                                @Param("labelStatus") String labelStatus,
                                @Param("incrementReject") boolean incrementReject,
                                @Param("reviewerId") Long reviewerId,
                                @Param("userId") Long userId);

    /** 作业级审核/验收后，把审核结果回刷到作业内数据资产所属的数据条目（数据集合包）。 */
    int syncEntriesFromAssetsByJob(@Param("jobId") Long jobId, @Param("userId") Long userId);

    List<ReviewRecordVO> selectReviewRecords(@Param("jobId") Long jobId);

    // ---------------- 条目级审核（审核状态维度，条目 = 数据集合包） ----------------

    /**
     * 条目级审核列表（跨任务）。
     *
     * @param taskId  任务过滤（可空）
     * @param status  页签：pending/reviewing/re_reviewing/passed/rejected/mine（可空=全部）
     * @param keyword 关键字（任务编号/名称、条目名/条目编号、批次名）
     * @param userId  当前用户（status=mine 时按 reviewer_id 过滤）
     */
    List<org.dromara.label.vo.ReviewEntryVO> selectReviewEntries(@Param("taskId") Long taskId,
                                                                 @Param("status") String status,
                                                                 @Param("keyword") String keyword,
                                                                 @Param("userId") Long userId,
                                                                 @Param("offset") int offset,
                                                                 @Param("limit") int limit);

    long countReviewEntries(@Param("taskId") Long taskId,
                            @Param("status") String status,
                            @Param("keyword") String keyword,
                            @Param("userId") Long userId);

    /** 条目审核目标快照（校验 + 通知 + 审核记录），入参与结果均为数据条目ID。 */
    List<EntryReviewTargetDTO> selectEntryReviewTargets(@Param("assetIds") List<Long> entryIds);

    /** 领取审核：把「已标注/已修订且未审核」的条目领取为当前审核人（审核中/复审中）。 */
    int updateEntryReviewClaim(@Param("assetIds") List<Long> entryIds, @Param("userId") Long userId);

    /** 释放审核领取：本人持有的审核中/复审中条目回到未审核（跳过/放弃节点时使用）。 */
    int releaseEntryReviewClaim(@Param("assetIds") List<Long> entryIds, @Param("userId") Long userId);

    /** 审核通过：不修改标注状态，仅把审核状态置为已通过。 */
    int updateEntryReviewApprove(@Param("assetIds") List<Long> entryIds, @Param("userId") Long userId);

    /** 审核驳回：标注状态置为未修订，审核状态置为审核不通过/复审不通过，不通过次数+1。 */
    int updateEntryReviewReject(@Param("assetIds") List<Long> entryIds, @Param("userId") Long userId);

    /** 条目级审核流转后，把条目的审核状态同步到包内全部数据资产。 */
    int syncAssetsFromEntries(@Param("assetIds") List<Long> entryIds);

    /** 单个条目的审核历史。 */
    List<ReviewRecordVO> selectReviewRecordsByAsset(@Param("assetId") Long entryId);
}
