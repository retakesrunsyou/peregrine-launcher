#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D PrevSampler;

uniform float Strength;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec3 now = texture(DiffuseSampler, texCoord).rgb;
    vec3 before = texture(PrevSampler, texCoord).rgb;
    fragColor = vec4(mix(now, before, Strength), 1.0);
}
