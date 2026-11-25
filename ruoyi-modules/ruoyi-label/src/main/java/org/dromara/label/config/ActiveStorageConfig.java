package org.dromara.label.config;

import io.minio.MinioAsyncClient;
import io.minio.MinioClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 主存储客户端选择器：按 {@code oss.enabled} 在 OSS 与 MinIO 之间做「硬切换」。
 *
 * <p>{@code true}：上传（含分片）、服务端读取、直链签名全部直连 OSS，
 * MinIO 完全不参与，两套存储之间不存在任何同步；
 * {@code false}：全部走 MinIO，与引入 OSS 之前的行为完全一致。</p>
 *
 * <p>两套存储桶名相同（mark-server）、对象 key 一致，因此业务层
 * （{@code MinioServiceImpl} 等）无需感知当前用的是哪套存储：
 * 切换时只需保证目标存储里已有全部对象。</p>
 */
@Configuration
public class ActiveStorageConfig {

  /** 服务端读写主客户端（上传/分片上传/openObject/复制/建桶都经它）。 */
  @Bean
  @Primary
  public MinioAsyncClient activeStorageClient(
      ObjectProvider<OssProperties> ossProps,
      @Qualifier("minioClient") MinioAsyncClient minioAsyncClient,
      @Qualifier("ossAsyncClient") ObjectProvider<MinioAsyncClient> ossAsyncClient) {
    OssProperties props = ossProps.getIfAvailable();
    if (props != null && props.isEnabled() && ossAsyncClient.getIfAvailable() != null) {
      return ossAsyncClient.getIfAvailable();
    }
    return minioAsyncClient;
  }

  /**
   * presign 签名客户端（纯本地计算，不发生网络请求）。
   * endpoint 即浏览器实际访问的地址：OSS 模式是公网域名，MinIO 模式是 public-endpoint。
   */
  @Bean("activeSigningClient")
  @Primary
  public MinioClient activeSigningClient(
      ObjectProvider<OssProperties> ossProps,
      @Qualifier("minioSigningClient") MinioClient minioSigningClient,
      @Qualifier("ossSigningClient") ObjectProvider<MinioClient> ossSigningClient) {
    OssProperties props = ossProps.getIfAvailable();
    if (props != null && props.isEnabled() && ossSigningClient.getIfAvailable() != null) {
      return ossSigningClient.getIfAvailable();
    }
    return minioSigningClient;
  }
}
