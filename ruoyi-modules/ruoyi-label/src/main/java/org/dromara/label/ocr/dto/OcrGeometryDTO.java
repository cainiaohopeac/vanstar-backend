package org.dromara.label.ocr.dto;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 裁剪几何（原图像素坐标）。
 * <ul>
 *   <li>BBOX：kind=BBOX, x/y/width/height</li>
 *   <li>POLYGON：kind=POLYGON, points=[[x,y],...]（左上起顺序）</li>
 * </ul>
 */
@Data
public class OcrGeometryDTO implements Serializable {

    private String kind;
    private Integer x;
    private Integer y;
    private Integer width;
    private Integer height;
    private List<List<Integer>> points;
}
