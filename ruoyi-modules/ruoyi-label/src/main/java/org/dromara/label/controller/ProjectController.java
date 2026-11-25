package org.dromara.label.controller;

import java.util.List;
import org.dromara.label.common.PageResult;
import org.dromara.label.common.Result;
import org.dromara.label.dto.BatchSaveDTO;
import org.dromara.label.dto.ClassAttributeDTO;
import org.dromara.label.dto.ClassDTO;
import org.dromara.label.dto.ClassSetDTO;
import org.dromara.label.dto.EntrySplitDTO;
import org.dromara.label.dto.ProjectAssetUploadDTO;
import org.dromara.label.dto.ProjectSaveDTO;
import org.dromara.label.service.ProjectService;
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
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 项目域（含项目生命周期、成员、标签体系、资产与批次、上传入库）。 */
@RestController
public class ProjectController {

  @Autowired
  private ProjectService projectService;

  // ---------------- 项目 ----------------

  @GetMapping("/projects")
  public Result<PageResult<ProjectVO>> projects(
      @RequestParam(value = "keyword", required = false) String keyword,
      @RequestParam(value = "status", required = false) String status,
      @RequestParam(value = "dataType", required = false) String dataType,
      @RequestParam(value = "mine", required = false, defaultValue = "false") boolean mine) {
    return Result.success(projectService.projects(keyword, status, dataType, mine));
  }

  @Log(title = "项目管理", businessType = BusinessType.INSERT)
  @PostMapping("/projects")
  public Result<ProjectVO> createProject(@Valid @RequestBody ProjectSaveDTO dto) {
    return Result.success("创建成功", projectService.saveProject(dto));
  }

  @GetMapping("/projects/{id}")
  public Result<ProjectVO> project(@PathVariable Long id) {
    return Result.success(projectService.project(id));
  }

  @Log(title = "项目管理", businessType = BusinessType.UPDATE)
  @PutMapping("/projects/{id}")
  public Result<ProjectVO> updateProject(
      @PathVariable Long id, @Valid @RequestBody ProjectSaveDTO dto) {
    dto.setId(id);
    return Result.success("保存成功", projectService.saveProject(dto));
  }

  @Log(title = "项目管理", businessType = BusinessType.DELETE)
  @DeleteMapping("/projects/{id}")
  public Result<Void> deleteProject(@PathVariable Long id) {
    projectService.archiveProject(id);
    return Result.success("已归档", null);
  }

  @GetMapping("/projects/{id}/overview")
  public Result<ProjectVO> projectOverview(@PathVariable Long id) {
    return Result.success(projectService.project(id));
  }

  @Log(title = "项目启动", businessType = BusinessType.UPDATE)
  @PostMapping("/projects/{id}/start")
  public Result<ProjectVO> startProject(@PathVariable Long id) {
    return Result.success("项目已启动", projectService.startProject(id));
  }

  @Log(title = "项目完成", businessType = BusinessType.UPDATE)
  @PostMapping("/projects/{id}/complete")
  public Result<ProjectVO> completeProject(@PathVariable Long id) {
    return Result.success("项目已完成", projectService.completeProject(id));
  }

  @Log(title = "项目归档", businessType = BusinessType.DELETE)
  @PostMapping("/projects/{id}/archive")
  public Result<Void> archiveProject(@PathVariable Long id) {
    projectService.archiveProject(id);
    return Result.success("项目已归档", null);
  }

  @Log(title = "项目恢复", businessType = BusinessType.UPDATE)
  @PostMapping("/projects/{id}/restore")
  public Result<ProjectVO> restoreProject(@PathVariable Long id) {
    return Result.success("项目已恢复", projectService.restoreProject(id));
  }

  @Log(title = "项目复制", businessType = BusinessType.INSERT)
  @PostMapping("/projects/{id}/copy")
  public Result<ProjectVO> copyProject(@PathVariable Long id) {
    return Result.success("已复制为副本项目", projectService.copyProject(id));
  }

  @GetMapping("/projects/{id}/members")
  public Result<List<ProjectMemberVO>> projectMembers(@PathVariable Long id) {
    return Result.success(projectService.projectMembers(id));
  }

  @Log(title = "项目数据上传", businessType = BusinessType.IMPORT, isSaveRequestData = false)
  @PostMapping(value = "/projects/{id}/assets/zip", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public Result<UploadResultVO> uploadProjectZip(
      @PathVariable Long id,
      @RequestParam(value = "batchId", required = false) Long batchId,
      @RequestParam(value = "framesPerEntry", required = false) Integer framesPerEntry,
      @RequestParam("file") MultipartFile file) {
    return Result.success("数据上传完成", projectService.uploadProjectZip(id, batchId, file, framesPerEntry));
  }

  // ---------------- 标签体系（类别集/类别） ----------------

  @GetMapping("/projects/{id}/class-sets")
  public Result<PageResult<ClassSetVO>> classSets(@PathVariable Long id) {
    return Result.success(projectService.classSets(id));
  }

  @Log(title = "类别集管理", businessType = BusinessType.INSERT)
  @PostMapping("/projects/{id}/class-sets")
  public Result<ClassSetVO> createClassSet(
      @PathVariable Long id, @Valid @RequestBody ClassSetDTO dto) {
    return Result.success("创建成功", projectService.createClassSet(id, dto));
  }

  @Log(title = "类别集管理", businessType = BusinessType.UPDATE)
  @PutMapping("/class-sets/{id}")
  public Result<ClassSetVO> updateClassSet(
      @PathVariable Long id, @Valid @RequestBody ClassSetDTO dto) {
    return Result.success("保存成功", projectService.updateClassSet(id, dto));
  }

  @Log(title = "类别集管理", businessType = BusinessType.DELETE)
  @DeleteMapping("/class-sets/{id}")
  public Result<Void> deleteClassSet(@PathVariable Long id) {
    projectService.deleteClassSet(id);
    return Result.success("删除成功", null);
  }

  @GetMapping("/class-sets/{id}/classes")
  public Result<List<ClassVO>> classes(@PathVariable Long id) {
    return Result.success(projectService.classes(id));
  }

  @Log(title = "类别管理", businessType = BusinessType.INSERT)
  @PostMapping("/class-sets/{id}/classes")
  public Result<ClassVO> createClass(
      @PathVariable Long id, @Valid @RequestBody ClassDTO dto) {
    return Result.success("创建成功", projectService.createClass(id, dto));
  }

  @Log(title = "类别管理", businessType = BusinessType.UPDATE)
  @PutMapping("/classes/{id}")
  public Result<ClassVO> updateClass(
      @PathVariable Long id, @Valid @RequestBody ClassDTO dto) {
    return Result.success("保存成功", projectService.updateClass(id, dto));
  }

  @Log(title = "类别管理", businessType = BusinessType.DELETE)
  @DeleteMapping("/classes/{id}")
  public Result<Void> deleteClass(@PathVariable Long id) {
    projectService.deleteClass(id);
    return Result.success("删除成功", null);
  }

  @Log(title = "类别属性配置", businessType = BusinessType.UPDATE)
  @PutMapping("/classes/{id}/attributes")
  public Result<ClassVO> updateClassAttributes(
      @PathVariable Long id, @Valid @RequestBody ClassAttributeDTO dto) {
    return Result.success("保存成功", projectService.updateClassAttributes(id, dto));
  }

  /** 标签属性列表：返回带 id 的属性配置，供「编辑标签」原样回显 */
  @GetMapping("/classes/{id}/attributes")
  public Result<List<ClassAttributeVO>> classAttributes(@PathVariable Long id) {
    return Result.success(projectService.classAttributes(id));
  }

  /**
   * 整份覆盖保存标签属性：先清掉原有属性再按提交顺序写入。
   * 前端「编辑标签」弹窗的属性配置一次性提交到这里，删除的行才会真正生效。
   */
  @Log(title = "类别属性配置", businessType = BusinessType.UPDATE)
  @PutMapping("/classes/{id}/attributes/batch")
  public Result<ClassVO> replaceClassAttributes(
      @PathVariable Long id, @RequestBody List<ClassAttributeDTO> list) {
    return Result.success("保存成功", projectService.replaceClassAttributes(id, list));
  }

  // ---------------- 全局属性 ----------------

  /** 全局属性配置列表（平台级；任务详情页「全局属性」tab 的数据源）。 */
  @GetMapping("/global-attributes")
  public Result<List<ClassAttributeVO>> globalAttributes(@RequestParam Long projectId) {
    return Result.success(projectService.globalAttributes(projectId));
  }

  /** 整份覆盖保存全局属性配置（项目维度）：先清空该项目配置再按提交顺序写入，「删除一行」才能真正生效。 */
  @Log(title = "全局属性配置", businessType = BusinessType.UPDATE)
  @PutMapping("/global-attributes/batch")
  public Result<List<ClassAttributeVO>> replaceGlobalAttributes(
      @RequestParam Long projectId,
      @RequestBody List<ClassAttributeDTO> list) {
    return Result.success("保存成功", projectService.replaceGlobalAttributes(projectId, list));
  }

  // ---------------- 资产与批次 ----------------

  @Log(title = "资产上传", businessType = BusinessType.IMPORT, isSaveRequestData = false)
  @PostMapping(value = "/assets/upload", consumes = MediaType.APPLICATION_JSON_VALUE)
  public Result<AssetVO> uploadAsset(@Valid @RequestBody ProjectAssetUploadDTO dto) {
    return Result.success("上传完成", projectService.createUpload(dto));
  }

  @Log(title = "资产上传", businessType = BusinessType.IMPORT, isSaveRequestData = false)
  @PostMapping(value = "/assets/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public Result<AssetVO> uploadAssetFile(
      @RequestParam("projectId") Long projectId,
      @RequestParam("mediaType") String mediaType,
      @RequestParam("file") MultipartFile file) {
    return Result.success("上传完成", projectService.createUpload(projectId, mediaType, file));
  }

  @Log(title = "资产导入", businessType = BusinessType.IMPORT, isSaveRequestData = false)
  @PostMapping("/assets/import")
  public Result<AssetVO> importAssets(@Valid @RequestBody ProjectAssetUploadDTO dto) {
    return Result.success("导入任务已创建", projectService.createUpload(dto));
  }

  @GetMapping("/projects/{id}/assets")
  public Result<PageResult<AssetVO>> projectAssets(
      @PathVariable Long id,
      @RequestParam(value = "batchId", required = false) Long batchId,
      @RequestParam(value = "uploadId", required = false) Long uploadId,
      @RequestParam(value = "entryId", required = false) Long entryId,
      @RequestParam(value = "mediaType", required = false) String mediaType,
      @RequestParam(value = "parseStatus", required = false) String parseStatus,
      @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
      @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
    return Result.success(
        projectService.assets(id, batchId, uploadId, entryId, mediaType, parseStatus, pageNum, pageSize));
  }

  @GetMapping("/assets/{id}")
  public Result<AssetVO> asset(@PathVariable Long id) {
    return Result.success(projectService.asset(id));
  }

  @GetMapping("/assets/{id}/preview")
  public Result<AssetVO> assetPreview(@PathVariable Long id) {
    return Result.success(projectService.assetPreview(id));
  }

  @Log(title = "资产重新解析", businessType = BusinessType.UPDATE)
  @PostMapping("/assets/{id}/retry")
  public Result<AssetVO> retryAsset(@PathVariable Long id) {
    return Result.success("已重新解析", projectService.retryAsset(id));
  }

  @GetMapping("/asset-batches")
  public Result<PageResult<AssetBatchVO>> assetBatches(
      @RequestParam(value = "projectId", required = false) Long projectId) {
    return Result.success(projectService.assetBatches(projectId));
  }

  @GetMapping("/asset-batches/{id}")
  public Result<AssetBatchVO> assetBatch(@PathVariable Long id) {
    return Result.success(projectService.assetBatch(id));
  }

  // ---------------- 数据批次管理 ----------------

  @GetMapping("/projects/{id}/batches")
  public Result<PageResult<AssetBatchVO>> projectBatches(@PathVariable Long id) {
    return Result.success(projectService.assetBatches(id));
  }

  @Log(title = "批次管理", businessType = BusinessType.INSERT)
  @PostMapping("/projects/{id}/batches")
  public Result<AssetBatchVO> createBatch(
      @PathVariable Long id, @Valid @RequestBody BatchSaveDTO dto) {
    return Result.success("批次创建成功", projectService.createBatch(id, dto));
  }

  @Log(title = "批次管理", businessType = BusinessType.UPDATE)
  @PutMapping("/asset-batches/{id}")
  public Result<AssetBatchVO> updateBatch(
      @PathVariable Long id, @Valid @RequestBody BatchSaveDTO dto) {
    return Result.success("保存成功", projectService.updateBatch(id, dto));
  }

  @Log(title = "批次管理", businessType = BusinessType.DELETE)
  @DeleteMapping("/asset-batches/{id}")
  public Result<Void> deleteBatch(
      @PathVariable Long id,
      @RequestParam(value = "force", defaultValue = "false") boolean force) {
    projectService.deleteBatch(id, force);
    return Result.success("批次已删除", null);
  }

  // ---------------- 上传数据管理 ----------------
  // 注意：本组接口的 {id} 必须限定为纯数字（{id:\d+}）。
  // 否则单段路径 /uploads/xxx.png 会被这里的 {id} 映射抢占
  // （比 FileAccessController 的 /uploads/** 更具体），导致文件网关 404/参数类型错误。

  @GetMapping("/asset-batches/{id}/uploads")
  public Result<PageResult<UploadDataVO>> batchUploads(@PathVariable Long id) {
    return Result.success(projectService.batchUploads(id));
  }

  @GetMapping("/uploads/{id:\\d+}")
  public Result<UploadDataVO> uploadData(@PathVariable Long id) {
    return Result.success(projectService.uploadData(id));
  }

  @Log(title = "上传数据删除", businessType = BusinessType.DELETE)
  @DeleteMapping("/uploads/{id:\\d+}")
  public Result<Void> deleteUpload(
      @PathVariable Long id,
      @RequestParam(value = "force", defaultValue = "false") boolean force) {
    projectService.deleteUpload(id, force);
    return Result.success("上传数据已删除", null);
  }

  // ---------------- 数据条目（上传数据下的数据集合包） ----------------

  @GetMapping("/uploads/{id:\\d+}/entries")
  public Result<PageResult<DataEntryVO>> uploadEntries(@PathVariable Long id) {
    return Result.success(projectService.uploadEntries(id));
  }

  @Log(title = "数据分割", businessType = BusinessType.OTHER)
  @PostMapping("/uploads/{id:\\d+}/split")
  public Result<List<DataEntryVO>> splitUpload(
      @PathVariable Long id, @Valid @RequestBody EntrySplitDTO dto) {
    return Result.success("数据分割完成", projectService.splitUploadEntries(id, dto));
  }
}
