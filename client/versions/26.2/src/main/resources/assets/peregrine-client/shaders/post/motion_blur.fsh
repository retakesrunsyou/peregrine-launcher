#version 330

uniform sampler2D InSampler;
uniform sampler2D PrevSampler;

in vec2 texCoord;

layout(std140) uniform MotionBlurConfig {
    float Strength;
};

out vec4 fragColor;

void main() {
    vec3 now = texture(InSampler, texCoord).rgb;
    vec3 before = texture(PrevSampler, texCoord).rgb;
    fragColor = vec4(mix(now, before, Strength), 1.0);
}
