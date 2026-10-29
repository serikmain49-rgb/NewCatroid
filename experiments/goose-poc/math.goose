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

// Box2D time-step scheduling; the Box2D simulation itself remains in libGDX.
export "newcatroid_physics_plan" fn physics_plan(accumulated: f32, step: f32, out: f32[:]) {
    var remainder = accumulated;
    var count = 0;
    while remainder >= step && count < 3 {
        remainder -= step;
        count += 1;
    }
    if remainder >= step { remainder = remainder % step; }
    out[0] = count as f32;
    out[1] = remainder;
}

// Stable insertion sort using exactly Java's float > comparison semantics.
export "newcatroid_sort_depth" fn sort_depth(ids: i32[:], depths: f32[:]) {
    for i in 1..ids.len {
        let key = ids[i];
        let depth = depths[i];
        var j = i;
        while j > 0 && depths[j - 1] > depth {
            ids[j] = ids[j - 1];
            depths[j] = depths[j - 1];
            j -= 1;
        }
        ids[j] = key;
        depths[j] = depth;
    }
}

// Numeric expression bytecode. The bridge validates stack effects and lengths.
// 0 = literal, 1..4 = arithmetic, 5 = negate, 6..11 = comparisons/logic,
// 12 = logical not, 13 = numeric equal, 14 = numeric not equal.
// Every instruction reproduces FormulaElement's intermediate infinity normalization.
export "newcatroid_formula_numeric" fn formula_numeric(ops: i32[:], values: f64[:], stack: f64[:], nan_value: f64) -> f64 {
    var top = 0;
    for i in 0..ops.len {
        let op = ops[i];
        if op == 0 {
            stack[top] = values[i];
            top += 1;
        } else if op == 5 {
            stack[top - 1] = -stack[top - 1];
        } else if op == 12 {
            if stack[top - 1] == 0.0 { stack[top - 1] = 1.0; }
            else { stack[top - 1] = 0.0; }
        } else {
            let right = stack[top - 1];
            let left = stack[top - 2];
            top -= 1;
            var result = nan_value;
            if op == 1 { result = left + right; }
            if op == 2 { result = left - right; }
            if op == 3 { result = left * right; }
            if op == 4 && right != 0.0 { result = left / right; }
            if op >= 6 {
                var truth = false;
                if op == 6 { truth = left > right; }
                if op == 7 { truth = left >= right; }
                if op == 8 { truth = left < right; }
                if op == 9 { truth = left <= right; }
                if op == 10 { truth = left != 0.0 && right != 0.0; }
                if op == 11 { truth = left != 0.0 || right != 0.0; }
                // FormulaElement treats two NaN values as equal.
                let equal = left == right || (left != left && right != right);
                if op == 13 { truth = equal; }
                if op == 14 { truth = !equal; }
                if truth { result = 1.0; } else { result = 0.0; }
            }
            let max = 1.7976931348623157e308;
            if result > max { result = max; }
            if result < -max { result = -max; }
            stack[top - 1] = result;
        }
    }
    stack[0]
}
