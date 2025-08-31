#version 120
uniform sampler2D tex;
uniform float time;
void main() {
    vec2 uv = gl_TexCoord[0].st;
    vec4 r = texture2D(tex, uv + vec2(0.03*sin(time*2.0),0));
    vec4 g = texture2D(tex, uv + vec2(0,0.03*sin(time*1.5)));
    vec4 b = texture2D(tex, uv + vec2(0.03*cos(time*1.7),0));
    gl_FragColor = vec4(r.r, g.g, b.b, 1.0);
}
