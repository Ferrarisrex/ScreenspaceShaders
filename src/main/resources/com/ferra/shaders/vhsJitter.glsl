#version 120
uniform sampler2D tex;
uniform float time;
varying vec2 texCoord;

void main() {
    vec2 uv = texCoord;
    float shift = sin(uv.y*100.0 + time*10.0) * 0.02;
    uv.x += shift;
    vec4 color = texture2D(tex, uv);
    float flicker = sin(time*50.0 + uv.y*20.0) * 0.05;
    color.rgb += flicker;
    gl_FragColor = color;
}
