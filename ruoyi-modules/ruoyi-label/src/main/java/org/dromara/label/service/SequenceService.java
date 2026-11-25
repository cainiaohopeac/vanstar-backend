package org.dromara.label.service;

import java.util.List;
import org.dromara.label.dto.CommentAddDTO;
import org.dromara.label.dto.SequenceAnnotationSaveDTO;
import org.dromara.label.vo.AnnotationErrorVO;
import org.dromara.label.vo.CommentVO;
import org.dromara.label.vo.FrameVO;
import org.dromara.label.vo.IdVO;
import org.dromara.label.vo.PrecheckVO;
import org.dromara.label.vo.SavedCountVO;
import org.dromara.label.vo.SensorDataVO;
import org.dromara.label.vo.SensorVO;
import org.dromara.label.vo.SequenceAnnotationVO;
import org.dromara.label.vo.SequenceImportVO;
import org.dromara.label.vo.SequenceVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 多传感器多帧序列数据服务（阶段1）。
 *
 * <p>负责 nuScenes 序列导入（解析 v1.0-trainval 元数据 JSON、建立
 * sequence/frame/sensor/calibration/ego_pose/sensor_data 关联链、传感器文件入库 MinIO），
 * 以及序列/帧/传感器数据的查询。</p>
 */
public interface SequenceService {

  /** 项目下的序列列表；batchId 非空时仅返回该批次下的序列。 */
  List<SequenceVO> sequences(Long projectId, Long batchId);

  /** 序列详情。 */
  SequenceVO sequence(Long id);

  /** 序列下的帧列表。 */
  List<FrameVO> frames(Long sequenceId);

  /** 帧下的传感器数据。 */
  List<SensorDataVO> frameSensorData(Long frameId);

  /** 项目下的传感器列表。 */
  List<SensorVO> sensors(Long projectId);

  /** 上传 nuScenes zip 并导入为序列数据到指定批次（batchId 为空时回退默认批次）。 */
  SequenceImportVO importNuscenesSequence(Long projectId, Long batchId, MultipartFile file);

  /** 从已入库对象导入 nuScenes 序列数据到指定批次。 */
  SequenceImportVO importNuscenesFromObject(Long projectId, Long batchId, String zipObjectName);

  /** 查询序列的标注数据。 */
  SequenceAnnotationVO annotationOfSequence(Long sequenceId);

  /** 保存序列标注。 */
  SavedCountVO saveSequenceAnnotation(Long sequenceId, SequenceAnnotationSaveDTO dto);

  /** 序列标注错误列表。 */
  List<AnnotationErrorVO> annotationErrors(Long sequenceId);

  /** 序列质检预检项。 */
  List<PrecheckVO> prechecks(Long sequenceId);

  /** 序列标注评论列表。 */
  List<CommentVO> comments(Long sequenceId);

  /** 添加序列评论。 */
  IdVO addComment(Long sequenceId, CommentAddDTO dto);

  /** 纠正错误项。 */
  void correctError(Long errorId);

  /** 纠正评论。 */
  void correctComment(Long commentId);

  /** 运行预检。 */
  List<PrecheckVO> runPrecheck(Long sequenceId, Long frameId);
}
