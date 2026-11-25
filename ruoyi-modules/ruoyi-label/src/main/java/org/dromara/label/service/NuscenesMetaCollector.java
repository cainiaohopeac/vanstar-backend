package org.dromara.label.service;

import java.io.IOException;
import java.io.InputStream;
import org.dromara.label.entity.LabelNuscenesMeta;

/**
 * nuScenes 元数据流式收集器：上传解压（ingestZip）扫描 zip 条目时，
 * 把 v1.0-* 目录下的元数据 JSON 收集到内存，结束后打包归档到 MinIO。
 *
 * <p>生命周期：collect（逐条目调用，流指针位于当前条目）→ hasMeta 判断 → finish 归档落库。</p>
 */
public interface NuscenesMetaCollector {

    /**
     * 收集当前 zip 条目：是 nuScenes 元数据 JSON 则读入内存，否则跳过（流指针仍会被消费）。
     *
     * @param entryName   zip 条目名（含目录层级）
     * @param entryStream 当前条目的输入流（ZipInputStream 本身）
     */
    void collect(String entryName, InputStream entryStream) throws IOException;

    /** 是否收集到元数据（没有任何 v1.0-* JSON 时无需归档）。 */
    boolean hasMeta();

    /** 结束收集：把收集到的 JSON 原样打包上传 MinIO 并登记归档记录。 */
    LabelNuscenesMeta finish();
}
