package org.dromara.label.ocr.support;

import org.dromara.label.ocr.OcrProperties;
import org.dromara.label.ocr.dto.OcrGeometryDTO;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * 图片裁剪工具：按 bbox / polygon（原图像素坐标）裁剪，多边形可选「框外涂白」。
 */
public final class ImageCropUtil {

    private ImageCropUtil() {
    }

    public static BufferedImage crop(BufferedImage src, OcrGeometryDTO g, OcrProperties props) {
        if ("POLYGON".equalsIgnoreCase(g.getKind()) && g.getPoints() != null && g.getPoints().size() >= 3) {
            return cropPolygon(src, g.getPoints(), props);
        }
        return cropBbox(src, g, props);
    }

    private static BufferedImage cropBbox(BufferedImage src, OcrGeometryDTO g, OcrProperties props) {
        int sw = src.getWidth();
        int sh = src.getHeight();
        int pad = (int) (props.getPaddingRatio() * Math.max(orZero(g.getWidth()), orZero(g.getHeight())));
        int x = clamp(orZero(g.getX()) - pad, 0, sw);
        int y = clamp(orZero(g.getY()) - pad, 0, sh);
        int x2 = clamp(orZero(g.getX()) + orZero(g.getWidth()) + pad, 0, sw);
        int y2 = clamp(orZero(g.getY()) + orZero(g.getHeight()) + pad, 0, sh);
        int w = Math.max(1, x2 - x);
        int h = Math.max(1, y2 - y);
        return src.getSubimage(x, y, w, h);
    }

    private static BufferedImage cropPolygon(BufferedImage src, List<List<Integer>> pts, OcrProperties props) {
        int sw = src.getWidth();
        int sh = src.getHeight();
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        for (List<Integer> p : pts) {
            int px = p.get(0);
            int py = p.get(1);
            minX = Math.min(minX, px);
            minY = Math.min(minY, py);
            maxX = Math.max(maxX, px);
            maxY = Math.max(maxY, py);
        }
        int pad = (int) (props.getPaddingRatio() * Math.max(maxX - minX, maxY - minY));
        int x = clamp(minX - pad, 0, sw);
        int y = clamp(minY - pad, 0, sh);
        int x2 = clamp(maxX + pad, 0, sw);
        int y2 = clamp(maxY + pad, 0, sh);
        int w = Math.max(1, x2 - x);
        int h = Math.max(1, y2 - y);
        BufferedImage crop = src.getSubimage(x, y, w, h);

        if (!props.isMaskOutsidePolygon()) {
            return crop;
        }
        // 框外涂白：白底 + 裁切图按多边形裁剪绘制，框外保持白色
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = out.createGraphics();
        g2.setColor(Color.WHITE);
        g2.fillRect(0, 0, w, h);
        Polygon poly = new Polygon();
        for (List<Integer> p : pts) {
            poly.addPoint(p.get(0) - x, p.get(1) - y);
        }
        g2.setClip(poly);
        g2.drawImage(crop, 0, 0, null);
        g2.dispose();
        return out;
    }

    private static int orZero(Integer v) {
        return v == null ? 0 : v;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
