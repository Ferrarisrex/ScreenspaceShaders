#version 120
uniform sampler2D tex;
void main() {
    vec4 color = texture2D(tex, gl_TexCoord[0].st);
    float gray = dot(color.rgb, vec3(0.299, 0.587, 0.114));
    gl_FragColor = vec4(vec3(gray), color.a);
}
