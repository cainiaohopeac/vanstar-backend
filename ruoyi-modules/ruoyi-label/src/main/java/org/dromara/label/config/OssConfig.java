package org.dromara.label.config;

import io.minio.BucketExistsArgs;
import io.minio.MinioAsyncClient;
import io.minio.MinioClient;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 阿里云 OSS 客户端配置（S3 协议，复用 MinIO SDK）。
 *
 * <p>两个客户端的分工与 {@link MinioConfig} 完全对称：</p>
 * <ul>
 *   <li>{@code ossClient}：服务端读写（stat / copy / sync），走公网 endpoint；</li>
 *   <li>{@code ossSigningClient}：presign 签名专用。SigV4 把 Host 计入签名，
 *       endpoint 必须是浏览器实际访问的地址（OSS 是 virtual-host 风格：
 *       {@code https://<bucket>.<endpoint>}），且必须显式 region 避免签名前发网络探测请求。</li>
 * </ul>
 */
@Configuration
@ConditionalOnProperty(prefix = "oss", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(OssProperties.class)
public class OssConfig {

  private static final Logger LOGGER = LoggerFactory.getLogger(OssConfig.class);

  /** OSS 大对象读写超时与 MinIO 对齐（点云单帧 2~3MB，导入 zip 可达 GB 级） */
  private static OkHttpClient ossHttpClient() {
    return new OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.MINUTES)
        .writeTimeout(60, TimeUnit.MINUTES)
        .build();
  }

  @Bean
  public MinioClient ossClient(OssProperties props) {
    return MinioClient.builder()
        .endpoint(props.getEndpoint())
        .credentials(props.getAccessKey(), props.getSecretKey())
        .region(props.getRegion())
        .httpClient(ossHttpClient())
        .build();
  }

  /**
   * OSS 的异步客户端（分片上传等 MinioAsyncClient 接口用）。
   * MinioClient 与 MinioAsyncClient 在 SDK 里是委托关系而非继承，
   * 主存储切换需要两种类型各一个实例。
   */
  @Bean("ossAsyncClient")
  public MinioAsyncClient ossAsyncClient(OssProperties props) {
    return MinioAsyncClient.builder()
        .endpoint(props.getEndpoint())
        .credentials(props.getAccessKey(), props.getSecretKey())
        .region(props.getRegion())
        .httpClient(ossHttpClient())
        .build();
  }

  @Bean("ossSigningClient")
  public MinioClient ossSigningClient(OssProperties props) {
    // OSS 是 virtual-host 风格：浏览器访问 https://<bucket>.oss-cn-xxx.aliyuncs.com/<key>。
    // MinIO SDK 默认 path-style（endpoint/bucket/key），OSS 同样支持，签名简单且行为一致，
    // 因此直接复用同一 endpoint，无需拼接虚拟域名。
    return MinioClient.builder()
        .endpoint(props.getEndpoint())
        .credentials(props.getAccessKey(), props.getSecretKey())
        .region(props.getRegion())
        .build();
  }

  /** 启动时确认 OSS 桶存在（OSS 桶一般在控制台已创建，这里只校验，不存在则告警不自动建）。 */
  @Bean
  public org.springframework.boot.ApplicationRunner ossBucketVerifier(
      @org.springframework.beans.factory.annotation.Qualifier("ossClient")
      MinioClient ossClient,
      OssProperties props) {
    return args -> {
      try {
        boolean exists =
            ossClient.bucketExists(BucketExistsArgs.builder().bucket(props.getBucket()).build());
        if (exists) {
          LOGGER.info("OSS bucket 已就绪: {} ({})", props.getBucket(), props.getEndpoint());
        } else {
          LOGGER.error("OSS bucket 不存在: {}，请先在阿里云控制台创建", props.getBucket());
        }
      } catch (Exception e) {
        LOGGER.error("OSS 连通性校验失败（endpoint={}）: {}", props.getEndpoint(), e.getMessage());
      }
    };
  }
}
