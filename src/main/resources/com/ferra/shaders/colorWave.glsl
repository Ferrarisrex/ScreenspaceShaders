#version 120
uniform sampler2D tex;
uniform float time;
varying vec2 texCoord;

void main() {
    vec4 color = texture2D(tex, texCoord);
    color.r += sin(texCoord.y*10.0 + time*2.0)*0.2;
    color.g += sin(texCoord.y*20.0 + time*3.0)*0.2;
    color.b += sin(texCoord.y*30.0 + time*4.0)*0.2;
    gl_FragColor = color;
}
