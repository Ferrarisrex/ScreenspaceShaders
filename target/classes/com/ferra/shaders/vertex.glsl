#version 120

// optional varying for new shaders
varying vec2 texCoord;

void main() {
    // keep fixed-function compatible
    gl_TexCoord[0] = gl_MultiTexCoord0;

    // also provide a varying for modern shaders
    texCoord = gl_MultiTexCoord0.xy;

    gl_Position = ftransform();
}
