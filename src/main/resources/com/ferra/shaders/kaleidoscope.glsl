#version 120
uniform sampler2D tex;
void main() {
    vec2 uv = gl_TexCoord[0].st - 0.5;
    float angle = atan(uv.y, uv.x) * 3.0;
    float radius = length(uv);
    vec2 newUV = vec2(cos(angle)*radius, sin(angle)*radius) + 0.5;
    gl_FragColor = texture2D(tex, newUV);
}
