#version 120
uniform sampler2D tex;
uniform float time;
varying vec2 texCoord;

void main() {
    vec2 uv = texCoord;
    float offset = sin(uv.y*50.0 + time*5.0) * 0.01;
    uv.x += offset;

    vec4 color = texture2D(tex, uv);

    float flicker = sin(time*50.0 + uv.y*10.0) * 0.05;
    color.rgb += flicker;

    gl_FragColor = color;
}
