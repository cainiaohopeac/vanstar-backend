package org.dromara.label.service;

import java.util.List;
import org.dromara.label.dto.LibraryImportDTO;
import org.dromara.label.dto.LibraryLabelDTO;
import org.dromara.label.dto.LibraryProjectComposeDTO;
import org.dromara.label.dto.LibraryQueryDTO;
import org.dromara.label.dto.LibrarySetDTO;
import org.dromara.label.vo.ClassSetVO;
import org.dromara.label.vo.LibraryLabelVO;
import org.dromara.label.vo.LibrarySetVO;

/**
 * 标签库域服务：平台级标签集/标签的维护，以及项目侧引入（复制语义）。
 *
 * <p>三个场景（scene）分面：image=2d图片 / point_cloud=3d点云 / text=文字。
 * 视觉场景的标签字段与 label_class 对齐，文字场景的字段与 label_text_class 对齐。</p>
 *
 * <p>项目引入走<b>复制</b>语义：整集引入（一个库集 → 项目一个标签集合）或
 * 任选组合（跨集勾选若干标签 → 项目一个标签集合）；复制后项目可自由编辑，
 * 不受库后续变更影响。文本引入保留父子层级。</p>
 */
public interface LibraryService {

    /** 标签集列表（按 scene 过滤，含集内标签数）。 */
    List<LibrarySetVO> sets(LibraryQueryDTO dto);

    /** 新建/更新标签集（有 id 为更新），返回最新 VO。 */
    LibrarySetVO saveSet(LibrarySetDTO dto);

    /** 删除标签集（软删，连带集内标签）。 */
    void deleteSet(LibraryQueryDTO dto);

    /** 标签列表（按 setId）。 */
    List<LibraryLabelVO> labels(LibraryQueryDTO dto);

    /** 新建/更新标签（有 id 为更新），返回最新 VO。 */
    LibraryLabelVO saveLabel(LibraryLabelDTO dto);

    /** 删除标签（软删）。 */
    void deleteLabel(LibraryQueryDTO dto);

    /**
     * 项目引入·整集（视觉场景）：库集整体复制为项目下一个标签集。
     *
     * @return 项目侧新建的标签集 VO
     */
    ClassSetVO importSetToProject(LibraryImportDTO dto);

    /**
     * 项目引入·任选组合（视觉场景）：跨集勾选的若干标签复制为项目下一个标签集。
     *
     * @return 项目侧新建的标签集 VO
     */
    ClassSetVO composeToProject(LibraryProjectComposeDTO dto);

    /**
     * 项目引入·整集（文字场景）：库集整体复制为项目下一个文本标签集（保留层级）。
     *
     * @return 项目侧新建的文本标签集 ID
     */
    Long importTextSetToProject(LibraryImportDTO dto);

    /**
     * 项目引入·任选组合（文字场景）：跨集勾选的若干文本标签复制为项目下一个文本标签集
     * （勾选中存在父子关系时层级一并复制；父未勾选时子标签提升为顶层）。
     *
     * @return 项目侧新建的文本标签集 ID
     */
    Long composeTextToProject(LibraryProjectComposeDTO dto);
}
