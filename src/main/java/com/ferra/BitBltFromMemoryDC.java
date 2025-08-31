package com.ferra;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.platform.win32.*;
import com.sun.jna.platform.win32.WinDef.*;
import com.sun.jna.platform.win32.WinGDI.BITMAPINFO;
import com.sun.jna.platform.win32.WinNT.HANDLE;

import javax.swing.*;
import java.awt.*;
import java.awt.image.*;

public class BitBltFromMemoryDC {

    private static final int SRCCOPY = 0x00CC0020;

    // --- manual adjustments ---
    private static final int OFFSET_X = 8;
    private static final int OFFSET_Y = 31;
    private static final int WIDTH_ADJUST = -16;
    private static final int HEIGHT_ADJUST = -39;
    private static final int SCALE = 1;

    static Memory buffer;
    static int[] pixels;
    static BufferedImage reusableImage;
    static RECT rect = new RECT();

    public static void main(String[] args) throws Exception {
        HWND hwndTarget = User32.INSTANCE.FindWindow(null, "Roblox");
        if (hwndTarget == null) {
            System.err.println("Target window not found!");
            return;
        }

        JFrame frame = new JFrame();
        frame.setUndecorated(true);
        frame.setAlwaysOnTop(true);
        frame.setBackground(new Color(0, 0, 0, 0));
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        OverlayPanel overlayPanel = new OverlayPanel();
        frame.setContentPane(overlayPanel);
        frame.setVisible(true);

        // Make frame click-through
        HWND hwndOverlay = new HWND(Native.getComponentPointer(frame));
        int exStyle = User32.INSTANCE.GetWindowLong(hwndOverlay, WinUser.GWL_EXSTYLE);
        exStyle |= WinUser.WS_EX_LAYERED | WinUser.WS_EX_TRANSPARENT;
        User32.INSTANCE.SetWindowLong(hwndOverlay, WinUser.GWL_EXSTYLE, exStyle);

        // Capture loop
        new Thread(() -> {
            while (true) {
                User32.INSTANCE.GetWindowRect(hwndTarget, rect);

                int width = rect.right - rect.left + WIDTH_ADJUST;
                int height = rect.bottom - rect.top + HEIGHT_ADJUST;

                frame.setSize(width, height);
                frame.setLocation(rect.left + OFFSET_X, rect.top + OFFSET_Y);

                BufferedImage frameImage = captureWindow(hwndTarget, width / SCALE, height / SCALE);
                if (frameImage != null) {
                    BufferedImage processed = postProcess(frameImage);
                    overlayPanel.setFrame(processed);
                }
                try { Thread.sleep(16); } catch (InterruptedException ignored) {}
            }
        }).start();
    }

    private static BufferedImage captureWindow(HWND hwnd, int w, int h) {
        HDC hdcWindow = User32.INSTANCE.GetDC(hwnd);
        if (hdcWindow == null) return null;

        HDC hdcMemDC = GDI32.INSTANCE.CreateCompatibleDC(hdcWindow);
        HBITMAP hBitmap = GDI32.INSTANCE.CreateCompatibleBitmap(hdcWindow, w, h);
        HANDLE old = GDI32.INSTANCE.SelectObject(hdcMemDC, hBitmap);

        GDI32.INSTANCE.BitBlt(hdcMemDC, 0, 0, w, h, hdcWindow, 0, 0, SRCCOPY);

        BITMAPINFO bmi = new BITMAPINFO();
        bmi.bmiHeader.biSize = bmi.bmiHeader.size();
        bmi.bmiHeader.biWidth = w;
        bmi.bmiHeader.biHeight = -h;
        bmi.bmiHeader.biPlanes = 1;
        bmi.bmiHeader.biBitCount = 32;
        bmi.bmiHeader.biCompression = WinGDI.BI_RGB;

        buffer = new Memory(w * h * 4);
        GDI32.INSTANCE.GetDIBits(hdcMemDC, hBitmap, 0, h, buffer, bmi, WinGDI.DIB_RGB_COLORS);

        pixels = buffer.getIntArray(0, w * h);
        DataBufferInt db = new DataBufferInt(pixels, pixels.length);
        DirectColorModel cm = new DirectColorModel(
                32, 0x00FF0000, 0x0000FF00, 0x000000FF, 0xFF000000
        );
        WritableRaster raster = Raster.createPackedRaster(
                db, w, h, w,
                new int[]{0x00FF0000, 0x0000FF00, 0x000000FF, 0xFF000000}, null
        );
        BufferedImage img = new BufferedImage(cm, raster, false, null);

        GDI32.INSTANCE.SelectObject(hdcMemDC, old);
        GDI32.INSTANCE.DeleteObject(hBitmap);
        GDI32.INSTANCE.DeleteDC(hdcMemDC);
        User32.INSTANCE.ReleaseDC(hwnd, hdcWindow);

        return img;
    }

    private static BufferedImage postProcess(BufferedImage src) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), src.getType());
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                int rgba = src.getRGB(x, y);
                int a = (rgba >> 24) & 0xff;
                int r = 255 - ((rgba >> 16) & 0xff);
                int g = 255 - ((rgba >> 8) & 0xff);
                int b = 255 - (rgba & 0xff);
                out.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
        return out;
    }

    static class OverlayPanel extends JPanel {
        private BufferedImage frame;
        public void setFrame(BufferedImage frame) {
            this.frame = frame;
            repaint();
        }
        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (frame != null) {
                // Draw scaled to current panel size
                g.drawImage(frame, 0, 0, getWidth(), getHeight(), null);
            }
        }
    }
}
