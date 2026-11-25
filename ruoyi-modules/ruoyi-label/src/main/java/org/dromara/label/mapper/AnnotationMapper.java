package org.dromara.label.mapper;

import org.apache.ibatis.annotations.Param;
import org.dromara.label.entity.LabelAnnotationIdRow;
import org.dromara.label.entity.LabelAnnotationObjectRow;
import org.dromara.label.entity.LabelObjectComment;
import org.dromara.label.vo.JobTaskInfoVO;
import org.dromara.label.vo.ObjectCommentVO;
import org.dromara.label.vo.WorkAssetVO;
import org.dromara.label.vo.WorkClassVO;
import org.dromara.label.vo.ObjectTrackVO;
import org.dromara.label.vo.WorkObjectVO;

import java.util.List;
import java.util.Map;

/**
 * 标注域数据访问（label_job_asset/label_asset/label_annotation/label_annotation_object/label_class 等）
 */
public interface AnnotationMapper {

    // ---------------- 标注工作台取数 ----------------

    List<WorkAssetVO> selectJobAssets(@Param("jobId") Long jobId);

    /** 「数据明细」面板：按作业反查任务名称/任务类型/批次名称/条目数量/条目类型 */
    JobTaskInfoVO selectJobTaskInfo(@Param("jobId") Long jobId);

    /**
     * 按作业的 project + media_type 取「整个项目」的全部资产。
     * 仅供项目级回看等场景使用，不要在标注工作台里当空结果兜底：
     * 那会把未领取的条目一并展示出去（越权）。
     *
     * @deprecated 标注工作台请用 {@link #selectJobAssets(Long)}
     */
    @Deprecated
    List<WorkAssetVO> selectProjectAssetsByJob(@Param("jobId") Long jobId);

    /** 点云作业的参考图片：同项目、同来源批次（job.batch_id 为空时不限批次）、media_type=image */
    List<WorkAssetVO> selectJobReferenceImages(@Param("jobId") Long jobId);

    List<WorkObjectVO> selectAnnotationObjects(@Param("jobId") Long jobId);

    // ---------------- 对象轨迹（逐帧 / 关键帧插值） ----------------

    /** 作业内的对象轨迹（存在范围与逐帧控制元数据） */
    List<ObjectTrackVO> selectJobTracks(@Param("jobId") Long jobId);

    /** 轨迹是否已存在（用于判断 insert / update） */
    Long selectTrackId(@Param("trackToken") String trackToken);

    int insertTrack(Map<String, Object> p);

    int updateTrack(Map<String, Object> p);

    /** 轨迹作废：整条移除（非物理删除，保留审计） */
    int deleteTrack(@Param("trackToken") String trackToken, @Param("userId") Long userId);

    /**
     * 作业资产的预标注对象（任务开启预标注引入时工作台注入画布）：
     * 仅返回尚无正式标注（label_annotation）的资产，避免与已保存对象重复。
     */
    List<WorkObjectVO> selectJobPreAnnotationObjects(@Param("jobId") Long jobId);

    List<WorkClassVO> selectJobClasses(@Param("jobId") Long jobId);

    Long selectAnnotationIdByJobAsset(@Param("jobId") Long jobId, @Param("assetId") Long assetId);

    Long selectJobIdByAsset(@Param("assetId") Long assetId);

    // ---------------- 保存标注 ----------------

    int insertAnnotation(Map<String, Object> p);

    int updateAnnotation(Map<String, Object> p);

    /**
     * 按「归属」软删当前编辑 asset 的全部对象（不限单个 annotation 头）：
     * 覆盖 attr_json.assetId 显式指向该 asset 的跨头拷贝，以及未携带 assetId 且挂在 asset 自己头下的老数据。
     */
    int softDeleteObjectsByAsset(@Param("jobId") Long jobId,
                                 @Param("assetId") Long assetId,
                                 @Param("userId") Long userId);

    /**
     * 兜底清理：软删本次 payload 未涉及的所有帧的旧对象行（全量保存语义）。
     *
     * @param keepAssetIds 本次 payload 中出现的资产ID（这些帧稍后按分组全量软删再插入）
     */
    int softDeleteObjectsExceptAssets(@Param("jobId") Long jobId,
                                      @Param("keepAssetIds") List<Long> keepAssetIds,
                                      @Param("userId") Long userId);

    int insertAnnotationObject(Map<String, Object> p);

    /**
     * 批量写入标注对象：一条 INSERT 带多行 VALUES，入参用强类型实体而非 Map。
     *
     * <p>保存草稿时对象数是「帧数 × 轨迹数」（关键帧插值会把每个轨迹铺满中间帧），
     * 上千个对象逐条 INSERT 就是上千次远程数据库往返。批量后同数据量只需几次往返。
     *
     * <p>注意不支持自增 id 回写（批量 INSERT 只会回填最后一行），本表调用方不需要 id，故无影响。
     */
    int insertAnnotationObjectRows(@Param("list") List<LabelAnnotationObjectRow> rows);

    /**
     * 一次性取回该作业全部 annotation 的 assetId / annotationId 两列。
     *
     * <p>批量保存时原本每帧都要 {@link #selectAnnotationIdByJobAsset} 查一次，
     * 60 帧就是 60 次往返；这里一次查完由调用方组装成映射并复用。
     *
     * <p><b>不要用 {@code @MapKey}</b>：它只决定 Map 的 key，value 是整行结果而非指定列，
     * 取出来当 Long 用会抛 ClassCastException，且泛型擦除让编译期发现不了。
     * 同理也不用 {@code Map<String, Object>} 当行类型——列名大小写取决于驱动行为。
     */
    List<LabelAnnotationIdRow> selectAnnotationIdsByJob(@Param("jobId") Long jobId);

    /**
     * 一次性软删该作业的全部标注对象。
     *
     * <p>批量保存是「全量语义」：本次 payload 已包含所有应保留的对象，其余一律视为残留。
     * 原先拆成「删不在 payload 里的帧」+「每帧各删一次」，60 帧就是 61 次 UPDATE，
     * 且每次都带 {@code JSON_EXTRACT(attr_json,'$.assetId')} 条件——JSON 函数无法走索引，
     * 只能全表扫描并逐行解析，这是保存慢的主因。既然最终结果等价于删光整作业，
     * 这里合并成一次不带 JSON 条件的 UPDATE。
     */
    int softDeleteObjectsByJob(@Param("jobId") Long jobId, @Param("userId") Long userId);

    /**
     * 软删指定 annotation 的对象行（**只碰本次 payload 覆盖的帧**）。
     * 取代 softDeleteObjectsByJob：全作业软删 + 增量保存（只发改动帧）会把
     * 未发送帧的对象清掉（数据丢失），且软删行无限堆积拖慢保存（全表 JOIN 扫描）。
     */
    int softDeleteObjectsByAnnotationIds(@Param("annotationIds") java.util.Collection<Long> annotationIds,
                                         @Param("userId") Long userId);

    int updateJobProgressAndStatus(@Param("jobId") Long jobId,
                                   @Param("status") String status,
                                   @Param("progress") int progress,
                                   @Param("userId") Long userId);

    /** 标注/提交时同步条目（label_asset）标注状态，用于任务详情页条目列表。 */
    int updateAssetLabelStatus(@Param("assetId") Long assetId,
                               @Param("labelStatus") String labelStatus,
                               @Param("userId") Long userId);

    /**
     * 提交审核时同步条目标注状态（未归入数据条目的数据资产兜底）：
     * 首次提交为「已标注(labeled)」，修订后提交为「已修订(revised)」，并把审核状态重置为「未审核(none)」。
     */
    int updateAssetSubmitStatus(@Param("assetId") Long assetId, @Param("userId") Long userId);

    /** 数据资产所属的数据条目（数据集合包）ID。 */
    Long selectEntryIdByAssetId(@Param("assetId") Long assetId);

    /** 数据条目当前的标注状态（用于区分首次提交与修订后提交）。 */
    String selectEntryLabelStatus(@Param("entryId") Long entryId);

    /**
     * 提交审核：条目（数据集合包）内的数据随条目一起流转，
     * 统一置为「已标注/已修订」，并把审核状态重置为「未审核(none)」，进入待审核。
     */
    int submitEntryAssets(@Param("entryId") Long entryId,
                          @Param("labelStatus") String labelStatus,
                          @Param("userId") Long userId);

    /** 提交审核：条目置为「已标注/已修订」，审核状态重置为「未审核(none)」。 */
    int submitEntryStatus(@Param("entryId") Long entryId,
                          @Param("labelStatus") String labelStatus,
                          @Param("userId") Long userId);

    /**
     * 作业（一批条目）的条目进度，用于按「条目」为单位聚合作业状态：
     * total=作业覆盖的条目总数，done=已进入待审核或终态的条目数。
     */
    Map<String, Object> selectJobEntryProgress(@Param("jobId") Long jobId);

    Long selectClassIdByName(@Param("jobId") Long jobId, @Param("className") String className);

    Long selectFirstClassId(@Param("jobId") Long jobId);

    // ---------------- 标注对象级批注（框级批注） ----------------

    /** 作业的框级批注（assetId 为空时返回该作业全部）。 */
    List<ObjectCommentVO> selectObjectComments(@Param("jobId") Long jobId, @Param("assetId") Long assetId);

    /** 新增框级/条目级批注（含审核节点选择的批注类型），回填自增主键到 id。 */
    int insertObjectComment(LabelObjectComment comment);

    /** 软删批注，仅创建人可删（userId 为空表示不校验创建人）。 */
    int deleteObjectComment(@Param("commentId") Long commentId, @Param("userId") Long userId);

    /** 更新批注修订状态（open / corrected），记录操作人；不校验创建人（标注员确认他人批注）。 */
    int updateObjectCommentStatus(@Param("commentId") Long commentId, @Param("status") String status, @Param("userId") Long userId);

    /**
     * 级联软删「宿主实例已不存在」的实例批注：范围限定本次保存覆盖的帧，
     * objectKey 不在提交对象集合里的批注一律软删。
     *
     * <p>系统行为，不校验 created_by —— 标注员删除实例时也要能清掉审核员写的批注，
     * 否则帧格一直标黄、批注列表却找不到对应实例。
     */
    int purgeOrphanObjectComments(Map<String, Object> p);

    /**
     * 当前资产最近一次存档里的整图批注。
     * 标注员「问题批注」随 AnnotationSaveDTO 整包落入 annotation_json，
     * 取回用于工作台回显（此前只存不读，标注员保存后看不到自己写的内容）。
     */
    String selectAnnotationComment(@Param("jobId") Long jobId, @Param("assetId") Long assetId);

    /**
     * 帧级全局属性值：取当前激活资产最近一次存档的 annotation_json $.globalAttrs（JSON 文本），
     * 由 Service 层反解析为 Map，供工作台「全局属性」弹窗回显。
     */
    String selectAnnotationGlobalAttrs(@Param("jobId") Long jobId, @Param("assetId") Long assetId);

    /**
     * 批量读取帧级批注与全局属性（导出平台扩展 sidecar 用）。
     * 返回行按 assetId 分组、annotation id 倒序，调用方每个资产取第一条即可。
     */
    List<Map<String, Object>> selectFrameAnnotationMeta(@Param("assetIds") List<Long> assetIds);

    // ---------------- 单帧无效 ----------------

    /** 标记 / 取消资产帧无效（frame_flag=invalid/normal，记录原因、操作人、时间）。 */
    int updateAssetFrameFlag(@Param("assetId") Long assetId,
                             @Param("frameFlag") String frameFlag,
                             @Param("invalidReason") String invalidReason,
                             @Param("userId") Long userId);

    /** 查询资产帧有效性标记（normal / invalid）。 */
    String selectAssetFrameFlag(@Param("assetId") Long assetId);
}
