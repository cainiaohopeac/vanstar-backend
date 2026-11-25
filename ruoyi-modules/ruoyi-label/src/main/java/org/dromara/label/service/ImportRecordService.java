package org.dromara.label.service;

import org.dromara.label.common.PageResult;
import org.dromara.label.dto.ImportCreateDTO;
import org.dromara.label.vo.ImportRecordVO;
import org.dromara.label.vo.ImportResumeVO;
import org.dromara.label.vo.ImportUploadChunkVO;
import org.dromara.label.vo.UploadResultVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 导入记录服务
 */
public interface ImportRecordService {

    /**
     * 创建导入记录并初始化 MinIO 分片上传
     */
    ImportRecordVO createImport(ImportCreateDTO dto);

    /**
     * 上传单个分片，返回进度信息
     */
    ImportUploadChunkVO uploadChunk(Long recordId, Integer partNumber, MultipartFile file);

    /**
     * 合并分片，完成导入。framesPerEntry 为每批帧数上限（图片/点云均按此切分条目），null 回退默认 60。
     */
    ImportRecordVO completeImport(Long recordId, Integer framesPerEntry);

    /**
     * 小文件直传：单请求同步完成上传与入库（跳过分片上传与后台处理），返回入库结果。
     * <p>fileName 支持带相对目录（文件夹上传），入库归入指定批次，行为与分片导入完成后的入库一致。
     * framesPerEntry 为每批帧数上限（图片/点云均按此切分条目），null 回退默认 60。</p>
     */
    UploadResultVO directImport(Long projectId, Long batchId, String fileName, MultipartFile file, Integer framesPerEntry);

    /**
     * 暂停导入
     */
    ImportRecordVO stopImport(Long recordId);

    /**
     * 续传导入，返回记录与已上传分片序号
     */
    ImportResumeVO resumeImport(Long recordId);

    /**
     * 分页查询导入记录（可按项目过滤）
     */
    PageResult<ImportRecordVO> listImports(Long projectId, Integer status, Integer pageNum, Integer pageSize);

    /**
     * 查询导入记录详情
     */
    ImportRecordVO getDetail(Long recordId);

    /**
     * 删除导入记录（同时清理 MinIO 资源）
     */
    void deleteImport(Long recordId);
}
