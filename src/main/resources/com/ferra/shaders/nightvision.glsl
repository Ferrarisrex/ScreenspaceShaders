#version 120
uniform sampler2D tex;
uniform float time;
varying vec2 texCoord;

float rand(vec2 co){
    return fract(sin(dot(co.xy ,vec2(12.9898,78.233))) * 43758.5453);
}

// Simple contrast function
vec3 applyContrast(vec3 color, float contrast) {
    return ((color - 0.5) * contrast + 0.5);
}

// Optional: brighten dark areas
vec3 brightenDark(vec3 color, float factor) {
    return color + (1.0 - color) * factor * (1.0 - color);
}

void main() {
    vec4 color = texture2D(tex, texCoord);

    // Add subtle flicker
    float noise = rand(texCoord * vec2(800.0, 600.0) + time*50.0);
    color.rgb += (noise - 0.5) * 0.2;

    // Convert to brightness (luminance)
    float brightness = dot(color.rgb, vec3(0.299, 0.587, 0.114));

    // Boost dark areas
    brightness = brightness + (1.0 - brightness) * 0.3; // increase dark parts

    // Optional contrast adjustment
    brightness = ((brightness - 0.5) * 1.3 + 0.5); // 1.3 = contrast factor

    // Apply green tint
    gl_FragColor = vec4(brightness*0.1, brightness*1.0, brightness*0.1, color.a);
}
