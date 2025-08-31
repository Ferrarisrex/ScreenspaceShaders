#version 120
uniform sampler2D tex;
uniform float time;
varying vec2 texCoord;

float rand(vec2 co, float seed){
    return fract(sin(dot(co.xy, vec2(12.9898,78.233)) + seed) * 43758.5453);
}

void main() {
    vec4 color = texture2D(tex, texCoord);

    // Use discrete frame index for flicker
    float frameSeed = floor(time * 60.0); 
    float noise = rand(texCoord * vec2(800.0, 600.0), frameSeed);

    // Add noise
    color.rgb += (noise - 0.5) * 0.25;

    // Luminance
    float brightness = dot(color.rgb, vec3(0.299, 0.587, 0.114));
    brightness = brightness + (1.0 - brightness) * 0.3; 
    brightness = ((brightness - 0.5) * 1.3 + 0.5);

    // Green tint
    gl_FragColor = vec4(brightness*0.1, brightness*1.0, brightness*0.1, color.a);
}
