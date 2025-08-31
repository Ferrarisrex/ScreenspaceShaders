#version 120
uniform sampler2D tex;
void main() {
    vec2 uv = gl_TexCoord[0].st - 0.5;
    float dist = length(uv);
    vec4 color = texture2D(tex, gl_TexCoord[0].st);
    gl_FragColor = vec4(color.rgb * smoothstep(0.8, 0.0, dist), color.a);
}
