#version 120
uniform sampler2D tex;
varying vec2 texCoord;

const int numChars = 10;
const float scale = 8.0; // size of each ASCII “cell”

void main() {
    // sample texture at lower resolution
    vec2 uv = floor(texCoord * scale) / scale;
    vec4 color = texture2D(tex, uv);

    // convert to brightness
    float lum = dot(color.rgb, vec3(0.299, 0.587, 0.114));

    // map brightness to ascii index
    float index = floor(lum * float(numChars-1));

    // create blocky “ASCII” effect using the index
    float charVal = index / float(numChars-1); 

    // render as grayscale
    gl_FragColor = vec4(vec3(charVal), 1.0);
}
