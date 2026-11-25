package org.dromara.label.common;

import java.awt.image.BufferedImage;
import java.io.InputStream;
import javax.imageio.ImageIO;

/**
 * 图片像素尺寸读取工具。
 * <p>从图片输入流解析宽高（JDK 内置 ImageIO，无需额外依赖）；流由调用方负责关闭。</p>
 */
public final class ImageDimensionUtil {

    private ImageDimensionUtil() {
    }

    /**
     * 读取图片像素宽高；流不可读或非图片返回 null。
     *
     * @param in 图片输入流（调用方负责关闭）
     * @return {width, height}，失败返回 null
     */
    public static int[] read(InputStream in) {
        if (in == null) {
            return null;
        }
        try {
            BufferedImage img = ImageIO.read(in);
            if (img != null) {
                return new int[] { img.getWidth(), img.getHeight() };
            }
        } catch (Exception e) {
            // 非图片 / 损坏文件：忽略，交由调用方按缺省处理
        }
        return null;
    }
}
