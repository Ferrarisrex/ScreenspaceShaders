#version 120
attribute vec4 position;
attribute vec2 texCoord0;

varying vec2 texCoord;

void main() {
    texCoord = texCoord0;
    gl_Position = ftransform();
}
