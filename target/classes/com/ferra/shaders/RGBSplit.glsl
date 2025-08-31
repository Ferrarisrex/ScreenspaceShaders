#version 120
uniform sampler2D tex;
void main() {
    vec2 uv = gl_TexCoord[0].st;
    vec4 r = texture2D(tex, uv + vec2(0.01, 0));
    vec4 g = texture2D(tex, uv);
    vec4 b = texture2D(tex, uv - vec2(0.01, 0));
    gl_FragColor = vec4(r.r, g.g, b.b, 1.0);
}
