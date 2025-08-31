#version 120
uniform sampler2D tex;
uniform float time;
varying vec2 texCoord;

void main() {
    vec4 color = texture2D(tex, texCoord);
    float pulse = 0.5 + 0.5*sin(time*5.0);
    color.rgb *= pulse;
    gl_FragColor = color;
}
