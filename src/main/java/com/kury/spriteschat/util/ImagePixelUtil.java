package com.kury.spriteschat.util;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;

/**
 * Tiện ích xử lý ảnh skin player head (cắt 8x8, ghép layer hat và chuyển đổi pixel chat).
 */
public final class ImagePixelUtil {

    private ImagePixelUtil() {}

    /**
     * Cắt phần khuôn mặt 8x8 từ ảnh skin 64x64 (hoặc 64x32) của Minecraft.
     * Tự động ghép lớp cơ bản (Base Layer: x=8, y=8) và lớp mũ ngoài (Hat Layer: x=40, y=8).
     */
    public static BufferedImage extractFace(BufferedImage skinImage) {
        if (skinImage == null) return null;

        BufferedImage face = new BufferedImage(8, 8, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = face.createGraphics();

        // 1. Lớp mặt cơ bản (Base layer): x=8, y=8, w=8, h=8
        int skinWidth = skinImage.getWidth();
        int skinHeight = skinImage.getHeight();

        if (skinWidth >= 16 && skinHeight >= 16) {
            BufferedImage baseHead = skinImage.getSubimage(8, 8, 8, 8);
            g2d.drawImage(baseHead, 0, 0, null);
        }

        // 2. Lớp mũ ngoài (Overlay/Hat layer): x=40, y=8, w=8, h=8
        if (skinWidth >= 48 && skinHeight >= 16) {
            BufferedImage hatLayer = skinImage.getSubimage(40, 8, 8, 8);
            // Kiểm tra xem hat layer có pixel không rỗng không
            boolean hasHat = false;
            for (int x = 0; x < 8; x++) {
                for (int y = 0; y < 8; y++) {
                    int alpha = (hatLayer.getRGB(x, y) >> 24) & 0xFF;
                    if (alpha > 10) {
                        hasHat = true;
                        break;
                    }
                }
                if (hasHat) break;
            }
            if (hasHat) {
                g2d.drawImage(hatLayer, 0, 0, null);
            }
        }

        g2d.dispose();
        return face;
    }

    /**
     * Chuyển đổi 8x8 pixel ảnh mặt thành chuỗi ký tự màu MiniMessage hiển thị trực tiếp trong chat.
     * Sử dụng các ký tự khối màu mini <#RRGGBB>█ để tạo thành icon lá cờ/đầu thu nhỏ.
     */
    public static String faceToInlineChatPixel(BufferedImage face) {
        if (face == null) return "<?> ";

        StringBuilder sb = new StringBuilder();
        // Lấy hàng pixel ở giữa hoặc trung bình để hiển thị 1 dòng ngang gọn gàng (8 block màu)
        // Hàng y = 3 và y = 4 là trung tâm mặt
        int y1 = 3;
        int y2 = 4;

        for (int x = 0; x < 8; x++) {
            int rgb1 = face.getRGB(x, y1);
            int rgb2 = face.getRGB(x, y2);

            int r = (((rgb1 >> 16) & 0xFF) + ((rgb2 >> 16) & 0xFF)) / 2;
            int g = (((rgb1 >> 8) & 0xFF) + ((rgb2 >> 8) & 0xFF)) / 2;
            int b = ((rgb1 & 0xFF) + (rgb2 & 0xFF)) / 2;

            String hex = String.format("%02x%02x%02x", r, g, b);
            sb.append("<#").append(hex).append(">█</#").append(hex).append(">");
        }

        return sb.toString();
    }

    /**
     * Tạo chuỗi hiển thị đầy đủ 8 hàng pixel dạng multiline MiniMessage (dành cho Hover tooltip hoặc xem chi tiết).
     */
    public static String faceToMultilineChatPixel(BufferedImage face) {
        if (face == null) return "";

        StringBuilder sb = new StringBuilder();
        // Ghép cặp 2 hàng một bằng ký tự nửa trên ▀ (\u2580)
        // Hàng trên là màu chữ, hàng dưới là màu nền (hoặc render 8 dòng █)
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 8; x++) {
                int rgb = face.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                String hex = String.format("%02x%02x%02x", r, g, b);
                sb.append("<#").append(hex).append(">█</#").append(hex).append(">");
            }
            if (y < 7) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    /**
     * Đọc ảnh từ mảng byte
     */
    public static BufferedImage fromBytes(byte[] bytes) throws IOException {
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
            return ImageIO.read(in);
        }
    }

    /**
     * Lưu ảnh vào file PNG
     */
    public static void savePng(BufferedImage image, File targetFile) throws IOException {
        File parent = targetFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        ImageIO.write(image, "PNG", targetFile);
    }
}
