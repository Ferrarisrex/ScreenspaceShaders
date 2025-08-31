#version 120
uniform sampler2D tex;
void main() {
    vec4 color = texture2D(tex, gl_TexCoord[0].st);
    vec3 c = color.rgb;
    gl_FragColor = vec4(
        c.r * 0.393 + c.g * 0.769 + c.b * 0.189,
        c.r * 0.349 + c.g * 0.686 + c.b * 0.168,
        c.r * 0.272 + c.g * 0.534 + c.b * 0.131,
        color.a
    );
}
