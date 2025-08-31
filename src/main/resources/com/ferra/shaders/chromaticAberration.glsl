#version 120
uniform sampler2D tex;
uniform float time;
varying vec2 texCoord;

void main() {
    float offset = 0.01 * sin(time*3.0);
    vec3 color;
    color.r = texture2D(tex, texCoord + vec2(offset,0)).r;
    color.g = texture2D(tex, texCoord).g;
    color.b = texture2D(tex, texCoord - vec2(offset,0)).b;
    gl_FragColor = vec4(color, 1.0);
}
