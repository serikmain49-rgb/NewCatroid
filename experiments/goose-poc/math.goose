extern fn nc_sqrt(x: f64) -> f64 = "sqrt";

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

// Matches libGDX Vector3.dst: float arithmetic followed by double sqrt and float cast.
export "newcatroid_distance3d" fn distance3d(ax: f32, ay: f32, az: f32, bx: f32, by: f32, bz: f32) -> f32 {
    let dx = ax - bx;
    let dy = ay - by;
    let dz = az - bz;
    nc_sqrt((dx * dx + dy * dy + dz * dz) as f64) as f32
}

// Records: x, y, half-width, half-height, output (1 = visible).
export "newcatroid_visibility_batch" fn visibility_batch(data: f32[:], cam_x: f32, cam_y: f32, cam_half_w: f32, cam_half_h: f32) {
    for entity in 0..data.len / 5 {
        let i = entity * 5;
        let x = data[i];
        let y = data[i + 1];
        let half_w = data[i + 2];
        let half_h = data[i + 3];
        if x + half_w < cam_x - cam_half_w || x - half_w > cam_x + cam_half_w ||
           y + half_h < cam_y - cam_half_h || y - half_h > cam_y + cam_half_h {
            data[i + 4] = 0.0;
        } else {
            data[i + 4] = 1.0;
        }
    }
}
