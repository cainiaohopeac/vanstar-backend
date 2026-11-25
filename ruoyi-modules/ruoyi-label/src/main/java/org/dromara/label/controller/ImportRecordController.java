package org.dromara.label.controller;

import org.dromara.label.common.PageResult;
import org.dromara.label.common.Result;
import org.dromara.label.dto.ImportCreateDTO;
import org.dromara.label.service.ImportRecordService;
import org.dromara.label.vo.ImportRecordVO;
import org.dromara.label.vo.ImportResumeVO;
import org.dromara.label.vo.ImportUploadChunkVO;
import org.dromara.label.vo.UploadResultVO;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件导入接口
 * <p>通过登录态（Sa-Token）获取当前用户，仅能操作自己的导入记录</p>
 */
@RestController
@RequestMapping("/import")
public class ImportRecordController {

    @Autowired
    private ImportRecordService importRecordService;

    /**
     * 创建导入记录并初始化 MinIO 分片上传
     */
    @Log(title = "创建文件导入", businessType = BusinessType.INSERT)
    @PostMapping("/create")
    public Result<ImportRecordVO> create(@Valid @RequestBody ImportCreateDTO dto) {
        return Result.success("创建导入成功", importRecordService.createImport(dto));
    }

    /**
     * 上传分片
     */
    @PostMapping("/{id}/chunk")
    public Result<ImportUploadChunkVO> uploadChunk(@PathVariable("id") Long id,
                                                   @RequestParam("partNumber") Integer partNumber,
                                                   @RequestParam("file") MultipartFile file) {
        return Result.success("分片上传成功", importRecordService.uploadChunk(id, partNumber, file));
    }

    /**
     * 合并分片，完成导入
     */
    @Log(title = "完成文件导入", businessType = BusinessType.IMPORT)
    @PostMapping("/{id}/complete")
    public Result<ImportRecordVO> complete(@PathVariable("id") Long id,
                                           @RequestParam(value = "framesPerEntry", required = false) Integer framesPerEntry) {
        return Result.success("已提交后台处理，资产入库完成后自动完成", importRecordService.completeImport(id, framesPerEntry));
    }

    /**
     * 小文件直传：单请求同步完成上传与入库，跳过分片上传与后台处理
     * <p>fileName 支持带相对目录（文件夹上传），batchId 可选（缺省归入默认批次）</p>
     */
    @Log(title = "文件直传导入", businessType = BusinessType.IMPORT, isSaveRequestData = false)
    @PostMapping("/direct")
    public Result<UploadResultVO> directImport(@RequestParam("projectId") Long projectId,
                                               @RequestParam(value = "batchId", required = false) Long batchId,
                                               @RequestParam(value = "framesPerEntry", required = false) Integer framesPerEntry,
                                               @RequestParam("fileName") String fileName,
                                               @RequestParam("file") MultipartFile file) {
        return Result.success("上传成功", importRecordService.directImport(projectId, batchId, fileName, file, framesPerEntry));
    }

    /**
     * 暂停导入（保留已上传分片，可续传）
     */
    @Log(title = "暂停文件导入", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/stop")
    public Result<ImportRecordVO> stop(@PathVariable("id") Long id) {
        return Result.success("导入已暂停", importRecordService.stopImport(id));
    }

    /**
     * 续传导入，返回已上传分片序号
     */
    @Log(title = "恢复文件导入", businessType = BusinessType.UPDATE)
    @PostMapping("/{id}/resume")
    public Result<ImportResumeVO> resume(@PathVariable("id") Long id) {
        return Result.success("已恢复导入", importRecordService.resumeImport(id));
    }

    /**
     * 分页查询导入记录（可按项目过滤）
     */
    @GetMapping("/list")
    public Result<PageResult<ImportRecordVO>> list(@RequestParam(value = "projectId", required = false) Long projectId,
                                                   @RequestParam(value = "status", required = false) Integer status,
                                                   @RequestParam(value = "pageNum", defaultValue = "1") Integer pageNum,
                                                   @RequestParam(value = "pageSize", defaultValue = "10") Integer pageSize) {
        return Result.success(importRecordService.listImports(projectId, status, pageNum, pageSize));
    }

    /**
     * 查询导入记录详情
     */
    @GetMapping("/{id}")
    public Result<ImportRecordVO> detail(@PathVariable("id") Long id) {
        return Result.success(importRecordService.getDetail(id));
    }

    /**
     * 删除导入记录（清理 MinIO 资源）
     */
    @Log(title = "删除导入记录", businessType = BusinessType.DELETE)
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable("id") Long id) {
        importRecordService.deleteImport(id);
        return Result.success("删除成功", null);
    }
}
