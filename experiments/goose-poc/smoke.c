#include "goose_poc.h"
#include <stdint.h>
#include <stdio.h>
#include <string.h>

static uint32_t seed = 0x829137u;
static float sample(void) {
    seed = seed * 1664525u + 1013904223u;
    return ((int32_t)(seed >> 8) - 8388608) / 8192.0f;
}
static int same(float a, float b) {
    uint32_t x, y;
    memcpy(&x, &a, sizeof x);
    memcpy(&y, &b, sizeof y);
    return x == y;
}
int main(int argc, char **argv) {
    goose_init(argc, argv);
    if (newcatroid_goose_add(20, 22) != 42 || newcatroid_goose_add(-2, 3) != 1) {
        fputs("Goose C ABI smoke test failed\n", stderr);
        return 1;
    }
    /* Match Fast2DMovementSystem's gravity-first update and float arithmetic. */
    for (int i = 0; i < 100000; i++) {
        float x = sample(), y = sample(), rot = sample();
        float vx = sample(), vy = sample(), gravity = sample();
        float angular = sample(), dt = sample() / 1024.0f;
        float expected_vy = vy + gravity * dt;
        float actual_vy = newcatroid_velocity_y(vy, gravity, dt);
        if (!same(expected_vy, actual_vy) ||
            !same(x + vx * dt, newcatroid_integrate(x, vx, dt)) ||
            !same(y + expected_vy * dt, newcatroid_integrate(y, actual_vy, dt)) ||
            !same(rot + angular * dt, newcatroid_integrate(rot, angular, dt))) {
            fprintf(stderr, "Movement mismatch at case %d\n", i);
            return 1;
        }
    }
    float actual[7 * 257], expected[7 * 257];
    for (int i = 0; i < 7 * 257; i++) actual[i] = expected[i] = sample();
    for (int frame = 0; frame < 120; frame++) {
        float dt = frame % 2 ? 0.016f : 0.033f;
        for (int i = 0; i < 7 * 257; i += 7) {
            expected[i + 4] += expected[i + 5] * dt;
            expected[i] += expected[i + 3] * dt;
            expected[i + 1] += expected[i + 4] * dt;
            expected[i + 2] += expected[i + 6] * dt;
        }
        sl_f32 slice = { actual, 7 * 257 };
        newcatroid_movement_batch(slice, dt);
        for (int i = 0; i < 7 * 257; i++) {
            if (!same(expected[i], actual[i])) {
                fprintf(stderr, "Goose batch mismatch at frame %d index %d\n", frame, i);
                return 1;
            }
        }
    }
    puts("Goose scalar and batch movement cases passed");
    return 0;
}
