package org.dromara.label.service.impl;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import org.dromara.label.config.MinioProperties;
import org.dromara.label.service.MinioService;
import io.minio.BucketExistsArgs;
import io.minio.CopyObjectArgs;
import io.minio.CopySource;
import io.minio.CreateMultipartUploadResponse;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioAsyncClient;
import io.minio.MinioClient;
import io.minio.http.Method;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.UploadPartResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.Part;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

/**
 * MinIO 对象存储服务
 * <p>
 * 职责：
 * <ul>
 *   <li>流式上传：{@link #putObject}（已知大小直接 PUT，size=-1 未知大小时 SDK 自动分片）</li>
 *   <li>服务端复制：{@link #copyObject}（大文件导入完成后复制到资产目录，无需回传）</li>
 *   <li>流式读取：{@link #openObject}（文件网关 /uploads/** 使用）</li>
 *   <li>大文件分片上传（multipart）：createMultipartUpload / uploadPart / complete…</li>
 * </ul>
 */
@Service
public class MinioServiceImpl implements MinioService {

    /** 上传对象失败自动重试总次数（含首次尝试） */
    private static final int PUT_RETRY_MAX_ATTEMPTS = 3;

    /** 上传重试退避基数（毫秒）：第 n 次失败后等待 n × 基数再重试 */
    private static final long PUT_RETRY_BACKOFF_BASE_MILLIS = 2000L;

    @Autowired
    private MinioAsyncClient minioClient;

    /**
     * 预签名专用客户端：endpoint 是「浏览器可访问的地址」，只做本地签名计算（不发网络请求）。
     * 注入的是主存储对应的签名客户端（{@code activeSigningClient}，@Primary）：
     * oss.enabled=true 时连 OSS 公网域名，false 时连 MinIO 的 public-endpoint。
     */
    @Autowired
    private MinioClient signingClient;

    @Autowired
    private MinioProperties minioProperties;

    // ---------------- 基础写入 ----------------

    /**
     * 确保桶存在（幂等），启动迁移/上传前可调用。
     */
    public void ensureBucket() {
        try {
            String bucket = minioProperties.getBucket();
            boolean exists = minioClient
                    .bucketExists(BucketExistsArgs.builder().bucket(bucket).build())
                    .join();
            if (!exists) {
                minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build()).join();
            }
        } catch (CompletionException e) {
            throw new RuntimeException("初始化桶失败：" + rootMessage(e), e);
        } catch (Exception e) {
            throw new RuntimeException("初始化桶失败：" + e.getMessage(), e);
        }
    }

    /**
     * 上传字节流到 MinIO（网络类失败自动重试）。
     * <p>
     * size &gt;= 0 表示调用方已知对象大小，走普通 PUT；
     * size = -1 表示未知，SDK 自动分片上传（流式处理，内存仅缓冲单个分片）。
     * <p>
     * 重试语义：okhttp 对「已开始写请求体」的流式上传不会自行重试（body 不可重放），
     * 连接池中的闲置连接被防火墙/杀毒软件掐掉后会直接抛 SocketException。
     * 因此仅当流可重放（{@code markSupported()}，如 ByteArrayInputStream）且
     * 根因为网络 IO 异常时，退避后 {@code reset()} 回绕到流起点重试；
     * S3 业务错误（如 NoSuchBucket）与不可重放流保持单次执行原行为。
     */
    public void putObject(String key, InputStream data, long size, String contentType) {
        // 1. 可重放判定：只有内存流支持 reset，失败后才能从头重传
        boolean replayable = data.markSupported();
        RuntimeException lastError = null;
        for (int attempt = 1; attempt <= PUT_RETRY_MAX_ATTEMPTS; attempt++) {
            try {
                doPutObject(key, data, size, contentType);
                return;
            } catch (RuntimeException e) {
                lastError = e;
                // 2. 终止重试：流不可重放 / 根因非网络 IO / 已是最后一次尝试
                if (!replayable || attempt == PUT_RETRY_MAX_ATTEMPTS
                        || !(rootCause(e) instanceof IOException)) {
                    throw e;
                }
                // 3. 网络类失败（连接被中止/超时）：退避后回绕流到起点重试
                sleepBeforeRetry(PUT_RETRY_BACKOFF_BASE_MILLIS * attempt, e);
                try {
                    data.reset();
                } catch (IOException resetError) {
                    throw e;
                }
            }
        }
        throw lastError;
    }

    /** 单次执行上传（原 putObject 逻辑）。 */
    private void doPutObject(String key, InputStream data, long size, String contentType) {
        try {
            long partSize = size == -1L ? minioProperties.getChunkSize() : -1L;
            minioClient
                    .putObject(
                            PutObjectArgs.builder()
                                    .bucket(minioProperties.getBucket())
                                    .object(key)
                                    .stream(data, size, partSize)
                                    .contentType(contentType == null ? "application/octet-stream" : contentType)
                                    .build())
                    .join();
        } catch (CompletionException e) {
            throw new RuntimeException("上传对象失败：" + rootMessage(e), e);
        } catch (Exception e) {
            throw new RuntimeException("上传对象失败：" + e.getMessage(), e);
        }
    }

    /** 重试前退避等待；被中断则恢复中断标记并交还原异常（放弃重试）。 */
    private static void sleepBeforeRetry(long millis, RuntimeException cause) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw cause;
        }
    }

    /**
     * 服务端复制对象（同桶），用于大文件导入完成后将对象归档到资产目录，无需回传本地。
     */
    public void copyObject(String sourceKey, String targetKey) {
        try {
            minioClient
                    .copyObject(
                            CopyObjectArgs.builder()
                                    .bucket(minioProperties.getBucket())
                                    .object(targetKey)
                                    .source(
                                            CopySource.builder()
                                                    .bucket(minioProperties.getBucket())
                                                    .object(sourceKey)
                                                    .build())
                                    .build())
                    .join();
        } catch (CompletionException e) {
            throw new RuntimeException("复制对象失败：" + rootMessage(e), e);
        } catch (Exception e) {
            throw new RuntimeException("复制对象失败：" + e.getMessage(), e);
        }
    }

    /**
     * 删除对象
     */
    public void removeObject(String objectName) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                            .bucket(minioProperties.getBucket())
                            .object(objectName)
                            .build())
                    .join();
        } catch (CompletionException e) {
            throw new RuntimeException("删除对象失败：" + rootMessage(e), e);
        } catch (Exception e) {
            throw new RuntimeException("删除对象失败：" + e.getMessage(), e);
        }
    }

    // ---------------- 读取 ----------------

    /**
     * 打开对象的只读流（调用方负责关闭）。
     */
    public InputStream openObject(String key) {
        try {
            return getObjectStream(key);
        } catch (Exception e) {
            throw new RuntimeException("打开对象失败：" + e.getMessage(), e);
        }
    }

    /**
     * 查询对象信息（大小 / Content-Type）。对象不存在返回 null。
     */
    public StatObjectResponse statObject(String key) {
        try {
            return minioClient
                    .statObject(
                            StatObjectArgs.builder()
                                    .bucket(minioProperties.getBucket())
                                    .object(key)
                                    .build())
                    .join();
        } catch (CompletionException e) {
            Throwable root = rootCause(e);
            if (root instanceof ErrorResponseException err
                    && "NoSuchKey".equals(err.errorResponse().code())) {
                return null;
            }
            throw new RuntimeException("查询对象信息失败：" + rootMessage(e), e);
        } catch (Exception e) {
            throw new RuntimeException("查询对象信息失败：" + e.getMessage(), e);
        }
    }

    private InputStream getObjectStream(String objectName) throws Exception {
        return minioClient.getObject(
                        GetObjectArgs.builder()
                                .bucket(minioProperties.getBucket())
                                .object(objectName)
                                .build())
                .join();
    }

    // ---------------- 大文件分片上传（multipart） ----------------

    /**
     * 创建分片上传，返回 uploadId
     */
    public String createMultipartUpload(String objectName) {
        try {
            CreateMultipartUploadResponse resp = minioClient
                    .createMultipartUploadAsync(minioProperties.getBucket(), null, objectName,
                            emptyMultimap(), emptyMultimap())
                    .join();
            return resp.result().uploadId();
        } catch (CompletionException e) {
            throw new RuntimeException("创建 MinIO 分片上传失败：" + rootMessage(e), e);
        } catch (Exception e) {
            throw new RuntimeException("创建 MinIO 分片上传失败：" + e.getMessage(), e);
        }
    }

    /**
     * 上传单个分片，返回 ETag
     */
    public String uploadPart(String objectName, String uploadId, int partNumber, InputStream data, long size) {
        try {
            UploadPartResponse resp = minioClient
                    .uploadPartAsync(minioProperties.getBucket(), null, objectName, data, size,
                            uploadId, partNumber, emptyMultimap(), emptyMultimap())
                    .join();
            return resp.etag();
        } catch (CompletionException e) {
            throw new RuntimeException("上传分片失败：" + rootMessage(e), e);
        } catch (Exception e) {
            throw new RuntimeException("上传分片失败：" + e.getMessage(), e);
        }
    }

    /**
     * 校验分片上传是否仍然有效（用于续传前检查）
     */
    public boolean uploadExists(String objectName, String uploadId) {
        try {
            minioClient.listPartsAsync(minioProperties.getBucket(), null, objectName,
                            1, 0, uploadId, emptyMultimap(), emptyMultimap())
                    .join();
            return true;
        } catch (CompletionException e) {
            Throwable root = rootCause(e);
            if (root instanceof ErrorResponseException err
                    && "NoSuchUpload".equals(err.errorResponse().code())) {
                return false;
            }
            throw new RuntimeException("查询分片上传状态失败：" + rootMessage(e), e);
        } catch (Exception e) {
            throw new RuntimeException("查询分片上传状态失败：" + e.getMessage(), e);
        }
    }

    /**
     * 合并分片，完成上传
     */
    public void completeMultipartUpload(String objectName, String uploadId, Part[] parts) {
        try {
            minioClient.completeMultipartUploadAsync(minioProperties.getBucket(), null, objectName,
                            uploadId, parts, emptyMultimap(), emptyMultimap())
                    .join();
        } catch (CompletionException e) {
            throw new RuntimeException("合并分片失败：" + rootMessage(e), e);
        } catch (Exception e) {
            throw new RuntimeException("合并分片失败：" + e.getMessage(), e);
        }
    }

    /**
     * 终止分片上传（删除未完成的分片，用于删除记录）
     */
    public void abortMultipartUpload(String objectName, String uploadId) {
        try {
            minioClient.abortMultipartUploadAsync(minioProperties.getBucket(), null, objectName,
                            uploadId, emptyMultimap(), emptyMultimap())
                    .join();
        } catch (CompletionException e) {
            throw new RuntimeException("终止分片上传失败：" + rootMessage(e), e);
        } catch (Exception e) {
            throw new RuntimeException("终止分片上传失败：" + e.getMessage(), e);
        }
    }

    // ---------------- 直链 ----------------

    /**
     * 生成预签名 GET 直链：浏览器可直连 MinIO 拉点云/图片，不必经业务后端转发字节。
     * <p>
     * 用独立的 signing client：它的 endpoint 是「浏览器实际访问的公网地址」，
     * SigV4 会把 Host 算进签名，用内网 endpoint 签出来的链接浏览器一访问就 403。
     */
    @Override
    public String presignedGetUrl(String key, Duration expiry) {
        if (key == null || key.isBlank()) {
            return null;
        }
        try {
            long seconds = expiry == null ? 3600L : expiry.getSeconds();
            // MinIO 允许 1 秒 ~ 7 天，超出直接报错，这里先夹紧
            int bounded = (int) Math.max(60L, Math.min(seconds, 7L * 24 * 3600));
            return signingClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(minioProperties.getBucket())
                            .object(key)
                            .expiry(bounded, TimeUnit.SECONDS)
                            .build());
        } catch (Exception e) {
            throw new RuntimeException("生成预签名直链失败：" + key, e);
        }
    }

    // ---------------- 工具 ----------------

    /**
     * 按文件名推断 Content-Type；未知类型返回 application/octet-stream。
     */
    public String contentTypeFor(String name) {
        if (name == null) {
            return "application/octet-stream";
        }
        String lower = name.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        if (lower.endsWith(".bmp")) {
            return "image/bmp";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".svg")) {
            return "image/svg+xml";
        }
        if (lower.endsWith(".zip")) {
            return "application/zip";
        }
        if (lower.endsWith(".json")) {
            return "application/json";
        }
        return "application/octet-stream";
    }

    private Multimap<String, String> emptyMultimap() {
        return HashMultimap.create();
    }

    private static Throwable rootCause(Throwable t) {
        Throwable cur = t;
        while (cur.getCause() != null && cur.getCause() != cur) {
            cur = cur.getCause();
        }
        return cur;
    }

    private static String rootMessage(Throwable t) {
        String msg = rootCause(t).getMessage();
        return (msg == null || msg.isBlank()) ? t.getMessage() : msg;
    }
}
