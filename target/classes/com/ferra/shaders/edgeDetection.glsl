#version 120
uniform sampler2D tex;
void main() {
    vec2 uv = gl_TexCoord[0].st;
    float dx = 1.0/512.0;
    float dy = 1.0/512.0;
    vec3 tl = texture2D(tex, uv + vec2(-dx, -dy)).rgb;
    vec3 t  = texture2D(tex, uv + vec2( 0.0, -dy)).rgb;
    vec3 tr = texture2D(tex, uv + vec2( dx, -dy)).rgb;
    vec3 l  = texture2D(tex, uv + vec2(-dx, 0.0)).rgb;
    vec3 r  = texture2D(tex, uv + vec2( dx, 0.0)).rgb;
    vec3 bl = texture2D(tex, uv + vec2(-dx, dy)).rgb;
    vec3 b  = texture2D(tex, uv + vec2( 0.0, dy)).rgb;
    vec3 br = texture2D(tex, uv + vec2( dx, dy)).rgb;
    vec3 gx = -tl - 2.0*l - bl + tr + 2.0*r + br;
    vec3 gy = -tl - 2.0*t - tr + bl + 2.0*b + br;
    float edge = length(gx + gy);
    gl_FragColor = vec4(vec3(edge), 1.0);
}
