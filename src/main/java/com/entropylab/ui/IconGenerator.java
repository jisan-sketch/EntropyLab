package com.entropylab.ui;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Generates high-resolution application icons for EntropyLab.
 */
public class IconGenerator {

    public static BufferedImage createIconImage(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = image.createGraphics();

        // Enable high-quality anti-aliasing
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        // Background squircle gradient (Deep indigo to dark slate)
        GradientPaint bgGradient = new GradientPaint(
                0, 0, new Color(30, 27, 75), // #1e1b4b
                size, size, new Color(79, 70, 229) // #4f46e5
        );
        g2.setPaint(bgGradient);
        int cornerRadius = (int) (size * 0.22);
        g2.fill(new RoundRectangle2D.Double(0, 0, size, size, cornerRadius, cornerRadius));

        // Subtle outer border
        g2.setColor(new Color(129, 140, 248, 120));
        g2.setStroke(new BasicStroke(size * 0.02f));
        g2.draw(new RoundRectangle2D.Double(size * 0.01, size * 0.01, size * 0.98, size * 0.98, cornerRadius, cornerRadius));

        // Center Flask / Chaos Symbol
        float cx = size / 2.0f;
        float cy = size / 2.0f;

        // Draw glowing stylized "E" + Chaos Sine Wave
        // 1. Draw glowing wave pulse in cyan
        g2.setColor(new Color(56, 189, 248, 220)); // #38bdf8
        g2.setStroke(new BasicStroke(size * 0.06f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

        int points = 40;
        int[] xPoints = new int[points];
        int[] yPoints = new int[points];
        float waveWidth = size * 0.65f;
        float startX = cx - waveWidth / 2.0f;

        for (int i = 0; i < points; i++) {
            float progress = (float) i / (points - 1);
            float x = startX + progress * waveWidth;
            // Damped sine wave simulating chaos pulse
            float envelope = (float) Math.sin(progress * Math.PI);
            float y = cy + (float) Math.sin(progress * Math.PI * 3.5) * (size * 0.18f) * envelope;
            xPoints[i] = Math.round(x);
            yPoints[i] = Math.round(y);
        }
        g2.drawPolyline(xPoints, yPoints, points);

        // 2. Bold stylized "E" glyph in clean white
        g2.setColor(new Color(255, 255, 255, 240));
        Font font = new Font(Font.SANS_SERIF, Font.BOLD, (int) (size * 0.42));
        g2.setFont(font);
        FontMetrics fm = g2.getFontMetrics();
        String letter = "E";
        int textWidth = fm.stringWidth(letter);
        int textHeight = fm.getAscent();
        g2.drawString(letter, cx - textWidth / 2.0f, cy + textHeight / 3.0f);

        g2.dispose();
        return image;
    }

    public static void writeIco(File outputFile, BufferedImage... images) throws IOException {
        java.util.List<byte[]> pngBytesList = new java.util.ArrayList<>();
        for (BufferedImage img : images) {
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            ImageIO.write(img, "PNG", baos);
            pngBytesList.add(baos.toByteArray());
        }

        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        java.io.DataOutputStream dos = new java.io.DataOutputStream(out);

        // Header: 2 bytes reserved (0), 2 bytes type (1 for ICO), 2 bytes image count
        writeShortLE(dos, 0);
        writeShortLE(dos, 1);
        writeShortLE(dos, images.length);

        int offset = 6 + (16 * images.length);
        for (int i = 0; i < images.length; i++) {
            BufferedImage img = images[i];
            byte[] png = pngBytesList.get(i);
            int w = img.getWidth() >= 256 ? 0 : img.getWidth();
            int h = img.getHeight() >= 256 ? 0 : img.getHeight();

            dos.writeByte(w); // width
            dos.writeByte(h); // height
            dos.writeByte(0); // color count
            dos.writeByte(0); // reserved
            writeShortLE(dos, 1); // planes
            writeShortLE(dos, 32); // bit count
            writeIntLE(dos, png.length); // size of image data
            writeIntLE(dos, offset); // offset of image data
            offset += png.length;
        }

        for (byte[] png : pngBytesList) {
            dos.write(png);
        }
        dos.flush();

        Files.write(outputFile.toPath(), out.toByteArray());
    }

    private static void writeShortLE(java.io.DataOutputStream dos, int val) throws IOException {
        dos.writeByte(val & 0xFF);
        dos.writeByte((val >> 8) & 0xFF);
    }

    private static void writeIntLE(java.io.DataOutputStream dos, int val) throws IOException {
        dos.writeByte(val & 0xFF);
        dos.writeByte((val >> 8) & 0xFF);
        dos.writeByte((val >> 16) & 0xFF);
        dos.writeByte((val >> 24) & 0xFF);
    }

    public static void generateResources() throws IOException {
        Path resDir = Path.of("src/main/resources");
        if (!Files.exists(resDir)) {
            Files.createDirectories(resDir);
        }

        File icon256 = resDir.resolve("icon.png").toFile();
        BufferedImage img256 = createIconImage(256);
        ImageIO.write(img256, "PNG", icon256);
        System.out.println("[IconGenerator] Generated: " + icon256.getAbsolutePath());

        File icon64 = resDir.resolve("icon-64.png").toFile();
        BufferedImage img64 = createIconImage(64);
        ImageIO.write(img64, "PNG", icon64);
        System.out.println("[IconGenerator] Generated: " + icon64.getAbsolutePath());

        BufferedImage img48 = createIconImage(48);
        BufferedImage img32 = createIconImage(32);
        BufferedImage img16 = createIconImage(16);

        File iconIco = resDir.resolve("icon.ico").toFile();
        writeIco(iconIco, img256, img64, img48, img32, img16);
        System.out.println("[IconGenerator] Generated: " + iconIco.getAbsolutePath());
    }

    public static void main(String[] args) throws Exception {
        generateResources();
    }
}
