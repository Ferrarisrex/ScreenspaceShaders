#version 120
uniform sampler2D tex;
uniform float time;
void main() {
    vec2 uv = gl_TexCoord[0].st;
    vec4 sum = vec4(0.0);
    for(int i=0; i<8; i++){
        sum += texture2D(tex, uv - vec2(float(i)*0.01,0.0));
    }
    gl_FragColor = sum / 8.0;
}
