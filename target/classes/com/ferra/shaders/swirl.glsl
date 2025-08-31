#version 120
uniform sampler2D tex;
uniform float time;
void main() {
    vec2 uv = gl_TexCoord[0].st - 0.5;
    float angle = 5.0 * length(uv);
    float s = sin(angle);
    float c = cos(angle);
    vec2 rotated = vec2(c*uv.x - s*uv.y, s*uv.x + c*uv.y);
    gl_FragColor = texture2D(tex, rotated + 0.5);
}
