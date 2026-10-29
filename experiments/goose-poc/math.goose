// Isolated C ABI smoke test. This is not yet part of the game engine.
export "newcatroid_goose_add" fn add(a: i32, b: i32) -> i32 { a + b }

// Arithmetic from Fast2DMovementSystem.process; still an isolated experiment.
export "newcatroid_velocity_y" fn velocity_y(v: f32, gravity: f32, dt: f32) -> f32 {
    v + gravity * dt
}
export "newcatroid_integrate" fn integrate(value: f32, speed: f32, dt: f32) -> f32 {
    value + speed * dt
}

// Packed records: x, y, rotation, vx, vy, gravity, angular velocity.
// The caller validates that the slice length is a multiple of seven.
export "newcatroid_movement_batch" fn movement_batch(data: f32[:], dt: f32) {
    for entity in 0..data.len / 7 {
        let i = entity * 7;
        data[i + 4] += data[i + 5] * dt;
        data[i] += data[i + 3] * dt;
        data[i + 1] += data[i + 4] * dt;
        data[i + 2] += data[i + 6] * dt;
    }
}
