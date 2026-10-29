#include "goose_poc.h"
#include <stdint.h>
#include <math.h>
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
    for (int i = 0; i < 100000; i++) {
        float ax = sample(), ay = sample(), az = sample();
        float bx = sample(), by = sample(), bz = sample();
        float dx = ax - bx, dy = ay - by, dz = az - bz;
        float expected = (float)sqrt((double)(dx * dx + dy * dy + dz * dz));
        if (!same(expected, newcatroid_distance3d(ax, ay, az, bx, by, bz))) {
            fprintf(stderr, "Distance mismatch at case %d\n", i);
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
    float rects[5 * 257], visibility_expected[257];
    for (int frame = 0; frame < 100; frame++) {
        float cx = sample(), cy = sample(), hw = sample(), hh = sample();
        for (int i = 0; i < 257; i++) {
            int o = i * 5;
            rects[o] = sample(); rects[o + 1] = sample();
            rects[o + 2] = sample(); rects[o + 3] = sample();
            if (i == 0) rects[o] = NAN;
            if (i == 1) rects[o + 1] = INFINITY;
            int outside = rects[o] + rects[o + 2] < cx - hw ||
                          rects[o] - rects[o + 2] > cx + hw ||
                          rects[o + 1] + rects[o + 3] < cy - hh ||
                          rects[o + 1] - rects[o + 3] > cy + hh;
            visibility_expected[i] = outside ? 0.0f : 1.0f;
        }
        sl_f32 rect_slice = { rects, 5 * 257 };
        newcatroid_visibility_batch(rect_slice, cx, cy, hw, hh);
        for (int i = 0; i < 257; i++) {
            if (!same(rects[i * 5 + 4], visibility_expected[i])) {
                fprintf(stderr, "Visibility mismatch at frame %d index %d\n", frame, i);
                return 1;
            }
        }
    }
    float time_step = 1.0f / 60.0f;
    for (int i = 0; i < 100000; i++) {
        float accumulator = sample() / 16.0f;
        if (i == 0) accumulator = NAN;
        if (i == 1) accumulator = INFINITY;
        float expected_accumulator = accumulator;
        int steps = 0;
        while (expected_accumulator >= time_step && steps < 3) {
            expected_accumulator -= time_step; steps++;
        }
        if (expected_accumulator >= time_step) expected_accumulator = fmodf(expected_accumulator, time_step);
        float result[2]; sl_f32 output = { result, 2 };
        newcatroid_physics_plan(accumulator, time_step, output);
        if (result[0] != steps || !(same(result[1], expected_accumulator) ||
              (isnan(result[1]) && isnan(expected_accumulator)))) {
            fprintf(stderr, "Physics scheduler mismatch at case %d\n", i); return 1;
        }
    }
    for (int trial = 0; trial < 1000; trial++) {
        int32_t ids[33], expected_ids[33];
        float depths[33], expected_depths[33];
        for (int i = 0; i < 33; i++) {
            ids[i] = expected_ids[i] = i;
            depths[i] = expected_depths[i] = (trial % 3 == 0) ? (float)(i % 4) : sample();
        }
        if (trial % 7 == 0) depths[12] = expected_depths[12] = NAN;
        if (trial % 11 == 0) depths[15] = expected_depths[15] = INFINITY;
        for (int i = 1; i < 33; i++) {
            int32_t key = expected_ids[i]; float z = expected_depths[i]; int j = i;
            while (j > 0 && expected_depths[j-1] > z) {
                expected_ids[j] = expected_ids[j-1]; expected_depths[j] = expected_depths[j-1]; j--;
            }
            expected_ids[j] = key; expected_depths[j] = z;
        }
        sl_i32 id_slice = { ids, 33 }; sl_f32 depth_slice = { depths, 33 };
        newcatroid_sort_depth(id_slice, depth_slice);
        for (int i = 0; i < 33; i++) if (ids[i] != expected_ids[i] || !same(depths[i], expected_depths[i])) {
            fprintf(stderr, "Depth order mismatch at trial %d index %d\n", trial, i); return 1;
        }
    }
    puts("Goose movement, visibility, distance, physics schedule and depth cases passed");
    return 0;
}
