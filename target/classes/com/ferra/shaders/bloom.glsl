#version 120
uniform sampler2D tex;
varying vec2 texCoord;

// bloom intensity and threshold
uniform float threshold = 0.7;   // only bright areas glow
uniform float intensity = 0.1;   // how strong the bloom is

void main() {
    vec4 color = texture2D(tex, texCoord);

    // compute luminance
    float lum = dot(color.rgb, vec3(0.299, 0.587, 0.114));

    // extract bright areas
    vec3 bloom = (lum > threshold) ? color.rgb : vec3(0.0);

    // simple blur by sampling neighbors
    float offset = 1.0/512.0; // adjust for resolution
    bloom += texture2D(tex, texCoord + vec2(offset, 0)).rgb * 0.25;
    bloom += texture2D(tex, texCoord + vec2(-offset, 0)).rgb * 0.25;
    bloom += texture2D(tex, texCoord + vec2(0, offset)).rgb * 0.25;
    bloom += texture2D(tex, texCoord + vec2(0, -offset)).rgb * 0.25;

    // add bloom to original color
    vec3 finalColor = color.rgb + bloom * intensity;

    gl_FragColor = vec4(finalColor, color.a);
}
