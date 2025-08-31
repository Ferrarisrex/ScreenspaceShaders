#version 120
uniform sampler2D tex;
uniform float time;
varying vec2 texCoord;

float rand(vec2 co){
    return fract(sin(dot(co.xy ,vec2(12.9898,78.233))) * 43758.5453);
}

void main() {
    vec4 color = texture2D(tex, texCoord);
    float noise = rand(texCoord * vec2(800.0, 600.0) + time*50.0);
    color.rgb += (noise - 0.5) * 0.2; // subtle flicker
    gl_FragColor = color;
}
