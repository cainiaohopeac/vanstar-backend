package org.dromara.label.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioAsyncClient;
import io.minio.MinioClient;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO 客户端配置
 * <p>
 * 使用 {@link MinioAsyncClient}：其暴露了分片上传所需的底层接口
 * （createMultipartUploadAsync / uploadPartAsync / listPartsAsync /
 * completeMultipartUploadAsync / abortMultipartUploadAsync），
 * 用于实现大文件分片上传与断点续传。
 */
@Configuration
@EnableConfigurationProperties(MinioProperties.class)
public class MinioConfig {

  private static final Logger LOGGER = LoggerFactory.getLogger(MinioConfig.class);

  @Bean
  public MinioAsyncClient minioClient(MinioProperties props) {
    // 大文件分片上传需要更长的读写超时时间
    OkHttpClient httpClient =
        new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.MINUTES)
            .writeTimeout(60, TimeUnit.MINUTES)
            .build();
    return MinioAsyncClient.builder()
        .endpoint(props.getEndpoint())
        .credentials(props.getAccessKey(), props.getSecretKey())
        .httpClient(httpClient)
        .build();
  }

  /**
   * 预签名专用客户端：endpoint 取「浏览器可访问的地址」（minio.public-endpoint）。
   *
   * <p>SigV4 把 Host 计入签名，所以 endpoint 必须是浏览器实际访问的地址
   * （用内网 endpoint 签出来的 URL 浏览器一访问就 403）。</p>
   *
   * <p><b>必须显式设置 region：</b>不设置时 SDK 的 {@code getPresignedObjectUrl} 并非纯本地计算——
   * 它会先向 endpoint 发一个 location 查询请求。而生产签名 endpoint 是公网地址
   * （如 http://60.217.79.104:9000），应用机在内网访问自己的公网 IP 需要 NAT 回环，
   * 多数网关不支持 → TCP 挂死 → presign 接口 60 秒都不返回（nginx 499/504），
   * 前端直连通道整体失效。显式给定 region（MinIO 默认 us-east-1）后签名退化为纯本地计算。</p>
   */
  @Bean("minioSigningClient")
  public MinioClient minioSigningClient(MinioProperties props) {
    String endpoint = props.getPublicEndpoint();
    if (endpoint == null || endpoint.isBlank()) {
      endpoint = props.getEndpoint();
    }
    return MinioClient.builder()
        .endpoint(endpoint)
        .credentials(props.getAccessKey(), props.getSecretKey())
        .region("us-east-1")
        .build();
  }

  /** 启动时按需自动创建桶。 */
  @Bean
  public ApplicationRunner minioBucketInitializer(
      MinioAsyncClient minioClient, MinioProperties props) {
    return args -> {
      if (!props.isInitializeBucket()) {
        LOGGER.info("MinIO bucket initializer is disabled.");
        return;
      }
      String bucket = props.getBucket();
      try {
        boolean exists =
            minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build()).join();
        if (!exists) {
          minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build()).join();
        }
      } catch (RuntimeException e) {
        LOGGER.warn(
            "Skip MinIO bucket initialization because MinIO is unavailable: {}", e.getMessage());
      }
    };
  }
}
