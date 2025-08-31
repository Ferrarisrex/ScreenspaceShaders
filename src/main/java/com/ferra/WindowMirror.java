package com.ferra;

import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.platform.win32.*;
import com.sun.jna.platform.win32.WinDef.*;
import com.sun.jna.platform.win32.WinGDI.BITMAPINFO;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.platform.win32.WinUser.INPUT;
import com.sun.jna.platform.win32.WinUser.MOUSEINPUT;
import com.sun.jna.platform.win32.WinUser.KEYBDINPUT;



import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.*;

public class WindowMirror {
    // --- Mouse/keyboard flags (define explicitly; not all JNA versions expose them) ---
    private static final int MOUSEEVENTF_MOVE       = 0x0001;
    private static final int MOUSEEVENTF_LEFTDOWN   = 0x0002;
    private static final int MOUSEEVENTF_LEFTUP     = 0x0004;
    private static final int MOUSEEVENTF_RIGHTDOWN  = 0x0008;
    private static final int MOUSEEVENTF_RIGHTUP    = 0x0010;
    private static final int MOUSEEVENTF_MIDDLEDOWN = 0x0020;
    private static final int MOUSEEVENTF_MIDDLEUP   = 0x0040;
    private static final int MOUSEEVENTF_WHEEL      = 0x0800;
    private static final int MOUSEEVENTF_ABSOLUTE   = 0x8000;
    private static final int SRCCOPY = 0x00CC0020;


    private static final int KEYEVENTF_KEYUP        = 0x0002;

    private final HWND hwnd;
    private final JFrame frame;
    private final JLabel view;      // shows the mirrored image
    private Rectangle winRect;      // current target window rect (screen coords)

    // capture scratch (re-used to reduce GC)
    private HDC hdcWindow;
    private HDC hdcMemDC;

    public WindowMirror(HWND hwnd) {
        this.hwnd  = hwnd;
        this.frame = new JFrame("Mirror: " + getTitle(hwnd));
        this.view  = new JLabel();

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new BorderLayout());
        frame.add(view, BorderLayout.CENTER);

        // Input listeners on the image label
        InputAdapter inputAdapter = new InputAdapter();
        view.addMouseListener(inputAdapter);
        view.addMouseMotionListener(inputAdapter);
        view.addMouseWheelListener(inputAdapter);
        frame.addKeyListener(inputAdapter);          // keyboard on the frame
        frame.setFocusable(true);
        frame.setSize(960, 600);
        frame.setLocationByPlatform(true);
        frame.setVisible(true);

        // capture loop
        new Thread(this::captureLoop, "CaptureLoop").start();
    }

    // --- Capture loop using BitBlt + GetDIBits (32-bit top-down BGRA) ---
    private void captureLoop() {
        while (frame.isVisible()) {
            try {
                winRect = getWindowRect(hwnd);
                if (winRect.width > 0 && winRect.height > 0) {
                    BufferedImage img = captureWindow(hwnd, winRect.width, winRect.height);
                    if (img != null) {
                        // scale to fit label size, preserving aspect
                        Image scaled = img.getScaledInstance(view.getWidth() > 0 ? view.getWidth() : img.getWidth(),
                                                             view.getHeight() > 0 ? view.getHeight() : img.getHeight(),
                                                             Image.SCALE_FAST);
                        SwingUtilities.invokeLater(() -> view.setIcon(new ImageIcon(scaled)));
                    }
                }
                Thread.sleep(12); // ~80 fps target (will be bounded by BitBlt speed)
            } catch (Throwable t) {
                t.printStackTrace();
                try { Thread.sleep(33); } catch (InterruptedException ignored) {}
            }
        }
        cleanupDCs();
    }

    private static String getTitle(HWND h) {
        char[] buf = new char[512];
        User32.INSTANCE.GetWindowText(h, buf, buf.length);
        return Native.toString(buf);
    }

    private static Rectangle getWindowRect(HWND h) {
        WinDef.RECT r = new WinDef.RECT();
        User32.INSTANCE.GetWindowRect(h, r);
        return new Rectangle(r.left, r.top, r.right - r.left, r.bottom - r.top);
    }

    // Single frame capture
    private BufferedImage captureWindow(HWND h, int w, int hgt) {
        // (Re)acquire DCs every frame is fine; for max perf you can cache them, but it’s trickier when the window resizes.
        hdcWindow = User32.INSTANCE.GetDC(h);
        if (hdcWindow == null) return null;
        hdcMemDC  = GDI32.INSTANCE.CreateCompatibleDC(hdcWindow);

        HBITMAP hBitmap = GDI32.INSTANCE.CreateCompatibleBitmap(hdcWindow, w, hgt);
        HANDLE old = GDI32.INSTANCE.SelectObject(hdcMemDC, hBitmap);

        // Copy from window to memory DC
        GDI32.INSTANCE.BitBlt(hdcMemDC, 0, 0, w, hgt, hdcWindow, 0, 0, SRCCOPY);

        // Prepare BITMAPINFO for 32-bit top-down
        BITMAPINFO bmi = new BITMAPINFO();
        bmi.bmiHeader.biSize = bmi.bmiHeader.size();
        bmi.bmiHeader.biWidth = w;
        bmi.bmiHeader.biHeight = -hgt; // negative = top-down
        bmi.bmiHeader.biPlanes = 1;
        bmi.bmiHeader.biBitCount = 32;
        bmi.bmiHeader.biCompression = WinGDI.BI_RGB;

        int bufSize = w * hgt * 4;
        Memory buffer = new Memory(bufSize);

        // Read pixels
        GDI32.INSTANCE.GetDIBits(hdcWindow, hBitmap, 0, hgt, buffer, bmi, WinGDI.DIB_RGB_COLORS);

        int[] pixels = buffer.getIntArray(0, w * hgt);
        DataBufferInt db = new DataBufferInt(pixels, pixels.length);
        DirectColorModel cm = new DirectColorModel(32, 0x00FF0000, 0x0000FF00, 0x000000FF, 0xFF000000);
        WritableRaster raster = Raster.createPackedRaster(db, w, hgt, w,
                new int[]{0x00FF0000, 0x0000FF00, 0x000000FF, 0xFF000000}, null);
        BufferedImage img = new BufferedImage(cm, raster, false, null);

        // Cleanup GDI objects for this frame
        GDI32.INSTANCE.SelectObject(hdcMemDC, old);
        GDI32.INSTANCE.DeleteObject(hBitmap);
        GDI32.INSTANCE.DeleteDC(hdcMemDC);
        User32.INSTANCE.ReleaseDC(h, hdcWindow);
        hdcMemDC = null;
        hdcWindow = null;

        return img;
    }

    private void cleanupDCs() {
        if (hdcMemDC != null) {
            GDI32.INSTANCE.DeleteDC(hdcMemDC);
            hdcMemDC = null;
        }
        if (hdcWindow != null) {
            User32.INSTANCE.ReleaseDC(hwnd, hdcWindow);
            hdcWindow = null;
        }
    }

    // --- Input forwarding (SendInput + SetCursorPos) ---
    private void focusTarget() {
        // Bring window to foreground (best effort)
        User32.INSTANCE.SetForegroundWindow(hwnd);
    }

    private void setCursorScreenPos(int sx, int sy) {
        User32.INSTANCE.SetCursorPos(sx, sy);
    }

    private void sendMouseButton(boolean left, boolean down) {
        INPUT input = new INPUT();
        input.type = new DWORD(WinUser.INPUT.INPUT_MOUSE);
        input.input.setType("mi");

        MOUSEINPUT mi = new MOUSEINPUT();
        mi.dx = new LONG(0);
        mi.dy = new LONG(0);
        mi.mouseData = new DWORD(0);
        mi.dwFlags = new DWORD(left ? (down ? MOUSEEVENTF_LEFTDOWN : MOUSEEVENTF_LEFTUP)
                                    : (down ? MOUSEEVENTF_RIGHTDOWN : MOUSEEVENTF_RIGHTUP));
        mi.time = new DWORD(0);
        mi.dwExtraInfo = new BaseTSD.ULONG_PTR(0);

        input.input.mi = mi;
        input.write();
        User32.INSTANCE.SendInput(new DWORD(1), (INPUT[]) input.toArray(1), input.size());
    }

    private void sendMouseMoveAbsolute(int sx, int sy) {
        // Optional alternative to SetCursorPos: absolute SendInput (0..65535 over virtual screen)
        // Here we simply use SetCursorPos for simplicity/stability.
    }

    private void sendKeyVK(int vk, boolean down) {
        INPUT input = new INPUT();
        input.type = new DWORD(WinUser.INPUT.INPUT_KEYBOARD);
        input.input.setType("ki");

        KEYBDINPUT ki = new KEYBDINPUT();
        ki.wVk   = new WORD(vk);
        ki.wScan = new WORD(0);
        ki.dwFlags = new DWORD(down ? 0 : KEYEVENTF_KEYUP);
        ki.time = new DWORD(0);
        ki.dwExtraInfo = new BaseTSD.ULONG_PTR(0);

        input.input.ki = ki;
        input.write();
        User32.INSTANCE.SendInput(new DWORD(1), (INPUT[]) input.toArray(1), input.size());
    }

    // Map Java AWT keyCode -> Windows VK for common keys
    private static int mapAWTKeyToWinVK(int awt) {
        // letters/digits are identical (A=65..Z=90, 0..9)
        if ((awt >= KeyEvent.VK_A && awt <= KeyEvent.VK_Z) ||
            (awt >= KeyEvent.VK_0 && awt <= KeyEvent.VK_9)) {
            return awt;
        }
        switch (awt) {
            case KeyEvent.VK_SPACE:   return 0x20; // VK_SPACE
            case KeyEvent.VK_ESCAPE:  return 0x1B; // VK_ESCAPE
            case KeyEvent.VK_ENTER:   return 0x0D; // VK_RETURN
            case KeyEvent.VK_TAB:     return 0x09; // VK_TAB
            case KeyEvent.VK_BACK_SPACE: return 0x08; // VK_BACK
            case KeyEvent.VK_LEFT:    return 0x25; // VK_LEFT
            case KeyEvent.VK_UP:      return 0x26; // VK_UP
            case KeyEvent.VK_RIGHT:   return 0x27; // VK_RIGHT
            case KeyEvent.VK_DOWN:    return 0x28; // VK_DOWN
            case KeyEvent.VK_SHIFT:   return 0x10; // VK_SHIFT
            case KeyEvent.VK_CONTROL: return 0x11; // VK_CONTROL
            case KeyEvent.VK_ALT:     return 0x12; // VK_MENU
            case KeyEvent.VK_DELETE:  return 0x2E; // VK_DELETE
            case KeyEvent.VK_HOME:    return 0x24; // VK_HOME
            case KeyEvent.VK_END:     return 0x23; // VK_END
            case KeyEvent.VK_PAGE_UP:   return 0x21; // VK_PRIOR
            case KeyEvent.VK_PAGE_DOWN: return 0x22; // VK_NEXT
            case KeyEvent.VK_F1: return 0x70; case KeyEvent.VK_F2: return 0x71;
            case KeyEvent.VK_F3: return 0x72; case KeyEvent.VK_F4: return 0x73;
            case KeyEvent.VK_F5: return 0x74; case KeyEvent.VK_F6: return 0x75;
            case KeyEvent.VK_F7: return 0x76; case KeyEvent.VK_F8: return 0x77;
            case KeyEvent.VK_F9: return 0x78; case KeyEvent.VK_F10:return 0x79;
            case KeyEvent.VK_F11:return 0x7A; case KeyEvent.VK_F12:return 0x7B;
            default:
                return awt & 0xFF; // fall back (often fine)
        }
    }

    // --- Input adapter that maps mirror coords -> screen coords and sends events ---
    private class InputAdapter extends MouseAdapter implements MouseMotionListener, MouseWheelListener, KeyListener {
        private boolean dragging = false;

        // Convert a mouse event in the JLabel to SCREEN coords inside the target window
        private Point toScreen(MouseEvent e) {
            if (winRect == null || winRect.width == 0 || winRect.height == 0) return null;

            int vw = Math.max(1, view.getWidth());
            int vh = Math.max(1, view.getHeight());

            // Map from scaled preview to window client pixels
            int wx = (int)Math.round((e.getX() / (double)vw) * winRect.width);
            int wy = (int)Math.round((e.getY() / (double)vh) * winRect.height);

            // Convert to screen
            int sx = winRect.x + wx;
            int sy = winRect.y + wy;
            return new Point(sx, sy);
        }

        @Override public void mousePressed(MouseEvent e) {
            Point p = toScreen(e);
            if (p == null) return;
            focusTarget();
            setCursorScreenPos(p.x, p.y);
            if (SwingUtilities.isLeftMouseButton(e)) {
                sendMouseButton(true, true);
                dragging = true;
            } else if (SwingUtilities.isRightMouseButton(e)) {
                sendMouseButton(false, true);
            }
        }

        @Override public void mouseReleased(MouseEvent e) {
            Point p = toScreen(e);
            if (p == null) return;
            focusTarget();
            setCursorScreenPos(p.x, p.y);
            if (SwingUtilities.isLeftMouseButton(e)) {
                sendMouseButton(true, false);
                dragging = false;
            } else if (SwingUtilities.isRightMouseButton(e)) {
                sendMouseButton(false, false);
            }
        }

        @Override public void mouseDragged(MouseEvent e) {
            if (!dragging) return;
            Point p = toScreen(e);
            if (p == null) return;
            focusTarget();
            setCursorScreenPos(p.x, p.y);
            // While dragging, Windows considers it held if we don't release; no extra SendInput needed for move.
        }

        @Override public void mouseMoved(MouseEvent e) {
            // Optional: hover tracking; to simulate movement, we could SendInput with MOVE,
            // but SetCursorPos + upcoming click is usually enough. Skip to avoid cursor jitter.
        }

        @Override public void mouseWheelMoved(MouseWheelEvent e) {
            // (Optional) Implement scrolling with MOUSEEVENTF_WHEEL if needed.
            // int delta = -e.getWheelRotation() * 120;
            // ...
        }

        @Override public void keyPressed(KeyEvent e) {
            focusTarget();
            int vk = mapAWTKeyToWinVK(e.getKeyCode());
            sendKeyVK(vk, true);
        }

        @Override public void keyReleased(KeyEvent e) {
            focusTarget();
            int vk = mapAWTKeyToWinVK(e.getKeyCode());
            sendKeyVK(vk, false);
        }

        @Override public void keyTyped(KeyEvent e) { /* not used */ }
    }

    // --- Window chooser (by title) ---
    private static HWND chooseWindowByExactTitle() {
        String title = JOptionPane.showInputDialog(null, "Enter exact window title:", "Pick Window", JOptionPane.QUESTION_MESSAGE);
        if (title == null || title.trim().isEmpty()) return null;

        final HWND[] result = new HWND[1];
        User32.INSTANCE.EnumWindows((h, data) -> {
            if (User32.INSTANCE.IsWindowVisible(h)) {
                char[] buf = new char[512];
                User32.INSTANCE.GetWindowText(h, buf, buf.length);
                if (title.equals(Native.toString(buf))) {
                    result[0] = h;
                    return false;
                }
            }
            return true;
        }, null);
        return result[0];
    }

    public static void main(String[] args) {
        HWND h = chooseWindowByExactTitle();
        if (h == null) {
            System.out.println("Window not found.");
            return;
        }
        new WindowMirror(h);
    }
}
