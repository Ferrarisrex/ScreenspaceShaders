package com.ferra;

import com.sun.jna.Memory;
import com.sun.jna.platform.win32.*;
import com.sun.jna.platform.win32.WinDef.*;
import com.sun.jna.platform.win32.WinGDI.BITMAPINFO;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.platform.win32.WinUser.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFWNativeWin32;
import org.lwjgl.opengl.GL;

import javax.swing.*;
import java.awt.*;
import java.awt.image.*;
import java.nio.ByteBuffer;

import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_BGRA;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.system.MemoryUtil.NULL;

public class DuplicateTabletScreenUsingBitBltCPUBufferDC_OPENGL {

    // --- App/shaping ---
    private static final String APPLICATION_NAME = "Roblox";
    private static final int OFFSET_X = 8;
    private static final int OFFSET_Y = 31;
    private static final int WIDTH_ADJUST = -16;
    private static final int HEIGHT_ADJUST = -39;
    private static final int SRCCOPY = 0x00CC0020;
    private static final int CROP_WIDTH = 60; // tjese are percents, what percent of screen is used
    private static final int CROP_HEIGHT = 65;
    private static final int CROP_X = 0;    // horizontal control for image
    private static final int CROP_Y = 50;  // vertical control for image

    // --- Capture cycle controls (customize) ---
    private static final double INTERVAL_SECONDS = 3.0;  // how often to run the capture/hotkey sequence
    // scan code for '3' on US layout (use scan codes; i tested 0x04)
    private static final int HOTKEY_SCAN = 0x04;
    private static final int HOTKEY_PRESS_MS = 15;       // how long to hold the key down
    private static final int POST_TOGGLE_WAIT_MS = 10;  // wait after toggling before second capture
    private static final float OVERLAY_ALPHA = 0.5f;     // viewer overlay opacity (viewer only) (removed)

    static RECT rect = new RECT();
    static Memory buffer;

    // GLFW overlay window (transparent & click-through) — we render baseline into overlayTexture when frozen
    static long window;
    static int overlayTexture = 0;
    static volatile boolean overlayActive = false;

    // Simple viewer to show the two layers
    static CaptureViewer viewer = new CaptureViewer();

    public static void main(String[] args) throws Exception {
        HWND hwndTarget = User32.INSTANCE.FindWindow(null, APPLICATION_NAME);
        if (hwndTarget == null) {
            System.err.println("Target window not found!");
            return;
        }

        if (!glfwInit()) throw new IllegalStateException("Unable to init GLFW");
        glfwWindowHint(GLFW_TRANSPARENT_FRAMEBUFFER, GLFW_TRUE);
        glfwWindowHint(GLFW_DECORATED, GLFW_FALSE);
        glfwWindowHint(GLFW_FLOATING, GLFW_TRUE);
        glfwWindowHint(GLFW_RESIZABLE, GLFW_TRUE);

        window = glfwCreateWindow(200, 200, "Overlay", NULL, NULL);
        if (window == NULL) throw new RuntimeException("Failed to create GLFW window");

        // make overlay click-through
        long hwndVal = GLFWNativeWin32.glfwGetWin32Window(window);
        HWND hwndOverlay = new HWND(new com.sun.jna.Pointer(hwndVal));
        int exStyle = User32.INSTANCE.GetWindowLong(hwndOverlay, WinUser.GWL_EXSTYLE);
        exStyle |= WinUser.WS_EX_LAYERED | WinUser.WS_EX_TRANSPARENT;
        User32.INSTANCE.SetWindowLong(hwndOverlay, WinUser.GWL_EXSTYLE, exStyle);

        // GL context
        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        GL.createCapabilities();

        // create overlay texture (will be reallocated each capture to match size)
        overlayTexture = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, overlayTexture);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glBindTexture(GL_TEXTURE_2D, 0);

        // Show the viewer window
        SwingUtilities.invokeLater(() -> viewer.setVisible(true));

        long lastCycleNanos = System.nanoTime();

        // main loop
        while (!glfwWindowShouldClose(window)) {
            // Track Roblox window rect and move/resize overlay to match
            User32.INSTANCE.GetWindowRect(hwndTarget, rect);
            int w = rect.right - rect.left + WIDTH_ADJUST;
            int h = rect.bottom - rect.top + HEIGHT_ADJUST;

            if (w > 0 && h > 0) {
                glfwSetWindowPos(window, rect.left + OFFSET_X, rect.top + OFFSET_Y);
                glfwSetWindowSize(window, w, h);
            }

            // Render overlay if active (otherwise keep transparent)
            glViewport(0, 0, Math.max(w, 1), Math.max(h, 1));
            glMatrixMode(GL_PROJECTION);
            glLoadIdentity();
            glOrtho(0, Math.max(w, 1), Math.max(h, 1), 0, -1, 1);
            glMatrixMode(GL_MODELVIEW);
            glLoadIdentity();

            glClearColor(0, 0, 0, 0);
            glClear(GL_COLOR_BUFFER_BIT);

            if (overlayActive && w > 0 && h > 0) {
                // draw overlayTexture fullscreen (top-left origin)
                glEnable(GL_TEXTURE_2D);
                glBindTexture(GL_TEXTURE_2D, overlayTexture);

                glColor4f(1f, 1f, 1f, 1f);
                glBegin(GL_QUADS);
                glTexCoord2f(0f, 0f); glVertex2f(0f, 0f);
                glTexCoord2f(1f, 0f); glVertex2f(w, 0f);
                glTexCoord2f(1f, 1f); glVertex2f(w, h);
                glTexCoord2f(0f, 1f); glVertex2f(0f, h);
                glEnd();

                glBindTexture(GL_TEXTURE_2D, 0);
                glDisable(GL_TEXTURE_2D);
            }

            glfwSwapBuffers(window);
            glfwPollEvents();

            // Every INTERVAL_SECONDS, run the sequence:
            double elapsed = (System.nanoTime() - lastCycleNanos) / 1_000_000_000.0;
            if (elapsed >= INTERVAL_SECONDS && w > 0 && h > 0) {
                lastCycleNanos = System.nanoTime();
                runHotkeyCaptureCycle(hwndTarget, w, h);
            }
        }

        // cleanup
        glDeleteTextures(overlayTexture);
        glfwDestroyWindow(window);
        glfwTerminate();
    }

    /**
     * Sequence:
     * 1) capture baseline into a GL texture and enable overlay (freezes appearance)
     * 2) press hotkey (down/up) to toggle in-game
     * 3) wait a little
     * 4) capture after-image for the viewer
     * 5) press hotkey again to restore
     * 6) disable overlay
     */
    private static void runHotkeyCaptureCycle(HWND hwndTarget, int w, int h) {
        try {
            // bring target front (best effort)
            User32.INSTANCE.SetForegroundWindow(hwndTarget);

            // 1) baseline capture (ByteBuffer for GL + BufferedImage for viewer)
            ByteBuffer baselineBuf = captureWindowByteBuffer(hwndTarget, w, h);
            BufferedImage baselineImg = bufferedFromBGRA(baselineBuf, w, h); // for viewer

            // upload baseline into overlay texture and enable overlay (makes overlay show immediately)
            glBindTexture(GL_TEXTURE_2D, overlayTexture);
            glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
            // reallocate texture at correct size
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, w, h, 0, GL_BGRA, GL_UNSIGNED_BYTE, baselineBuf);
            glBindTexture(GL_TEXTURE_2D, 0);

            overlayActive = true;

            // force one immediate draw so user sees the frozen baseline before we press keys
            renderOverlayNow(w, h);

            // 2) press hotkey (toggle on)
            pressKeyOnce(HOTKEY_SCAN);
            Thread.sleep(POST_TOGGLE_WAIT_MS);

            // 3) after-image capture (what the game displays when key pressed)
            BufferedImage afterImg = captureWindowBuffered(hwndTarget, w, h);

            // 4) press hotkey again to restore in-game state
            pressKeyOnce(HOTKEY_SCAN);

            // 5) hide overlay (unfreeze)
            overlayActive = false;
            renderOverlayNow(w, h); // redraw to remove overlay immediately

            // show viewer (baseline vs after)
            viewer.setImages(baselineImg, afterImg, OVERLAY_ALPHA);

        } catch (Throwable t) {
            t.printStackTrace();
            // ensure overlay not stuck on error
            overlayActive = false;
        }
    }

    // helper: draw overlay once and swap buffers (so overlay appears while blocking)
    private static void renderOverlayNow(int w, int h) {
        // ensure GL context still current (we're in main thread usually)
        glViewport(0, 0, Math.max(w, 1), Math.max(h, 1));
        glMatrixMode(GL_PROJECTION);
        glLoadIdentity();
        glOrtho(0, Math.max(w, 1), Math.max(h, 1), 0, -1, 1);
        glMatrixMode(GL_MODELVIEW);
        glLoadIdentity();

        glClearColor(0, 0, 0, 0);
        glClear(GL_COLOR_BUFFER_BIT);

        if (overlayActive && w > 0 && h > 0) {
            glEnable(GL_TEXTURE_2D);
            glBindTexture(GL_TEXTURE_2D, overlayTexture);
            glBegin(GL_QUADS);
            glTexCoord2f(0f,0f); glVertex2f(0,0);
            glTexCoord2f(1f,0f); glVertex2f(w,0);
            glTexCoord2f(1f,1f); glVertex2f(w,h);
            glTexCoord2f(0f,1f); glVertex2f(0,h);
            glEnd();
            glBindTexture(GL_TEXTURE_2D, 0);
            glDisable(GL_TEXTURE_2D);
        }

        glfwSwapBuffers(window);
        glfwPollEvents();
    }

    // --- Capture helpers ---

    /** Captures into a direct ByteBuffer (BGRA top-down) via BitBlt + GetDIBits. */
    private static ByteBuffer captureWindowByteBuffer(HWND hwnd, int w, int h) {
        HDC hdcWindow = User32.INSTANCE.GetDC(hwnd);
        if (hdcWindow == null) return null;

        HDC hdcMemDC = GDI32.INSTANCE.CreateCompatibleDC(hdcWindow);
        HBITMAP hBitmap = GDI32.INSTANCE.CreateCompatibleBitmap(hdcWindow, w, h);
        HANDLE old = GDI32.INSTANCE.SelectObject(hdcMemDC, hBitmap);

        GDI32.INSTANCE.BitBlt(hdcMemDC, 0, 0, w, h, hdcWindow, 0, 0, SRCCOPY);

        BITMAPINFO bmi = new BITMAPINFO();
        bmi.bmiHeader.biSize = bmi.bmiHeader.size();
        bmi.bmiHeader.biWidth = w;
        bmi.bmiHeader.biHeight = -h; // top-down
        bmi.bmiHeader.biPlanes = 1;
        bmi.bmiHeader.biBitCount = 32;
        bmi.bmiHeader.biCompression = WinGDI.BI_RGB;

        buffer = new Memory((long) w * h * 4);
        GDI32.INSTANCE.GetDIBits(hdcMemDC, hBitmap, 0, h, buffer, bmi, WinGDI.DIB_RGB_COLORS);

        ByteBuffer byteBuffer = BufferUtils.createByteBuffer(w * h * 4);
        byteBuffer.put(buffer.getByteArray(0, w * h * 4));
        byteBuffer.flip();

        GDI32.INSTANCE.SelectObject(hdcMemDC, old);
        GDI32.INSTANCE.DeleteObject(hBitmap);
        GDI32.INSTANCE.DeleteDC(hdcMemDC);
        User32.INSTANCE.ReleaseDC(hwnd, hdcWindow);

        return byteBuffer;
    }

    /** Captures into a BufferedImage (for viewer) */
    private static BufferedImage captureWindowBuffered(HWND hwnd, int w, int h) {
        ByteBuffer bb = captureWindowByteBuffer(hwnd, w, h);
        if (bb == null) return null;
        return bufferedFromBGRA(bb, w, h);
    }

    /** Convert BGRA top-down ByteBuffer into a BufferedImage (same mask approach you used earlier) */
    private static BufferedImage bufferedFromBGRA(ByteBuffer bb, int w, int h) {
        int[] pixels = new int[w * h];
        bb.asIntBuffer().get(pixels); // interpret bytes as little-endian ints
        DataBufferInt db = new DataBufferInt(pixels, pixels.length);
        DirectColorModel cm = new DirectColorModel(32,
                0x00FF0000, // R
                0x0000FF00, // G
                0x000000FF, // B
                0xFF000000  // A
        );
        WritableRaster raster = Raster.createPackedRaster(db, w, h, w,
                new int[]{0x00FF0000, 0x0000FF00, 0x000000FF, 0xFF000000}, null);
        return new BufferedImage(cm, raster, false, null);
    }

    // --- Input: press a SCANCODE once ---
    private static void pressKeyOnce(int scanCode) {
        INPUT input = new INPUT();
        input.type = new DWORD(INPUT.INPUT_KEYBOARD);
        input.input.setType("ki");

        // key down (scancode)
        KEYBDINPUT kiDown = new KEYBDINPUT();
        kiDown.wVk = new WORD(0);
        kiDown.wScan = new WORD(scanCode); //0x04 for KEY.3
        kiDown.dwFlags = new DWORD(0x0008); // KEYEVENTF_SCANCODE
        kiDown.time = new DWORD(0);
        kiDown.dwExtraInfo = new BaseTSD.ULONG_PTR(0);
        input.input.ki = kiDown;
        input.write();
        User32.INSTANCE.SendInput(new DWORD(1), (INPUT[]) input.toArray(1), input.size());

        try { Thread.sleep(HOTKEY_PRESS_MS); } catch (InterruptedException ignored) {}

        // key up
        KEYBDINPUT kiUp = new KEYBDINPUT();
        kiUp.wVk = new WORD(0);
        kiUp.wScan = new WORD(scanCode);
        kiUp.dwFlags = new DWORD(0x0008 | 0x0002); // SCANCODE | KEYUP
        kiUp.time = new DWORD(0);
        kiUp.dwExtraInfo = new BaseTSD.ULONG_PTR(0);
        input.input.ki = kiUp;
        input.write();
        User32.INSTANCE.SendInput(new DWORD(1), (INPUT[]) input.toArray(1), input.size());
    }

    // --- Simple Swing viewer that draws baseline + overlay with alpha ---
    static class CaptureViewer extends JFrame {
        private volatile BufferedImage base, over;
        private volatile float alpha = 0.5f;

        private final JPanel panel = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                if (base != null) {
                    //g2.drawImage(base, 0, 0, getWidth(), getHeight(), null);
                }
                if (over != null) {
                    int panelW = getWidth();
                    int panelH = getHeight();

                    // Calculate cropped dimensions in panel coordinates
                    int cropW = (int) (panelW * (CROP_WIDTH / 100.0));
                    int cropH = (int) (panelH * (CROP_HEIGHT / 100.0));

                    // Centered crop region on the panel
                    int x = (panelW - cropW) / 2;
                    int y = (panelH - cropH) / 2;

                    // Source region of the image (same percentage from the image itself)
                    int srcW = (int) (over.getWidth() * (CROP_WIDTH / 100.0));
                    int srcH = (int) (over.getHeight() * (CROP_HEIGHT / 100.0));
                    int srcX = (over.getWidth() - srcW) / 2;
                    int srcY = (over.getHeight() - srcH) / 2;
                    
                    g2.drawImage(over,
                            x, y, x + cropW, y + cropH,   // destination rectangle
                            srcX + CROP_X, srcY + CROP_Y, srcX + srcW, srcY + srcH,   // source rectangle
                            null);

                    
                    //g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
                    g2.setColor(Color.WHITE);
                    
                    g2.drawString(APPLICATION_NAME + " " + window, srcX, srcY);
                }

                g2.dispose();
            }
        };

        CaptureViewer() {
            super("Tibet Display");
            setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            setSize(960, 540);
            setLocationByPlatform(true);
            add(panel);
        }

        void setImages(BufferedImage baseline, BufferedImage overlay, float a) {
            this.base = baseline;
            this.over = overlay;
            this.alpha = a;
            panel.repaint();
        }
    }
}
