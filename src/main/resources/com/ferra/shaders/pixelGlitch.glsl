#version 120
uniform sampler2D tex;
uniform float time;
varying vec2 texCoord;

void main() {
    vec2 uv = texCoord;
    float yOffset = sin(uv.y*50.0 + time*10.0) * 0.02;
    float xOffset = step(0.98, fract(sin(time*10.0)*43758.5453)) * 0.1;
    uv.x += xOffset;
    uv.y += yOffset;
    gl_FragColor = texture2D(tex, uv);
}
