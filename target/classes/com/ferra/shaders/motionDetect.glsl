#version 120
uniform sampler2D currentFrame;
uniform sampler2D previousFrame;
uniform float threshold;

varying vec2 texCoord;

void main() {
    vec4 curr = texture2D(currentFrame, texCoord);
    vec4 prev = texture2D(previousFrame, texCoord);

    float diff = distance(curr.rgb, prev.rgb);

    if(diff > threshold) {
        gl_FragColor = vec4(1.0, 0.0, 0.0, 1.0); // highlight movement
    } else {
        gl_FragColor = curr; // normal color
    }
}
