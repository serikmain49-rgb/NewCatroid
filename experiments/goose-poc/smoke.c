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
    puts("Goose ABI and 100000 movement cases passed");
    return 0;
}
