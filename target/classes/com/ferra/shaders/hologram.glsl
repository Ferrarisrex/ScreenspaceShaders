#version 120
uniform sampler2D tex;
uniform float time;
varying vec2 texCoord;

void main() {
    vec4 color = texture2D(tex, texCoord);
    float scan = sin(texCoord.y * 800.0 + time*20.0) * 0.1;
    color.rgb += scan;
    color.g *= 1.2; // bluish tint
    color.b *= 1.5;
    gl_FragColor = color;
}
