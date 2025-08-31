package com.ferra;

import com.sun.jna.Memory;
import com.sun.jna.platform.win32.*;
import com.sun.jna.platform.win32.WinDef.*;
import com.sun.jna.platform.win32.WinGDI.BITMAPINFO;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import org.lwjgl.BufferUtils;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWNativeWin32;
import org.lwjgl.opengl.GL;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL12.GL_BGRA;
import static org.lwjgl.opengl.GL12.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL13.*;
import static org.lwjgl.opengl.GL20.*;
import static org.lwjgl.opengl.GL30.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.system.MemoryUtil.NULL;

public class BitBltFromMemoryDC_OPENGL {

    private static final String APPLICATION_NAME = "Command Prompt";
    private static final String FRAGMENT_SHADER_FILE = "invert.glsl";
    private static final String VERTEX_SHADER_FILE = "vertex.glsl";
    
    private static final int OFFSET_X = 8;
    private static final int OFFSET_Y = 31;
    private static final int WIDTH_ADJUST = -16;
    private static final int HEIGHT_ADJUST = -39;

    private static final int SRCCOPY = 0x00CC0020;

    static RECT rect = new RECT();
    static Memory buffer;

    static int shaderProgram;
    static int fbo;
    static int texCurrent, texPrevious;
    static int uniformCurrent, uniformPrevious, uniformThreshold;
    static long startTime = System.nanoTime();

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

        long window = glfwCreateWindow(200, 200, "Overlay", NULL, NULL);
        if (window == NULL) throw new RuntimeException("Failed to create GLFW window");

        long hwndVal = GLFWNativeWin32.glfwGetWin32Window(window);
        HWND hwndOverlay = new HWND(new com.sun.jna.Pointer(hwndVal));
        int exStyle = User32.INSTANCE.GetWindowLong(hwndOverlay, WinUser.GWL_EXSTYLE);
        exStyle |= WinUser.WS_EX_LAYERED | WinUser.WS_EX_TRANSPARENT;
        User32.INSTANCE.SetWindowLong(hwndOverlay, WinUser.GWL_EXSTYLE, exStyle);

        glfwMakeContextCurrent(window);
        glfwSwapInterval(1);
        GL.createCapabilities();

        glEnable(GL_BLEND);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        // --- Shaders ---
        shaderProgram = createShaderProgram();
        glUseProgram(shaderProgram);
        uniformCurrent = glGetUniformLocation(shaderProgram, "currentFrame");
        uniformPrevious = glGetUniformLocation(shaderProgram, "previousFrame");
        uniformThreshold = glGetUniformLocation(shaderProgram, "threshold");
        glUniform1i(uniformCurrent, 0);
        glUniform1i(uniformPrevious, 1);
        glUniform1f(uniformThreshold, 0.05f);
        glUseProgram(0);

        // --- Textures + FBO ---
        texCurrent = glGenTextures();
        texPrevious = glGenTextures();
        for (int tex : new int[]{texCurrent, texPrevious}) {
            glBindTexture(GL_TEXTURE_2D, tex);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        }

        fbo = glGenFramebuffers();

        // --- Main loop ---
        while (!glfwWindowShouldClose(window)) {
            User32.INSTANCE.GetWindowRect(hwndTarget, rect);
            int w = rect.right - rect.left + WIDTH_ADJUST;
            int h = rect.bottom - rect.top + HEIGHT_ADJUST;
            if (w <= 0 || h <= 0) continue;

            glfwSetWindowPos(window, rect.left + OFFSET_X, rect.top + OFFSET_Y);
            glfwSetWindowSize(window, w, h);

            glViewport(0, 0, w, h);
            glMatrixMode(GL_PROJECTION);
            glLoadIdentity();
            glOrtho(0, w, h, 0, -1, 1);
            glMatrixMode(GL_MODELVIEW);
            glLoadIdentity();

            ByteBuffer imgData = captureWindow(hwndTarget, w, h);
            if (imgData != null) {
                // upload current frame
                glBindTexture(GL_TEXTURE_2D, texCurrent);
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, w, h, 0, GL_BGRA, GL_UNSIGNED_BYTE, imgData);
            }

            // render quad with motion detection
            glClearColor(0, 0, 0, 0);
            glClear(GL_COLOR_BUFFER_BIT);
            glUseProgram(shaderProgram);

            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, texCurrent);
            glActiveTexture(GL_TEXTURE1);
            glBindTexture(GL_TEXTURE_2D, texPrevious);

            // --- set time uniform ---
            float elapsed = (System.nanoTime() - startTime) / 1_000_000_000f;
            int timeLoc = glGetUniformLocation(shaderProgram, "time");
            if (timeLoc >= 0) glUniform1f(timeLoc, elapsed);

            glBegin(GL_QUADS);
            glTexCoord2f(0f,0f); glVertex2f(0,0);
            glTexCoord2f(1f,0f); glVertex2f(w,0);
            glTexCoord2f(1f,1f); glVertex2f(w,h);
            glTexCoord2f(0f,1f); glVertex2f(0,h);
            glEnd();

            glUseProgram(0);
            glfwSwapBuffers(window);
            glfwPollEvents();

            // swap current/previous textures
            int tmp = texPrevious;
            texPrevious = texCurrent;
            texCurrent = tmp;
        }

        glDeleteTextures(texCurrent);
        glDeleteTextures(texPrevious);
        glDeleteProgram(shaderProgram);
        glfwDestroyWindow(window);
        glfwTerminate();
    }

    private static ByteBuffer captureWindow(HWND hwnd, int w, int h) {
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

        buffer = new Memory((long) w * h * 4);
        GDI32.INSTANCE.GetDIBits(hdcMemDC, hBitmap, 0, h, buffer, bmi, WinGDI.DIB_RGB_COLORS);

        ByteBuffer byteBuffer = BufferUtils.createByteBuffer(w*h*4);
        byteBuffer.put(buffer.getByteArray(0, w*h*4));
        byteBuffer.flip();

        GDI32.INSTANCE.SelectObject(hdcMemDC, old);
        GDI32.INSTANCE.DeleteObject(hBitmap);
        GDI32.INSTANCE.DeleteDC(hdcMemDC);
        User32.INSTANCE.ReleaseDC(hwnd, hdcWindow);

        return byteBuffer;
    }

    private static int createShaderProgram() throws Exception {
        String vert = loadShaderFromResource(VERTEX_SHADER_FILE);
        String frag = loadShaderFromResource(FRAGMENT_SHADER_FILE); 
        int vs = glCreateShader(GL_VERTEX_SHADER);
        glShaderSource(vs, vert);
        glCompileShader(vs);
        if (glGetShaderi(vs, GL_COMPILE_STATUS) == GL_FALSE)
            System.err.println("Vertex compile log:\n" + glGetShaderInfoLog(vs));

        int fs = glCreateShader(GL_FRAGMENT_SHADER);
        glShaderSource(fs, frag);
        glCompileShader(fs);
        if (glGetShaderi(fs, GL_COMPILE_STATUS) == GL_FALSE)
            System.err.println("Fragment compile log:\n" + glGetShaderInfoLog(fs));

        int prog = glCreateProgram();
        glAttachShader(prog, vs);
        glAttachShader(prog, fs);
        glLinkProgram(prog);
        if (glGetProgrami(prog, GL_LINK_STATUS) == GL_FALSE)
            System.err.println("Program link log:\n" + glGetProgramInfoLog(prog));

        glDeleteShader(vs);
        glDeleteShader(fs);
        return prog;
    }

    private static String loadShaderFromResource(String name) throws Exception {
        try (var is = BitBltFromMemoryDC_OPENGL.class.getResourceAsStream("/com/ferra/shaders/" + name)) {
            if (is == null) throw new RuntimeException("Shader not found: " + name);
            return new String(is.readAllBytes());
        }
    }
}
