#version 120
uniform sampler2D tex;
void main() {
    vec2 uv = gl_TexCoord[0].st;
    float size = 0.05; // size of pixels
    uv = floor(uv / size) * size;
    gl_FragColor = texture2D(tex, uv);
}
