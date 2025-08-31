#version 120
uniform sampler2D tex;
void main() {
    vec2 uv = gl_TexCoord[0].st;
    vec2 center = vec2(0.5, 0.5);
    vec2 dir = uv - center;
    vec4 sum = vec4(0.0);
    int samples = 5;
    for (int i = 0; i < samples; i++) {
        sum += texture2D(tex, uv - dir * 0.002 * float(i));
    }
    gl_FragColor = sum / float(samples);
}
