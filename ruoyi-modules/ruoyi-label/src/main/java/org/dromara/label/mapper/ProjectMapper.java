package org.dromara.label.mapper;

import org.apache.ibatis.annotations.Param;
import org.dromara.label.vo.AssetBatchVO;
import org.dromara.label.vo.AssetVO;
import org.dromara.label.vo.ClassAttributeVO;
import org.dromara.label.vo.ClassSetVO;
import org.dromara.label.vo.DataEntryVO;
import org.dromara.label.vo.ClassVO;
import org.dromara.label.vo.ProjectMemberVO;
import org.dromara.label.vo.ProjectVO;
import org.dromara.label.vo.UploadDataVO;

import java.util.List;
import java.util.Map;

/**
 * 项目域数据访问（label_project / label_class_set / label_class / label_asset /
 * label_asset_batch 等，SQL 见 resources/mapper/label/ProjectMapper.xml）
 */
public interface ProjectMapper {

    // ---------------- 项目 ----------------

    List<ProjectVO> selectProjects(@Param("keyword") String keyword,
                                   @Param("status") String status,
                                   @Param("dataType") String dataType,
                                   @Param("createdBy") Long createdBy);

    ProjectVO selectProjectById(@Param("id") Long id);

    /** 读取原始行（SELECT *），供复制项目使用 */
    Map<String, Object> selectProjectRow(@Param("id") Long id);

    String selectProjectStatus(@Param("id") Long id);

    /** 仅未删除项目的状态（编辑校验用），不存在返回 null */
    String selectActiveProjectStatus(@Param("id") Long id);

    /** 项目关联的全部队伍ID（去重非空） */
    List<Long> selectProjectTeamIds(@Param("projectId") Long projectId);

    /** 有效用户检查（状态正常），返回用户ID或 null */
    Long selectActiveUserId(@Param("userId") Long userId);

    int archiveProject(@Param("id") Long id, @Param("userId") Long userId);

    int restoreProject(@Param("id") Long id, @Param("userId") Long userId);

    int startProject(@Param("id") Long id, @Param("userId") Long userId);

    int completeProject(@Param("id") Long id, @Param("userId") Long userId);

    int insertProject(Map<String, Object> p);

    int updateProject(Map<String, Object> p);

    long countClassSetByProject(@Param("projectId") Long projectId);

    long countTextClassByProject(@Param("projectId") Long projectId);

    String selectProjectDataType(@Param("projectId") Long projectId);

    long countAssetByProject(@Param("projectId") Long projectId);

    // ---------------- 类别集 / 类别 ----------------

    List<ClassSetVO> selectClassSets(@Param("projectId") Long projectId);

    ClassSetVO selectClassSetById(@Param("id") Long id);

    int insertClassSet(Map<String, Object> p);

    int updateClassSet(@Param("id") Long id,
                       @Param("name") String name,
                       @Param("version") String version,
                       @Param("userId") Long userId);

    int deleteClassSet(@Param("id") Long id, @Param("userId") Long userId);

    List<ClassVO> selectClasses(@Param("classSetId") Long classSetId);

    ClassVO selectClassById(@Param("id") Long id);

    int insertClass(Map<String, Object> p);

    int updateClass(@Param("id") Long id,
                    @Param("labelName") String labelName,
                    @Param("color") String color,
                    @Param("toolType") String toolType,
                    @Param("sortOrder") Integer sortOrder,
                    @Param("defaultSizeL") Double defaultSizeL,
                    @Param("defaultSizeW") Double defaultSizeW,
                    @Param("defaultSizeH") Double defaultSizeH,
                    @Param("isRigid") Integer isRigid,
                    @Param("minInsidePoints") Integer minInsidePoints,
                    @Param("keypointTemplateJson") String keypointTemplateJson,
                    @Param("userId") Long userId);

    int deleteClass(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * 标签属性 upsert：基于 uk_class_attr_name(class_id, attr_name) 唯一键做幂等写入。
     * 重复保存同名属性不会插出多条，仅 update attr_type/attr_options/required/max_length。
     *
     * @param maxLength 文本最大字符数（仅 attrType='text' 时使用；null 或 0 表示不限制）
     */
    int insertClassAttribute(@Param("classId") Long classId,
                             @Param("attrName") String attrName,
                             @Param("attrType") String attrType,
                             @Param("attrOptionsJson") String attrOptionsJson,
                             @Param("required") Integer required,
                             @Param("maxLength") Integer maxLength,
                             @Param("userId") Long userId);

    /**
     * 标签下的未删除属性列表（按 id 升序，即配置顺序）。
     * attr_options 以 JSON_UNQUOTE 文本返回，由 Service 层反解析为原始值列表。
     */
    List<ClassAttributeVO> selectClassAttributes(@Param("classId") Long classId);

    /**
     * 软删除标签下的全部属性。
     * 整份覆盖保存（{@code PUT /classes/{id}/attributes}）时先清掉旧属性，
     * 再按前端提交的顺序重新写入，这样「删除属性行」才能真正生效。
     */
    int deleteClassAttributesByClass(@Param("classId") Long classId, @Param("userId") Long userId);

    // ---------------- 全局属性 ----------------

    /**
     * 全局属性 upsert（项目维度）：基于 uk_global_attr_name(project_id, attr_name) 唯一键做幂等写入。
     * 结构与标签属性一致（attrType：select=下拉 / text=文本）。
     */
    int insertGlobalAttribute(@Param("projectId") Long projectId,
                              @Param("attrName") String attrName,
                              @Param("attrType") String attrType,
                              @Param("attrOptionsJson") String attrOptionsJson,
                              @Param("required") Integer required,
                              @Param("maxLength") Integer maxLength,
                              @Param("userId") Long userId);

    /** 指定项目未删除的全局属性（按 id 升序，即配置顺序）。attr_options 以 JSON 文本返回。 */
    List<ClassAttributeVO> selectGlobalAttributes(@Param("projectId") Long projectId);

    /** 软删除指定项目的全部全局属性（整份覆盖保存前清空，删除的行才能真正生效）。 */
    int deleteAllGlobalAttributes(@Param("projectId") Long projectId, @Param("userId") Long userId);

    // ---------------- 项目成员 ----------------

    List<ProjectMemberVO> selectProjectMembers(@Param("projectId") Long projectId);

    // ---------------- 资产与批次 ----------------

    List<AssetBatchVO> selectAssetBatches(@Param("projectId") Long projectId);

    AssetBatchVO selectAssetBatchById(@Param("id") Long id);

    /** 新建批次（p: projectId/batchNo/batchName/remark/sourceType/userId） */
    int insertAssetBatch(Map<String, Object> p);

    /** 重命名批次 / 修改备注 */
    int updateAssetBatch(@Param("id") Long id,
                         @Param("batchName") String batchName,
                         @Param("remark") String remark,
                         @Param("userId") Long userId);

    /** 软删除批次 */
    int deleteAssetBatch(@Param("id") Long id, @Param("userId") Long userId);

    /** 批次内未删除资产数 */
    long countAssetByBatch(@Param("batchId") Long batchId);

    /** 批次内未删除序列数 */
    long countSequenceByBatch(@Param("batchId") Long batchId);

    /** 软删除批次内全部资产 */
    int deleteAssetsByBatch(@Param("batchId") Long batchId, @Param("userId") Long userId);

    /** 软删除批次内全部序列 */
    int deleteSequencesByBatch(@Param("batchId") Long batchId, @Param("userId") Long userId);

    long countAssets(@Param("projectId") Long projectId,
                     @Param("batchId") Long batchId,
                     @Param("uploadId") Long uploadId,
                     @Param("entryId") Long entryId,
                     @Param("mediaType") String mediaType,
                     @Param("parseStatus") String parseStatus);

    List<AssetVO> selectAssets(@Param("projectId") Long projectId,
                               @Param("batchId") Long batchId,
                               @Param("uploadId") Long uploadId,
                               @Param("entryId") Long entryId,
                               @Param("mediaType") String mediaType,
                               @Param("parseStatus") String parseStatus,
                               @Param("limit") int limit,
                               @Param("offset") int offset);

    AssetVO selectAssetById(@Param("id") Long id);

    int retryAsset(@Param("id") Long id, @Param("userId") Long userId);

    int insertAsset(Map<String, Object> p);

    /** 批次计数累加（total/success 同步 +1，状态转为 parsed） */
    int bumpAssetBatch(@Param("batchId") Long batchId);

    // ---------------- 上传数据（批次下的独立上传单元） ----------------

    /** 批次下的上传数据列表（含其下数据条目实时数量） */
    List<UploadDataVO> selectUploadsByBatch(@Param("batchId") Long batchId);

    UploadDataVO selectUploadById(@Param("id") Long id);

    /** 查询同批次下同名称、同来源类型的最近一条上传数据ID（文件夹上传归组复用） */
    Long selectLatestUploadId(@Param("projectId") Long projectId, @Param("batchId") Long batchId,
                              @Param("fileName") String fileName, @Param("sourceType") String sourceType);

    /** 新建上传数据（p: projectId/batchId/uploadNo/fileName/fileSize/objectName/sourceType/status/userId） */
    int insertUpload(Map<String, Object> p);

    /** 上传编号回填为「UPLOAD-自增id」（并发安全：唯一性由主键保证，见 createUploadData 注释） */
    int updateUploadNo(@Param("id") Long id);

    /** 上传数据下未删除资产数 */
    long countAssetByUpload(@Param("uploadId") Long uploadId);

    /** 重算上传数据条目数 */
    int refreshUploadAssetCount(@Param("uploadId") Long uploadId);

    /** 软删除上传数据 */
    int deleteUpload(@Param("id") Long id, @Param("userId") Long userId);

    /** 软删除批次下全部上传数据 */
    int deleteUploadsByBatch(@Param("batchId") Long batchId, @Param("userId") Long userId);

    /** 软删除上传数据下全部资产 */
    int deleteAssetsByUpload(@Param("uploadId") Long uploadId, @Param("userId") Long userId);

    // ---------------- 数据条目（上传数据下的数据集合包） ----------------

    /** 上传数据下的条目列表（按序号升序） */
    List<DataEntryVO> selectEntriesByUpload(@Param("uploadId") Long uploadId);

    /** 条目详情 */
    DataEntryVO selectDataEntryById(@Param("id") Long id);

    /** 上传数据下「不分割」的默认条目ID（不存在返回 null） */
    Long selectDefaultEntryIdByUpload(@Param("uploadId") Long uploadId);

    /** 上传数据下序号最大的条目ID（已分割后新入库数据追加用，不存在返回 null） */
    Long selectLastEntryIdByUpload(@Param("uploadId") Long uploadId);

    /** 上传数据下最近一个未满（点云资产数 < maxFrames）的点云标注条目ID；不存在返回 null */
    Long selectLastOpenPointCloudEntry(@Param("uploadId") Long uploadId, @Param("maxFrames") int maxFrames);

    /** 上传数据下最大条目序号（不存在返回 0） */
    int selectMaxEntrySeqNo(@Param("uploadId") Long uploadId);

    /**
     * 项目内已用的最大条目号序号（取 entry_no 中 ENTRY- 之后的数字，不存在返回 0）。
     * 编号按项目独立递增，不同项目互不影响；已软删除的条目也计入，避免复用号码撞唯一索引。
     */
    Long selectMaxEntryNoSeq(@Param("projectId") Long projectId);

    /** 批次下最近一个未满（点云资产数 < maxFrames）的点云标注条目ID；不存在返回 null（跨 upload 聚合用） */
    Long selectLastOpenPointCloudEntryByBatch(@Param("batchId") Long batchId, @Param("maxFrames") int maxFrames);

    /** 批次下最大条目序号（不存在返回 0） */
    int selectMaxEntrySeqNoByBatch(@Param("batchId") Long batchId);

    /** 上传数据下的数据ID（按入库顺序） */
    List<Long> selectAssetIdsByUpload(@Param("uploadId") Long uploadId);

    /** 上传数据下点云资产ID（按点云帧切分标注条目用；无点云时退化为全部资产） */
    List<Long> selectPointCloudAssetIdsByUpload(@Param("uploadId") Long uploadId);

    /** nuScenes：把相机图按 label_nuscenes_asset_map 归并到其点云所在条目（参考资产，不进入标注作业） */
    int bindCameraImagesToPointCloudEntry(@Param("uploadId") Long uploadId, @Param("userId") Long userId);

    /** nuScenes 兜底：上传下未归属条目的相机图归入首个点云条目，避免孤儿资产 */
    int bindOrphanImagesToFirstPointCloudEntry(@Param("uploadId") Long uploadId, @Param("userId") Long userId);

    /** 批次所属任务ID（批次已下发任务时为新入库条目回填任务归属，未下发返回 null） */
    Long selectTaskIdByBatch(@Param("batchId") Long batchId);

    /** 新建数据条目（p: projectId/batchId/uploadId/entryNo/entryName/seqNo/splitMode/userId） */
    int insertDataEntry(Map<String, Object> p);

    /** 把单条数据挂到条目上 */
    int assignAssetToEntry(@Param("entryId") Long entryId,
                           @Param("assetId") Long assetId,
                           @Param("userId") Long userId);

    /** 把一批数据挂到条目上（分割数据用） */
    int bindAssetsToEntry(@Param("entryId") Long entryId,
                          @Param("assetIds") List<Long> assetIds,
                          @Param("userId") Long userId);

    /** 清空上传数据下全部数据的条目归属（重新分割用） */
    int clearAssetEntryByUpload(@Param("uploadId") Long uploadId, @Param("userId") Long userId);

    /**
     * 按上传数据维度刷新条目冗余的数据资产信息列（media_type / parse_status / first_asset_id）。
     * splitUploadEntries / attachAssetsToDefaultEntry 末尾调用；聚合值由常量传入，
     * 对应 {@link org.dromara.label.common.MediaType#MIXED} 与
     * {@link org.dromara.label.common.AssetParseStatus} 的 FAILED / SUCCESS。
     */
    int refreshEntryAssetInfoByUpload(@Param("uploadId") Long uploadId,
                                      @Param("mixedMediaType") String mixedMediaType,
                                      @Param("failedStatus") String failedStatus,
                                      @Param("successStatus") String successStatus);

    /**
     * 按资产维度刷新其所属条目的冗余数据资产信息列。retryAsset 等单资产状态变更后调用；
     * 资产未归属条目时不命中任何行，幂等。
     */
    int refreshEntryAssetInfoByAssetId(@Param("assetId") Long assetId,
                                       @Param("mixedMediaType") String mixedMediaType,
                                       @Param("failedStatus") String failedStatus,
                                       @Param("successStatus") String successStatus);

    /** 重算上传数据下全部条目的数据条数 */
    int refreshDataEntryAssetCountByUpload(@Param("uploadId") Long uploadId);

    /** 软删除上传数据下全部条目（重新分割/删除上传用） */
    int deleteDataEntriesByUpload(@Param("uploadId") Long uploadId, @Param("userId") Long userId);

    /** 软删除批次下全部条目 */
    int deleteDataEntriesByBatch(@Param("batchId") Long batchId, @Param("userId") Long userId);

    /** 上传数据下已下发任务的数据条数（>0 表示不允许重新分割） */
    long countAssignedAssetByUpload(@Param("uploadId") Long uploadId);
}
