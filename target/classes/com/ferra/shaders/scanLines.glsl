#version 120
uniform sampler2D tex;
void main() {
    vec2 uv = gl_TexCoord[0].st;
    vec4 color = texture2D(tex, uv);
    float scan = sin(uv.y * 800.0) * 0.1;
    gl_FragColor = vec4(color.rgb - scan, color.a);
}
