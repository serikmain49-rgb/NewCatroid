// Isolated C ABI smoke test. This is not yet part of the game engine.
export "newcatroid_goose_add" fn add(a: i32, b: i32) -> i32 { a + b }

// Arithmetic from Fast2DMovementSystem.process; still an isolated experiment.
export "newcatroid_velocity_y" fn velocity_y(v: f32, gravity: f32, dt: f32) -> f32 {
    v + gravity * dt
}
export "newcatroid_integrate" fn integrate(value: f32, speed: f32, dt: f32) -> f32 {
    value + speed * dt
}
