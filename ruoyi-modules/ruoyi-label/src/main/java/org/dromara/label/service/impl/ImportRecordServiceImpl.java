package org.dromara.label.service.impl;

import org.dromara.common.core.exception.ServiceException;
import org.dromara.label.common.PageResult;
import org.dromara.label.config.MinioProperties;
import org.dromara.label.dto.ImportCreateDTO;
import org.dromara.label.dto.NuscenesParseDTO;
import org.dromara.label.entity.ImportPart;
import org.dromara.label.entity.ImportRecord;
import org.dromara.label.mapper.ImportPartMapper;
import org.dromara.label.mapper.ImportRecordMapper;
import org.dromara.label.service.BaseService;
import org.dromara.label.service.ImportRecordService;
import org.dromara.label.service.MinioService;
import org.dromara.label.service.ProjectService;
import org.dromara.label.service.NuscenesImportService;
import org.dromara.label.vo.ImportRecordVO;
import org.dromara.label.vo.ImportResumeVO;
import org.dromara.label.vo.ImportUploadChunkVO;
import org.dromara.label.vo.UploadResultVO;
import io.minio.messages.Part;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 导入记录服务实现
 * <p>
 * 基于 MinIO 分片上传（multipart upload）实现大文件异步导入与断点续传：
 * <ul>
 *     <li>创建导入时在 MinIO 初始化分片上传，生成 uploadId</li>
 *     <li>每个分片独立上传，成功后在 import_part 表记录分片序号与 ETag</li>
 *     <li>暂停/中断后，分片与 uploadId 保留在 MinIO，续传时跳过已上传分片</li>
 *     <li>全部上传完成后调用 completeMultipartUpload 合并，生成最终对象</li>
 * </ul>
 */
@Slf4j
@Service
public class ImportRecordServiceImpl extends BaseService implements ImportRecordService {

    /** 单次分片上传允许的最大分片数（MinIO 限制为 10000） */
    private static final int MAX_PARTS = 10000;
    /** MinIO 最小分片大小（5MB，最后一个分片除外） */
    private static final long MIN_PART_SIZE = 5L * 1024 * 1024;

    @Autowired
    private ImportRecordMapper recordMapper;

    @Autowired
    private ImportPartMapper partMapper;

    @Autowired
    private MinioService minioService;

    @Autowired
    private MinioProperties minioProperties;

    @Autowired
    private ProjectService projectService;

    @Autowired
    private NuscenesImportService nuscenesImportService;

    /** 导入后台处理线程池：分片合并 + 资产入库 */
    @Autowired
    @Qualifier("labelImportExecutor")
    private ThreadPoolTaskExecutor importExecutor;

    @Override
    public ImportRecordVO createImport(ImportCreateDTO dto) {
        Long userId = currentUserId();
        long fileSize = dto.getFileSize();
        long chunkSize = dto.getChunkSize() != null && dto.getChunkSize() >= MIN_PART_SIZE
                ? dto.getChunkSize()
                : minioProperties.getChunkSize();

        int totalChunks = (int) ((fileSize + chunkSize - 1) / chunkSize);
        if (totalChunks > MAX_PARTS) {
            chunkSize = (fileSize + MAX_PARTS - 1) / MAX_PARTS;
            totalChunks = MAX_PARTS;
        }

        String fileName = sanitizeFileName(dto.getFileName());
        String objectName = buildObjectName(userId, fileName);

        ImportRecord record = new ImportRecord();
        record.setUserId(userId);
        record.setProjectId(dto.getProjectId());
        record.setBatchId(resolveBatchId(dto.getProjectId(), dto.getBatchId()));
        record.setFileName(fileName);
        record.setFileSize(fileSize);
        record.setObjectName(objectName);
        record.setBucketName(minioProperties.getBucket());
        record.setChunkSize(chunkSize);
        record.setTotalChunks(totalChunks);
        record.setUploadedChunks(0);
        record.setUploadedBytes(0L);
        record.setStatus(ImportRecord.STATUS_PENDING);
        recordMapper.insert(record);

        try {
            String uploadId = minioService.createMultipartUpload(objectName);
            recordMapper.updateUploadId(record.getId(), uploadId);
            record.setUploadId(uploadId);
            record.setStatus(ImportRecord.STATUS_UPLOADING);
        } catch (Exception e) {
            recordMapper.updateStatusAndError(record.getId(), ImportRecord.STATUS_FAILED, e.getMessage());
            throw e;
        }
        return toVO(recordMapper.selectById(record.getId()));
    }

    @Override
    public ImportUploadChunkVO uploadChunk(Long recordId, Integer partNumber, MultipartFile file) {
        Long userId = currentUserId();
        ImportRecord record = getOwnedRecord(userId, recordId);
        int status = record.getStatus();
        if (status == ImportRecord.STATUS_COMPLETED || status == ImportRecord.STATUS_FAILED) {
            throw new ServiceException("当前导入状态不允许上传分片，请先续传");
        }
        if (status == ImportRecord.STATUS_PROCESSING) {
            throw new ServiceException("导入正在后台处理中，无法继续上传分片");
        }
        if (record.getUploadId() == null) {
            throw new ServiceException("导入尚未初始化，请先创建导入");
        }
        if (partNumber == null || partNumber < 1 || partNumber > record.getTotalChunks()) {
            throw new ServiceException("分片序号不合法");
        }
        long expectSize = expectPartSize(record, partNumber);
        if (file == null || file.isEmpty()) {
            throw new ServiceException("分片内容为空");
        }
        if (file.getSize() != expectSize) {
            throw new ServiceException("分片大小不正确，期望 " + expectSize + " 字节，实际 " + file.getSize() + " 字节");
        }

        String etag;
        try (InputStream in = file.getInputStream()) {
            etag = minioService.uploadPart(record.getObjectName(), record.getUploadId(), partNumber, in, file.getSize());
        } catch (IOException e) {
            recordMapper.updateStatusAndError(recordId, ImportRecord.STATUS_FAILED, "读取分片失败：" + e.getMessage());
            throw new RuntimeException("读取分片失败：" + e.getMessage(), e);
        } catch (Exception e) {
            recordMapper.updateStatusAndError(recordId, ImportRecord.STATUS_FAILED, e.getMessage());
            throw e;
        }

        ImportPart part = new ImportPart();
        part.setRecordId(recordId);
        part.setPartNumber(partNumber);
        part.setEtag(etag);
        part.setPartSize(file.getSize());
        partMapper.upsert(part);

        int uploadedChunks = partMapper.countByRecordId(recordId);
        long uploadedBytes = partMapper.sumSizeByRecordId(recordId);
        recordMapper.updateProgress(recordId, uploadedChunks, uploadedBytes);

        return new ImportUploadChunkVO(uploadedChunks, uploadedBytes, record.getTotalChunks());
    }

    @Override
    public ImportRecordVO completeImport(Long recordId, Integer framesPerEntry) {
        Long userId = currentUserId();
        ImportRecord record = getOwnedRecord(userId, recordId);
        if (record.getStatus() == ImportRecord.STATUS_COMPLETED) {
            return toVO(record);
        }
        if (record.getStatus() == ImportRecord.STATUS_PROCESSING) {
            // 已提交后台处理，直接返回当前状态供前端轮询
            return toVO(record);
        }
        if (record.getStatus() != ImportRecord.STATUS_UPLOADING && record.getStatus() != ImportRecord.STATUS_PENDING) {
            throw new ServiceException("当前导入状态无法完成，请先续传");
        }
        if (record.getUploadId() == null) {
            throw new ServiceException("导入尚未初始化，请先创建导入");
        }
        List<ImportPart> parts = partMapper.selectByRecordId(recordId);
        if (parts.size() < record.getTotalChunks()) {
            throw new ServiceException("还有 " + (record.getTotalChunks() - parts.size()) + " 个分片未上传");
        }
        // 抢占状态：待上传/上传中 -> 处理中，避免重复提交后台任务（影响行数 0 表示已被并发提交）
        if (recordMapper.tryStartProcessing(recordId) == 0) {
            return toVO(recordMapper.selectById(recordId));
        }
        try {
            importExecutor.execute(() -> finalizeImport(recordId, userId, framesPerEntry));
        } catch (RuntimeException e) {
            // 线程池不可用（如应用关闭中），回写失败状态避免记录悬挂在“处理中”
            recordMapper.updateStatusAndError(recordId, ImportRecord.STATUS_FAILED,
                    "无法提交后台处理任务：" + e.getMessage());
            throw e;
        }
        return toVO(recordMapper.selectById(recordId));
    }

    /**
     * 小文件直传：上传到 MinIO 临时对象后复用分片导入的入库逻辑（支持相对目录与批次归组），
     * 同步执行、一次请求完成，适合海量小文件批量导入；入库后临时对象即被清理。
     */
    @Override
    public UploadResultVO directImport(Long projectId, Long batchId, String fileName, MultipartFile file, Integer framesPerEntry) {
        if (file == null || file.isEmpty()) {
            throw new ServiceException("上传文件不能为空");
        }
        Long userId = currentUserId();
        String relPath = sanitizeFileName(fileName);
        String objectName = buildObjectName(userId, relPath);
        try (InputStream in = file.getInputStream()) {
            minioService.putObject(objectName, in, file.getSize(), minioService.contentTypeFor(relPath));
        } catch (IOException e) {
            throw new ServiceException("读取上传文件失败：" + e.getMessage());
        } catch (Exception e) {
            throw new ServiceException("上传文件失败：" + e.getMessage());
        }
        try {
            UploadResultVO importResult = projectService.importStoredObject(projectId, batchId, relPath, objectName, framesPerEntry);
            autoParseNuscenesAfterImport(importResult);
            return importResult;
        } finally {
            try {
                // 入库已通过 copyObject/流式解压落位，临时对象用完即清
                minioService.removeObject(objectName);
            } catch (Exception ignore) {
                // 清理失败不影响导入结果
            }
        }
    }

    @Override
    public ImportRecordVO stopImport(Long recordId) {
        Long userId = currentUserId();
        ImportRecord record = getOwnedRecord(userId, recordId);
        if (record.getStatus() == ImportRecord.STATUS_COMPLETED
                || record.getStatus() == ImportRecord.STATUS_FAILED
                || record.getStatus() == ImportRecord.STATUS_PROCESSING) {
            throw new ServiceException("当前导入状态无法暂停");
        }
        recordMapper.updateStatus(recordId, ImportRecord.STATUS_PAUSED);
        return toVO(recordMapper.selectById(recordId));
    }

    @Override
    public ImportResumeVO resumeImport(Long recordId) {
        Long userId = currentUserId();
        ImportRecord record = getOwnedRecord(userId, recordId);
        if (record.getStatus() == ImportRecord.STATUS_COMPLETED) {
            throw new ServiceException("该导入已完成，无需续传");
        }
        if (record.getStatus() == ImportRecord.STATUS_PROCESSING) {
            throw new ServiceException("导入正在后台处理中，无需续传");
        }
        if (record.getUploadId() == null) {
            // 从未开始上传过，重新初始化分片上传
            String uploadId = minioService.createMultipartUpload(record.getObjectName());
            recordMapper.updateUploadId(recordId, uploadId);
            record.setUploadId(uploadId);
        } else if (!minioService.uploadExists(record.getObjectName(), record.getUploadId())) {
            // MinIO 上的分片上传已失效（如被服务端清理），重建并清空分片记录
            String uploadId = minioService.createMultipartUpload(record.getObjectName());
            partMapper.deleteByRecordId(recordId);
            recordMapper.updateUploadId(recordId, uploadId);
            record.setUploadId(uploadId);
        }
        recordMapper.updateStatus(recordId, ImportRecord.STATUS_UPLOADING);
        record.setStatus(ImportRecord.STATUS_UPLOADING);

        List<Integer> uploadedPartNumbers = partMapper.selectByRecordId(recordId).stream()
                .map(ImportPart::getPartNumber)
                .collect(Collectors.toList());

        ImportResumeVO result = new ImportResumeVO();
        result.setRecord(toVO(record));
        result.setUploadedPartNumbers(uploadedPartNumbers);
        return result;
    }

    @Override
    public PageResult<ImportRecordVO> listImports(Long projectId, Integer status, Integer pageNum, Integer pageSize) {
        Long userId = currentUserId();
        int page = pageNum == null || pageNum < 1 ? 1 : pageNum;
        int size = pageSize == null || pageSize < 1 ? 10 : Math.min(pageSize, 100);
        int offset = (page - 1) * size;

        List<ImportRecord> list = recordMapper.selectByUserId(userId, projectId, status, offset, size);
        long total = recordMapper.countByUserId(userId, projectId, status);

        List<ImportRecordVO> voList = list.stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        return new PageResult<>(total, voList);
    }

    @Override
    public ImportRecordVO getDetail(Long recordId) {
        return toVO(getOwnedRecord(currentUserId(), recordId));
    }

    @Override
    public void deleteImport(Long recordId) {
        ImportRecord record = getOwnedRecord(currentUserId(), recordId);
        if (record.getStatus() == ImportRecord.STATUS_PROCESSING) {
            throw new ServiceException("导入正在后台处理中，请稍后再删除");
        }
        try {
            if (record.getStatus() == ImportRecord.STATUS_COMPLETED) {
                minioService.removeObject(record.getObjectName());
            } else if (record.getUploadId() != null) {
                minioService.abortMultipartUpload(record.getObjectName(), record.getUploadId());
            }
        } catch (Exception e) {
            // 清理 MinIO 资源失败不影响删除记录，尽力而为
        }
        partMapper.deleteByRecordId(recordId);
        recordMapper.deleteById(recordId);
    }

    /**
     * 后台执行导入收尾：MinIO 合并分片 + 将最终对象导入为项目资产（zip 解压 / 单文件复制入库）。
     * 由 completeImport 提交到 labelImportExecutor 线程池执行，结果回写导入记录状态。
     */
    private void finalizeImport(Long recordId, Long userId, Integer framesPerEntry) {
        try {
            ImportRecord record = recordMapper.selectByIdAndUserId(recordId, userId);
            if (record == null) {
                log.warn("导入记录不存在或已删除，跳过后台处理：id={}", recordId);
                return;
            }
            if (record.getStatus() != ImportRecord.STATUS_PROCESSING) {
                // 状态已被其它流程变更（如重新上传/删除），不再处理
                return;
            }
            List<ImportPart> parts = partMapper.selectByRecordId(recordId);
            if (parts.size() < record.getTotalChunks()) {
                throw new IllegalStateException(
                        "还有 " + (record.getTotalChunks() - parts.size()) + " 个分片未上传");
            }
            Part[] partArray = parts.stream()
                    .map(p -> new Part(p.getPartNumber(), p.getEtag()))
                    .toArray(Part[]::new);
            // 1. MinIO 合并分片，生成最终对象
            minioService.completeMultipartUpload(record.getObjectName(), record.getUploadId(), partArray);
            // 2. 将最终对象导入为项目资产（zip 解压 / 单文件复制），归入记录所属批次
            UploadResultVO importResult = projectService.importStoredObject(
                    record.getProjectId(), record.getBatchId(), record.getFileName(), record.getObjectName(), framesPerEntry);
            recordMapper.updateFinish(recordId);
            // 导入结束（入库事务已提交）后自动解析 nuScenes 元数据并预标注（见下方说明）
            autoParseNuscenesAfterImport(importResult);
            log.info("导入后台处理完成：recordId={}", recordId);
        } catch (Exception e) {
            log.error("导入后台处理失败：recordId={}", recordId, e);
            recordMapper.updateStatusAndError(recordId, ImportRecord.STATUS_FAILED,
                    e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
        }
    }

    /**
     * 导入完成后（入库事务已提交）自动解析 nuScenes 元数据并预标注。
     * <p>ingestZip 在入库事务内已尝试同步解析，但彼时资产尚未提交、对其他事务不可见，
     * 易解析失败被静默吞掉；此处在其返回（事务已提交）后兜底触发，确保 nuScenes 数据包导入即自动预标注，
     * 无需在批次面板手动点「预标注」。非 nuScenes 数据包（无元数据归档）由 parseUpload 内部判定后跳过，零副作用。</p>
     */
    private void autoParseNuscenesAfterImport(UploadResultVO result) {
        if (result == null || result.getUploadId() == null) {
            return;
        }
        try {
            NuscenesParseDTO parseRequest = new NuscenesParseDTO();
            parseRequest.setUploadId(result.getUploadId());
            nuscenesImportService.parseUpload(parseRequest);
        } catch (Exception e) {
            // 解析失败不影响导入主流程；归档状态已由解析侧回写，可在批次面板点「预标注」手动重试
            log.warn("导入完成后 nuScenes 自动解析失败（可在批次面板点「预标注」手动重试）：uploadId={}",
                    result.getUploadId(), e);
        }
    }

    private ImportRecord getOwnedRecord(Long userId, Long recordId) {
        ImportRecord record = recordMapper.selectByIdAndUserId(recordId, userId);
        if (record == null) {
            throw new ServiceException("导入记录不存在");
        }
        return record;
    }

    /** 实体转 VO */
    private ImportRecordVO toVO(ImportRecord record) {
        if (record == null) {
            return null;
        }
        ImportRecordVO vo = new ImportRecordVO();
        BeanUtils.copyProperties(record, vo);
        return vo;
    }

    /** 计算指定分片的期望大小 */
    private long expectPartSize(ImportRecord record, int partNumber) {
        if (partNumber == record.getTotalChunks()) {
            return record.getFileSize() - (long) (record.getTotalChunks() - 1) * record.getChunkSize();
        }
        return record.getChunkSize();
    }

    /**
     * 清洗文件名：保留相对目录层级（文件夹上传时前端传入相对路径），
     * 逐段去除非法字符并防目录穿越；非法时回退为 file。
     */
    private String sanitizeFileName(String name) {
        String safe = sanitizeRelativePath(name);
        return safe.isEmpty() ? "file" : safe;
    }

    /** 生成 MinIO 对象名：mark-server/{userId}/{yyyyMMdd}/{uuid}_{文件名}（仅取末级文件名，不带目录） */
    private String buildObjectName(Long userId, String fileName) {
        String date = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        String uuid = UUID.randomUUID().toString().replace("-", "");
        int slash = fileName.lastIndexOf('/');
        String baseName = slash < 0 ? fileName : fileName.substring(slash + 1);
        return "mark-server/" + userId + "/" + date + "/" + uuid + "_" + baseName;
    }
}
