package org.dromara.label.service;

import java.util.List;
import org.dromara.label.common.PageResult;
import org.dromara.label.dto.BatchSaveDTO;
import org.dromara.label.dto.ClassAttributeDTO;
import org.dromara.label.dto.ClassDTO;
import org.dromara.label.dto.ClassSetDTO;
import org.dromara.label.dto.EntrySplitDTO;
import org.dromara.label.dto.ProjectAssetUploadDTO;
import org.dromara.label.dto.ProjectSaveDTO;
import org.dromara.label.vo.AssetBatchVO;
import org.dromara.label.vo.AssetVO;
import org.dromara.label.vo.ClassAttributeVO;
import org.dromara.label.vo.ClassSetVO;
import org.dromara.label.vo.ClassVO;
import org.dromara.label.vo.DataEntryVO;
import org.dromara.label.vo.ProjectMemberVO;
import org.dromara.label.vo.ProjectVO;
import org.dromara.label.vo.UploadDataVO;
import org.dromara.label.vo.UploadResultVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 项目域服务：项目生命周期（创建/编辑/启动/完成/归档/恢复/复制）、项目成员、
 * 标签体系（类别集/类别）、资产数据与批次、zip/文件上传与入库。
 */
public interface ProjectService {

  // ---------------- 项目 ----------------

  /** 分页查询项目列表（支持关键字、状态、数据类型过滤；mine=true 时仅当前用户创建的项目）。 */
  PageResult<ProjectVO> projects(String keyword, String status, String dataType, boolean mine);

  /** 查询项目详情。 */
  ProjectVO project(Long id);

  /** 归档项目。 */
  void archiveProject(Long id);

  /** 恢复项目。 */
  ProjectVO restoreProject(Long id);

  /** 复制项目。 */
  ProjectVO copyProject(Long sourceId);

  /** 启动项目。 */
  ProjectVO startProject(Long id);

  /** 完成项目。 */
  ProjectVO completeProject(Long id);

  /** 上传项目 zip 数据包到指定数据批次（batchId 为空时回退默认批次）。framesPerEntry 为每批帧数上限，null 回退默认 60。 */
  UploadResultVO uploadProjectZip(Long projectId, Long batchId, MultipartFile file, Integer framesPerEntry);

  /** 导入已入库的存储对象到指定数据批次（文件导入记录回调使用）。framesPerEntry 为每批帧数上限，null 回退默认 60。 */
  UploadResultVO importStoredObject(Long projectId, Long batchId, String fileName, String objectName, Integer framesPerEntry);

  /** 创建或更新项目。 */
  ProjectVO saveProject(ProjectSaveDTO dto);

  // ---------------- 项目成员 ----------------

  /** 项目成员列表。 */
  List<ProjectMemberVO> projectMembers(Long projectId);

  // ---------------- 标签体系（类别集/类别） ----------------

  /** 类别集列表。 */
  PageResult<ClassSetVO> classSets(Long projectId);

  /** 类别集下的类别列表。 */
  List<ClassVO> classes(Long classSetId);

  /** 创建类别集。 */
  ClassSetVO createClassSet(Long projectId, ClassSetDTO dto);

  /** 更新类别集。 */
  ClassSetVO updateClassSet(Long id, ClassSetDTO dto);

  /** 删除类别集。 */
  void deleteClassSet(Long id);

  /** 创建类别。 */
  ClassVO createClass(Long classSetId, ClassDTO dto);

  /** 更新类别。 */
  ClassVO updateClass(Long id, ClassDTO dto);

  /** 删除类别。 */
  void deleteClass(Long id);

  /** 更新类别属性配置（单条 upsert，保留给兼容调用）。 */
  ClassVO updateClassAttributes(Long id, ClassAttributeDTO dto);

  /** 类别属性配置列表（含 id，供「编辑标签」原样回显）。 */
  List<ClassAttributeVO> classAttributes(Long id);

  /**
   * 整份覆盖保存类别属性配置：先清掉该标签原有属性，再按提交顺序写入。
   * 只有整份替换，前端「删除一行属性」才能真正落到库中。
   */
  ClassVO replaceClassAttributes(Long id, List<ClassAttributeDTO> list);

  // ---------------- 全局属性 ----------------

  /** 全局属性配置列表（平台级，供任务详情页「全局属性」tab 与标注工作台属性赋值表单使用）。 */
  List<ClassAttributeVO> globalAttributes(Long projectId);

  /** 整份覆盖保存全局属性配置：先清空原有配置，再按提交顺序写入。 */
  List<ClassAttributeVO> replaceGlobalAttributes(Long projectId, List<ClassAttributeDTO> list);

  // ---------------- 数据批次 ----------------

  /** 项目数据批次列表（含批次内资产/序列实时数量）。 */
  PageResult<AssetBatchVO> assetBatches(Long projectId);

  /** 数据批次详情。 */
  AssetBatchVO assetBatch(Long id);

  /** 新建数据批次。 */
  AssetBatchVO createBatch(Long projectId, BatchSaveDTO dto);

  /** 重命名数据批次 / 修改备注。 */
  AssetBatchVO updateBatch(Long id, BatchSaveDTO dto);

  /** 删除数据批次；force 为 true 时连同批次内资产与序列一起软删除。 */
  void deleteBatch(Long id, boolean force);

  // ---------------- 上传数据（批次下的独立上传单元） ----------------

  /** 批次下的上传数据列表；每次上传为一个独立单元，互不影响。 */
  PageResult<UploadDataVO> batchUploads(Long batchId);

  /** 上传数据详情。 */
  UploadDataVO uploadData(Long id);

  /** 删除上传数据；force 为 true 时连同其下数据资产一起软删除。 */
  void deleteUpload(Long id, boolean force);

  // ---------------- 数据条目（上传数据下的数据集合包） ----------------

  /** 上传数据下的数据条目列表；一个条目 = 一个数据集合包。 */
  PageResult<DataEntryVO> uploadEntries(Long uploadId);

  /**
   * 分割数据：一次上传的数据默认整包为 1 个条目（不分割），
   * 也可按设置（每份数据量 / 份数）平均分割成多个条目；已下发标注任务的上传不允许重新分割。
   */
  List<DataEntryVO> splitUploadEntries(Long uploadId, EntrySplitDTO dto);

  // ---------------- 资产 ----------------

  /** 项目资产分页查询；batchId 非空时仅返回该批次下的数据。 */
  PageResult<AssetVO> assets(
      Long projectId, Long batchId, String mediaType, String parseStatus, int pageNum, int pageSize);

  /** 项目资产分页查询；uploadId/entryId 非空时仅返回该上传数据 / 该数据条目下的数据。 */
  PageResult<AssetVO> assets(
      Long projectId,
      Long batchId,
      Long uploadId,
      Long entryId,
      String mediaType,
      String parseStatus,
      int pageNum,
      int pageSize);

  /** 资产详情。 */
  AssetVO asset(Long id);

  /** 资产预览信息。 */
  AssetVO assetPreview(Long id);

  /** 重新解析资产。 */
  AssetVO retryAsset(Long id);

  /** 创建资产上传/导入任务（JSON 清单）。 */
  AssetVO createUpload(ProjectAssetUploadDTO dto);

  /** 创建资产上传（单文件直传）。 */
  AssetVO createUpload(Long projectId, String mediaType, MultipartFile file);
}
