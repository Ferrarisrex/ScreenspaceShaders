#version 120
uniform sampler2D tex;
void main() {
    vec2 uv = gl_TexCoord[0].st;
    if(uv.x < 0.5)
        uv.x = uv.x * 2.0;
    else
        uv.x = 2.0*(1.0-uv.x);
    gl_FragColor = texture2D(tex, uv);
}
