#version 120
uniform sampler2D tex;
uniform float time; // add time uniform from Java
void main() {
    vec2 uv = gl_TexCoord[0].st;
    uv.y += 0.05 * sin(uv.x * 20.0 + time * 5.0);
    gl_FragColor = texture2D(tex, uv);
}
