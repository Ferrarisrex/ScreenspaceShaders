#version 120
uniform sampler2D tex;
void main() {
    vec4 c = texture2D(tex, gl_TexCoord[0].st);
    gl_FragColor = vec4(c.g, c.b, c.r, c.a); // swap RGB channels
}
