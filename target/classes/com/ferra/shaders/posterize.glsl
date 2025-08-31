#version 120
uniform sampler2D tex;
void main() {
    vec4 color = texture2D(tex, gl_TexCoord[0].st);
    float levels = 4.0; // number of color levels
    color.rgb = floor(color.rgb * levels) / levels;
    gl_FragColor = color;
}
