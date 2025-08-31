package com.ferra.subclasses;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class ImagePanel extends JPanel {
    private BufferedImage frame;

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        if (frame != null) g.drawImage(frame, 0, 0, getWidth(), getHeight(), null);
    }

    public void setFrame(BufferedImage img) {
        this.frame = img;
        repaint();
    }
}
