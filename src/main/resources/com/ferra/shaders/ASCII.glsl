#version 120

uniform sampler2D tex;       // original scene
uniform sampler2D asciiTex;  // ASCII atlas, one row
uniform vec2 screenSize;     // window resolution in pixels

varying vec2 texCoord;

const int numChars = 10;     // ASCII chars in atlas
const float cellSize = 8.0;  // pixels per character

void main() {
    vec2 fragPix = texCoord * screenSize;

    // Which cell is this fragment in?
    vec2 cell = floor(fragPix / cellSize);

    // Sample original scene at center of cell
    vec2 sampleUV = (cell + 0.5) * cellSize / screenSize;
    vec4 color = texture2D(tex, sampleUV);

    // Compute luminance
    float lum = dot(color.rgb, vec3(0.299, 0.587, 0.114));

    // Map brightness to ASCII index
    float index = floor(lum * float(numChars - 1));

    // Map to UV coordinates of atlas
    float charWidth = 1.0 / float(numChars);
    vec2 atlasUV = vec2(index * charWidth + fract(fragPix.x / cellSize) * charWidth,
                        fract(fragPix.y / cellSize));

    // Sample atlas
    vec4 asciiColor = texture2D(asciiTex, atlasUV);

    gl_FragColor = vec4(asciiColor.rgb, 1.0);
}
