package org.dromara.label.service.impl;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.label.config.MinioProperties;
import org.dromara.label.mapper.ProjectDocMapper;
import org.dromara.label.mapper.ProjectMapper;
import org.dromara.label.service.BaseService;
import org.dromara.label.service.MinioService;
import org.dromara.label.service.ProjectDocService;
import org.dromara.label.vo.ProjectDocVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * 项目标注文档服务实现：
 * <ul>
 *   <li>项目级单文档约束：上传/替换走「删旧 MinIO + 软删旧 DB + 写新对象 + 插新 DB」；
 *       单文档语义由 label_project_doc.uk_project_id 唯一键兜底，并发上传也不会出现多份文档。</li>
 *   <li>权限：上传/替换/删除仅项目创建者（label_project.created_by），
 *       其他项目成员可通过 GET 接口预览但不能改。</li>
 *   <li>PDF 类型校验：客户端 accept + 服务端 contentType + 文件名后缀 三重兜底，
 *       避免用户绕开前端校验上传非 PDF。</li>
 *   <li>MinIO key 命名：projects/{projectId}/doc/{uuid}.pdf，与项目数据资产同前缀、按用途分目录。</li>
 * </ul>
 */
@Service
public class ProjectDocServiceImpl extends BaseService implements ProjectDocService {

    private static final String DOC_PREFIX = "projects/%d/doc/";
    private static final String PDF_CONTENT_TYPE = "application/pdf";
    private static final long MAX_FILE_SIZE = 50L * 1024 * 1024; // 50MB 上限，防止异常大文件撑爆 MinIO

    @Autowired
    private ProjectDocMapper projectDocMapper;

    @Autowired
    private ProjectMapper projectMapper;

    @Autowired
    private MinioService minioService;

    @Autowired
    private MinioProperties minioProperties;

    @Override
    public ProjectDocVO getProjectDoc(Long projectId) {
        return projectDocMapper.selectDocByProjectId(projectId);
    }

    @Override
    @Transactional
    public ProjectDocVO uploadProjectDoc(Long projectId, MultipartFile file) {
        assertProjectCreator(projectId);
        assertPdf(file);

        // 1) 取旧记录：先物理删旧 DB 行（释放 uk_project_id 唯一键，避免覆盖时冲突），
        //    等新行成功插入后再统一删除旧 MinIO 对象，避免极端时序下两个 MinIO 操作都失败的窗口
        Map<String, Object> oldRow = projectDocMapper.selectDocRow(projectId);
        String oldObjectName = oldRow == null ? null : (String) oldRow.get("object_name");
        if (oldRow != null) {
            // 物理删除（含已软删历史行），否则唯一键未释放、插新行会触发冲突；
            // 审计由 Service 层 writeOperationLog 统一承担，不依赖软删行。
            projectDocMapper.deleteByProjectId(projectId);
        }

        // 2) 上传新对象到 MinIO
        String objectName = String.format(Locale.ROOT, DOC_PREFIX, projectId)
                + UUID.randomUUID().toString().replace("-", "") + ".pdf";
        try {
            minioService.putObject(objectName, file.getInputStream(), file.getSize(), PDF_CONTENT_TYPE);
        } catch (IOException e) {
            throw new ServiceException("标注文档上传失败：" + e.getMessage());
        }

        // 3) 插新 DB 行（uk_project_id 唯一键保证项目维度单文档）
        Map<String, Object> p = new HashMap<>();
        p.put("projectId", projectId);
        p.put("fileName", file.getOriginalFilename());
        p.put("objectName", objectName);
        p.put("bucketName", minioProperties.getBucket());
        p.put("fileSize", file.getSize());
        p.put("contentType", PDF_CONTENT_TYPE);
        p.put("userId", currentUserId());
        try {
            projectDocMapper.insertDoc(p);
        } catch (Exception dbErr) {
            // 补偿：DB 写入失败时把刚上传到 MinIO 的新对象删掉，避免 MinIO 残留孤儿对象
            safeRemoveObject(objectName);
            throw new ServiceException("标注文档入库失败：" + dbErr.getMessage());
        }

        // 4) 新 DB 行已落地，再删旧 MinIO 对象（即便失败也不影响新文档主业务）
        if (oldObjectName != null) {
            safeRemoveObject(oldObjectName);
        }

        // 5) 回传元数据 + 写操作日志
        ProjectDocVO vo = projectDocMapper.selectDocByProjectId(projectId);
        if (vo != null) {
            writeOperationLog("UPLOAD_PROJECT_DOC", "project", projectId,
                Map.of("docId", vo.getId(), "fileName", vo.getFileName()));
        }
        return vo;
    }

    @Override
    @Transactional
    public void deleteProjectDoc(Long projectId) {
        assertProjectCreator(projectId);
        Map<String, Object> row = projectDocMapper.selectDocRow(projectId);
        if (row == null) {
            return;
        }
        String oldObjectName = (String) row.get("object_name");
        // 先物理删 DB 行（DB 是真理之源，落定后再处理 MinIO）
        projectDocMapper.deleteByProjectId(projectId);
        writeOperationLog("DELETE_PROJECT_DOC", "project", projectId, Map.of("projectId", projectId));
        // 再删 MinIO 对象，失败仅记日志；后台清理脚本兜底处理残留孤儿对象
        if (oldObjectName != null) {
            safeRemoveObject(oldObjectName);
        }
    }

    /**
     * 权限校验：仅项目创建者可上传/替换/删除标注文档（其他项目成员只能预览）。
     */
    private void assertProjectCreator(Long projectId) {
        Map<String, Object> row = projectMapper.selectProjectRow(projectId);
        if (row == null) {
            throw new ServiceException("项目不存在或已归档");
        }
        Object createdBy = row.get("created_by");
        if (createdBy == null || !String.valueOf(createdBy).equals(String.valueOf(currentUserId()))) {
            throw new ServiceException("仅项目创建者可上传/删除标注文档");
        }
    }

    /**
     * PDF 校验：contentType 与扩展名兜底（部分浏览器/网关会丢 contentType）。
     */
    private void assertPdf(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ServiceException("请选择 PDF 文件");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new ServiceException("标注文档大小不能超过 50MB");
        }
        String name = file.getOriginalFilename();
        boolean extOk = name != null && name.toLowerCase(Locale.ROOT).endsWith(".pdf");
        String ct = file.getContentType();
        boolean ctOk = ct != null && ct.toLowerCase(Locale.ROOT).contains("pdf");
        if (!extOk && !ctOk) {
            throw new ServiceException("仅支持上传 PDF 格式的标注文档");
        }
    }

    /**
     * 安全删除 MinIO 对象：删除失败不抛错（避免影响上传/删除主业务），
     * 残留孤儿对象由后台独立清理任务兜底。
     */
    private void safeRemoveObject(String objectName) {
        if (objectName == null || objectName.isBlank()) {
            return;
        }
        try {
            minioService.removeObject(objectName);
        } catch (Exception ignored) {
            // 孤儿对象可由独立清理任务处理；不抛错以免影响正常上传/删除主业务
        }
    }
}