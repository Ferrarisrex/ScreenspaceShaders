#version 120
uniform sampler2D tex;
uniform float time;
varying vec2 texCoord;

float hash(vec2 p){
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}

void main() {
    vec4 color = texture2D(tex, texCoord);

    vec2 p = texCoord * vec2(50.0,50.0) + vec2(time*5.0);
    float n = hash(floor(p));
    if(n > 0.95){
        color.rgb = vec3(1.0,0.5,1.0); // particle highlight
    }

    gl_FragColor = color;
}
