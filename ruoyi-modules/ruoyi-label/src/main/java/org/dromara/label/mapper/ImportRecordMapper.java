package org.dromara.label.mapper;

import org.dromara.label.entity.ImportRecord;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 导入记录 Mapper
 */
public interface ImportRecordMapper {

    int insert(ImportRecord record);

    ImportRecord selectById(@Param("id") Long id);

    /** 按 ID + 用户查询（用户只能操作自己的导入记录） */
    ImportRecord selectByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

    /** 查询导入记录：支持按用户、项目、状态过滤 */
    List<ImportRecord> selectByUserId(@Param("userId") Long userId,
                                      @Param("projectId") Long projectId,
                                      @Param("status") Integer status,
                                      @Param("offset") int offset,
                                      @Param("limit") int limit);

    long countByUserId(@Param("userId") Long userId,
                       @Param("projectId") Long projectId,
                       @Param("status") Integer status);

    int updateUploadId(@Param("id") Long id, @Param("uploadId") String uploadId);

    int updateStatus(@Param("id") Long id, @Param("status") int status);

    int updateStatusAndError(@Param("id") Long id, @Param("status") int status, @Param("errorMsg") String errorMsg);

    int updateProgress(@Param("id") Long id, @Param("uploadedChunks") int uploadedChunks, @Param("uploadedBytes") long uploadedBytes);

    int updateFinish(@Param("id") Long id);

    /** 抢占式提交后台处理：仅当记录仍为待上传(0)/上传中(1)时置为处理中(5)，返回影响行数，0 表示已被并发处理 */
    int tryStartProcessing(@Param("id") Long id);

    int deleteById(@Param("id") Long id);
}
