package org.dromara.label.service;

import org.dromara.label.vo.ProjectDocVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 项目标注文档（项目级单 PDF）服务。
 */
public interface ProjectDocService {

    /**
     * 取项目当前生效的标注文档元数据；不存在返回 null。
     */
    ProjectDocVO getProjectDoc(Long projectId);

    /**
     * 上传/替换项目标注文档（先删旧 MinIO 对象与旧 DB 记录，再上传新对象再插入新记录）。
     *
     * <p>仅项目创建者可调用。</p>
     *
     * @param file PDF 文件（仅 application/pdf）
     * @return 上传后的文档元数据
     */
    ProjectDocVO uploadProjectDoc(Long projectId, MultipartFile file);

    /**
     * 删除项目标注文档（软删 DB + 删 MinIO 对象）。
     *
     * <p>仅项目创建者可调用。</p>
     */
    void deleteProjectDoc(Long projectId);
}