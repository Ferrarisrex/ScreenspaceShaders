#version 120
uniform sampler2D tex;
uniform float time;
varying vec2 texCoord;

void main() {
    vec4 color = texture2D(tex, texCoord);
    float scanline = sin(texCoord.y*800.0 + time*20.0) * 0.05;
    color.rgb -= scanline;
    gl_FragColor = color;
}
