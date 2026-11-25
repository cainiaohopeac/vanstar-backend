package org.dromara.label.mapper;

import org.apache.ibatis.annotations.Param;
import org.dromara.label.vo.AnnotationErrorVO;
import org.dromara.label.vo.AnnotationObjectVO;
import org.dromara.label.vo.ClassRefVO;
import org.dromara.label.vo.CommentVO;
import org.dromara.label.vo.FrameVO;
import org.dromara.label.vo.PrecheckVO;
import org.dromara.label.vo.SensorDataVO;
import org.dromara.label.vo.SensorVO;
import org.dromara.label.vo.SequenceVO;

import java.util.List;
import java.util.Map;

/**
 * 序列域数据访问（label_sequence/frame/sensor/calibration/ego_pose/sensor_data/
 * annotation/annotation_object/instance/annotation_error/precheck/comment 等）
 */
public interface SequenceMapper {

    // ---------------- 查询 ----------------

    /** 按项目查询序列，batchId 非空时仅返回该批次下的序列 */
    List<SequenceVO> selectSequences(@Param("projectId") Long projectId, @Param("batchId") Long batchId);

    SequenceVO selectSequence(@Param("id") Long id);

    List<FrameVO> selectFrames(@Param("sequenceId") Long sequenceId);

    List<SensorDataVO> selectSensorDataByFrame(@Param("frameId") Long frameId);

    List<SensorVO> selectSensors(@Param("projectId") Long projectId);

    List<ClassRefVO> selectClassesOfProject(@Param("projectId") Long projectId);

    List<AnnotationObjectVO> selectObjectsOfAnnotation(@Param("annotationId") Long annotationId);

    Long selectLatestAnnotationIdByFrame(@Param("frameId") Long frameId);

    Long selectAnnotationId(@Param("sequenceId") Long sequenceId, @Param("frameId") Long frameId);

    List<AnnotationErrorVO> selectAnnotationErrors(@Param("sequenceId") Long sequenceId);

    List<PrecheckVO> selectPrechecks(@Param("sequenceId") Long sequenceId);

    List<CommentVO> selectComments(@Param("sequenceId") Long sequenceId);

    Long selectSensorId(@Param("projectId") Long projectId, @Param("channel") String channel);

    /** 标定查重（重复解析不重复落库）。 */
    Long selectCalibrationIdByToken(@Param("token") String token);

    /** 位姿查重（重复解析不重复落库）。 */
    Long selectEgoPoseIdByToken(@Param("token") String token);

    /** 序列查重：batchId 非空时限定在批次内查重，为空时按项目查重 */
    Long selectSequenceIdByToken(@Param("projectId") Long projectId,
                                 @Param("batchId") Long batchId,
                                 @Param("token") String token);

    Long selectCalibrationSensorId(@Param("calibrationId") Long calibrationId);

    Long selectInstanceId(@Param("projectId") Long projectId, @Param("instanceToken") String instanceToken);

    // ---------------- 写入 ----------------

    int insertSensor(Map<String, Object> p);

    int insertCalibration(Map<String, Object> p);

    int insertEgoPose(Map<String, Object> p);

    int insertSequence(Map<String, Object> p);

    int insertFrame(Map<String, Object> p);

    int updateFrameNext(@Param("prevId") Long prevId, @Param("frameId") Long frameId);

    int updateSequenceFirstLast(@Param("id") Long id,
                                @Param("firstFrameId") Long firstFrameId,
                                @Param("lastFrameId") Long lastFrameId);

    int insertAnnotation(Map<String, Object> p);

    int touchAnnotation(@Param("id") Long id, @Param("userId") Long userId);

    int deleteAnnotationObjects(@Param("annotationId") Long annotationId);

    int insertAnnotationObject(Map<String, Object> p);

    int touchInstance(@Param("id") Long id, @Param("frameId") Long frameId);

    int insertInstance(Map<String, Object> p);

    int insertSensorData(Map<String, Object> p);

    int deletePrechecks(@Param("sequenceId") Long sequenceId, @Param("frameId") Long frameId);

    int insertPrecheck(Map<String, Object> p);

    int insertComment(Map<String, Object> p);

    int correctError(@Param("id") Long errorId);

    int correctComment(@Param("id") Long commentId);
}
