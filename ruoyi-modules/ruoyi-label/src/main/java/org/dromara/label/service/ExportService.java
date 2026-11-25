package org.dromara.label.service;

import org.dromara.label.common.PageResult;
import org.dromara.label.dto.ExportCreateDTO;
import org.dromara.label.vo.DeliveryRecordVO;
import org.dromara.label.vo.ExportBatchVO;
import org.dromara.label.vo.ExportDownloadVO;
import org.dromara.label.vo.ExportJobVO;

import java.util.List;

/**
 * 导出交付域服务：导出任务创建/列表/详情/取消/下载清单生成、交付记录。
 */
public interface ExportService {

  /** 导出任务列表。 */
  PageResult<ExportJobVO> exports();

  /** 项目下可导出（已完成：批次内全部条目合格）的批次列表，供创建导出时选择。 */
  List<ExportBatchVO> exportBatches(Long projectId);

  /** 创建导出任务（异步）：校验项目/批次（需为项目下已完成批次）、格式（JSON/KITTI）与合格数据门槛后，
   * 插入 processing 任务并提交后台线程池生成 ZIP 上传 MinIO，立即返回任务详情，前端轮询列表获取进度。 */
  ExportJobVO createExport(ExportCreateDTO dto);

  /** 导出任务详情。 */
  ExportJobVO export(Long id);

  /** 取消导出任务（仅 success 状态不可取消）。 */
  ExportJobVO cancelExport(Long id);

  /** 下载导出：仅 success 状态可下载，返回 ZIP 下载地址（/uploads/** 网关相对路径）与合格数据清单。 */
  ExportDownloadVO downloadExport(Long id);

  /** 交付记录列表。 */
  PageResult<DeliveryRecordVO> deliveryRecords();
}
