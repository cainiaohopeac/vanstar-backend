package org.dromara.label.mapper;

import org.dromara.label.entity.ImportPart;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 导入分片 Mapper
 */
public interface ImportPartMapper {

    /** 新增或覆盖分片（同一分片重传时更新 etag） */
    int upsert(ImportPart part);

    List<ImportPart> selectByRecordId(@Param("recordId") Long recordId);

    int countByRecordId(@Param("recordId") Long recordId);

    long sumSizeByRecordId(@Param("recordId") Long recordId);

    int deleteByRecordId(@Param("recordId") Long recordId);
}
