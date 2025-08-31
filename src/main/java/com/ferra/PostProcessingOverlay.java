package com.ferra;

import com.sun.jna.Native;
import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.*;
import com.sun.jna.platform.win32.WinDef.*;
import com.sun.jna.platform.win32.WinGDI.*;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.platform.win32.WinUser.*;
import com.sun.jna.ptr.PointerByReference;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class PostProcessingOverlay {

    public static void main(String[] args) throws Exception {
        HWND hwndTarget = User32.INSTANCE.FindWindow(null, "Task Manager");
        if (hwndTarget == null) {
            System.err.println("Target window not found!");
            return;
        }

        JFrame frame = new JFrame();
        frame.setUndecorated(true);
        frame.setAlwaysOnTop(true);
        frame.setBackground(new Color(0, 0, 0, 0));
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setVisible(true);

        OverlayPanel overlayPanel = new OverlayPanel();
        frame.setContentPane(overlayPanel);

        HWND hwndOverlay = new HWND(Native.getComponentPointer(frame));
        int exStyle = User32.INSTANCE.GetWindowLong(hwndOverlay, WinUser.GWL_EXSTYLE);
        exStyle |= WinUser.WS_EX_LAYERED | WinUser.WS_EX_TRANSPARENT;
        User32.INSTANCE.SetWindowLong(hwndOverlay, WinUser.GWL_EXSTYLE, exStyle);

        RECT rect = new RECT();
        User32.INSTANCE.GetWindowRect(hwndTarget, rect);
        int width = rect.right - rect.left;
        int height = rect.bottom - rect.top;

        frame.setSize(width, height);
        frame.setLocation(rect.left, rect.top);
        

        new Thread(() -> {
            while (true) {
                BufferedImage frameImage = captureWindow(hwndTarget, width, height);
                if (frameImage != null) {
                    BufferedImage processed = invert(frameImage);
                    overlayPanel.setFrame(processed);
                }
                try {
                    Thread.sleep(16);
                } catch (InterruptedException ignored) {}
            }
        }).start();
    }

    private static BufferedImage captureWindow(HWND hwnd, int width, int height) {
    HDC hdcWindow = User32.INSTANCE.GetDC(hwnd);
    HDC hdcMemDC = GDI32.INSTANCE.CreateCompatibleDC(hdcWindow);

    BITMAPINFO bmi = new BITMAPINFO();
    bmi.bmiHeader.biWidth = width;
    bmi.bmiHeader.biHeight = -height; // top-down
    bmi.bmiHeader.biPlanes = 1;
    bmi.bmiHeader.biBitCount = 32;
    bmi.bmiHeader.biCompression = WinGDI.BI_RGB;

    PointerByReference ppvBits = new PointerByReference();
    HBITMAP hBitmap = GDI32.INSTANCE.CreateDIBSection(hdcMemDC, bmi, WinGDI.DIB_RGB_COLORS, ppvBits, null, 0);
    HANDLE hOld = GDI32.INSTANCE.SelectObject(hdcMemDC, hBitmap);

    User32.INSTANCE.PrintWindow(hwnd, hdcMemDC, 0);

    // Read pixels
    int[] pixels = ppvBits.getValue().getIntArray(0, width * height);

    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    image.setRGB(0, 0, width, height, pixels, 0, width);

    GDI32.INSTANCE.SelectObject(hdcMemDC, hOld);
    GDI32.INSTANCE.DeleteObject(hBitmap);
    GDI32.INSTANCE.DeleteDC(hdcMemDC);
    User32.INSTANCE.ReleaseDC(hwnd, hdcWindow);

    return image;
}



    private static BufferedImage invert(BufferedImage src) {
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
        return src;
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
            if (frame != null) g.drawImage(frame, 0, 0, null);
        }
    }
}
