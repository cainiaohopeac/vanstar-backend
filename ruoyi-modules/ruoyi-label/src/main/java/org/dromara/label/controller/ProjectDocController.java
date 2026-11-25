package org.dromara.label.controller;

import org.dromara.label.common.Result;
import jakarta.servlet.http.HttpServletResponse;
import org.dromara.label.service.MinioService;
import org.dromara.label.service.ProjectDocService;
import org.dromara.label.service.TaskService;
import org.dromara.label.vo.ProjectDocVO;
import org.dromara.common.log.annotation.Log;
import org.dromara.common.log.enums.BusinessType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * 项目标注文档接口（项目级单 PDF 参考文档）。
 *
 * <p>资源路径统一为 {@code /label/project-doc}，项目/任务标识放在查询参数
 * 或 multipart 表单字段中（不放路径变量），便于在网关/反代层做统一的文档类
 * 路由策略（例如：把文档类请求转发到文件服务集群）。</p>
 *
 * <ul>
 *   <li>项目详情页「标注文档」标签：上传/替换/删除（仅项目创建者）；</li>
 *   <li>标注页工具栏：通过任务 ID 反查项目文档（项目成员只读预览）。</li>
 * </ul>
 *
 * <p>实际 PDF 流式返回复用现有 {@link FileAccessController}（/uploads/** 网关），
 * 本控制器只返回元数据（objectName 前端自拼 /uploads/{objectName}）。</p>
 */
@RestController
public class ProjectDocController {

    @Autowired
    private ProjectDocService projectDocService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private MinioService minioService;

    /**
     * 取项目标注文档：
     * <ul>
     *   <li>{@code projectId} 非空：直接按项目查询；</li>
     *   <li>{@code taskId} 非空：反查所属项目后再查文档（标注页工具栏用，前端已有 taskId）。</li>
     *   <li>都未传或都查不到：返回 null（data=null）。</li>
     * </ul>
     */
    @GetMapping("/label/project-doc")
    public Result<ProjectDocVO> getProjectDoc(
        @RequestParam(value = "projectId", required = false) Long projectId,
        @RequestParam(value = "taskId", required = false) Long taskId) {
        Long targetProjectId = projectId;
        if (targetProjectId == null && taskId != null) {
            targetProjectId = taskService.projectIdOfTask(taskId);
        }
        if (targetProjectId == null) {
            return Result.success(null);
        }
        return Result.success(projectDocService.getProjectDoc(targetProjectId));
    }

    /**
     * 上传/替换项目标注文档（仅项目创建者；服务端校验 PDF 类型 + 50MB 上限）。
     *
     * <p>multipart 字段：
     * <ul>
     *   <li>{@code file}：PDF 文件（必填）</li>
     *   <li>{@code projectId}：所属项目 ID（必填）</li>
     * </ul>
     */
    @Log(title = "项目标注文档上传", businessType = BusinessType.IMPORT, isSaveRequestData = false)
    @PostMapping(value = "/label/project-doc", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<ProjectDocVO> uploadProjectDoc(
        @RequestParam("projectId") Long projectId,
        @RequestParam("file") MultipartFile file) {
        return Result.success("标注文档上传成功", projectDocService.uploadProjectDoc(projectId, file));
    }

    /**
     * 删除项目标注文档（仅项目创建者；软删 DB + 删 MinIO 对象）。
     */
    @Log(title = "项目标注文档删除", businessType = BusinessType.DELETE)
    @DeleteMapping("/label/project-doc")
    public Result<Void> deleteProjectDoc(@RequestParam("projectId") Long projectId) {
        projectDocService.deleteProjectDoc(projectId);
        return Result.success("标注文档已删除", null);
    }

    /**
     * PDF 文件流式预览（直接走接口流式返回，不再依赖 /uploads/** 文件网关，
     * 该网关在当前 Spring 路径匹配下未生效，避免链路耦合）。
     *
     * <p>iframe src 直接调用，依赖浏览器自动带上 sa-token cookie 完成匿名访问
     * （需在 application.yml 的 security.excludes 中加入本路径）。</p>
     *
     * <p>资源标识：{@code projectId} 优先；否则按 {@code taskId} 反查所属项目；
     * 都未传或查不到返回 404。</p>
     */
    @GetMapping("/label/project-doc/preview")
    public void preview(
        @RequestParam(value = "projectId", required = false) Long projectId,
        @RequestParam(value = "taskId", required = false) Long taskId,
        HttpServletResponse response) throws IOException {
        Long targetProjectId = projectId;
        if (targetProjectId == null && taskId != null) {
            targetProjectId = taskService.projectIdOfTask(taskId);
        }
        if (targetProjectId == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "project not found");
            return;
        }
        ProjectDocVO doc = projectDocService.getProjectDoc(targetProjectId);
        if (doc == null || doc.getObjectName() == null || doc.getObjectName().isBlank()) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "doc not found");
            return;
        }
        String objectName = doc.getObjectName();
        response.setContentType("application/pdf");
        response.setHeader("Content-Disposition", "inline;filename=\""
            + (doc.getFileName() == null ? "annotation.pdf" : doc.getFileName()) + "\"");
        try (InputStream in = minioService.openObject(objectName);
             OutputStream out = response.getOutputStream()) {
            in.transferTo(out);
        }
    }
}