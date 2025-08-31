#version 120
uniform sampler2D tex;
void main() {
    vec2 uv = gl_TexCoord[0].st - 0.5;
    uv *= 1.0 - length(uv)*0.5;
    gl_FragColor = texture2D(tex, uv + 0.5);
}