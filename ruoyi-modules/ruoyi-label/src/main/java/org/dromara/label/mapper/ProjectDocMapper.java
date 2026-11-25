package org.dromara.label.mapper;

import org.apache.ibatis.annotations.Param;
import org.dromara.label.vo.ProjectDocVO;

import java.util.Map;

/**
 * 项目标注文档数据访问（SQL 见 resources/mapper/label/ProjectDocMapper.xml）。
 */
public interface ProjectDocMapper {

    /**
     * 按项目 ID 取当前生效的标注文档（含上传人姓名）。
     *
     * @param projectId 项目ID
     * @return 元数据；不存在返回 null
     */
    ProjectDocVO selectDocByProjectId(@Param("projectId") Long projectId);

    /**
     * 读取原始行（SELECT *），删除旧 MinIO 对象时使用（拿到 objectName/bucketName）。
     *
     * @param projectId 项目ID
     * @return 原始行；不存在返回 null
     */
    Map<String, Object> selectDocRow(@Param("projectId") Long projectId);

    /**
     * 插入/替换文档（按 project_id 唯一键）：先删后插以确保对象路径最新。
     *
     * @param p fileName / objectName / bucketName / fileSize / contentType / projectId / userId
     * @return 受影响行数
     */
    int insertDoc(Map<String, Object> p);

    /**
     * 按项目物理删除文档行（含已软删的历史行）。
     *
     * <p>为什么不用软删：表上 {@code uk_project_id} 是普通 UNIQUE KEY，软删后行仍在表里
     * 仍占用唯一键，再插新行即触发「数据库中已存在该记录」冲突。
     * 项目级单文档场景下审计由操作日志 {@code writeOperationLog} 承担，无需保留历史行。</p>
     *
     * @param projectId 项目ID
     * @return 受影响行数
     */
    int deleteByProjectId(@Param("projectId") Long projectId);
}