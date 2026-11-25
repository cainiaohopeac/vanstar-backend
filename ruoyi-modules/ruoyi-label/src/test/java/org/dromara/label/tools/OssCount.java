package org.dromara.label.tools;

import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.Result;
import io.minio.messages.Item;

/** 统计 OSS 桶对象数与总字节；可选第 6 参为前缀过滤（一次性工具）。 */
public final class OssCount {

  public static void main(String[] args) throws Exception {
    MinioClient c = MinioClient.builder()
        .endpoint(args[0]).credentials(args[2], args[3]).region(args[1]).build();
    String prefix = args.length > 5 ? args[5] : "";
    long count = 0;
    long bytes = 0;
    for (Result<Item> r : c.listObjects(
        ListObjectsArgs.builder().bucket(args[4]).prefix(prefix).recursive(true).build())) {
      Item it = r.get();
      count++;
      bytes += it.size();
    }
    System.out.println("OSS 对象数=" + count + ", 总字节=" + bytes + " ("
        + (bytes / 1073741824) + "GB), prefix=" + prefix);
  }
}
