#include <jni.h>
#include <math.h>
#include <pthread.h>
#include "movement_generated.h"

/* Goose exports must be used on the thread that initialized its runtime. */
static pthread_mutex_t lock = PTHREAD_MUTEX_INITIALIZER;
/* TLS is cleared when a thread exits; thread IDs can otherwise be reused. */
static _Thread_local int goose_owner_thread;
static int initialized;

JNIEXPORT jboolean JNICALL
Java_org_catrobat_catroid_fast2d_GooseMovement_step(JNIEnv *env, jclass type,
        jfloat x, jfloat y, jfloat rotation, jfloat vx, jfloat vy,
        jfloat gravity, jfloat angularVelocity, jfloat delta, jfloatArray out) {
    (void)type;
    if (!out || (*env)->GetArrayLength(env, out) < 4) return JNI_FALSE;
    pthread_mutex_lock(&lock);
    if (!initialized) {
        goose_init(0, NULL);
        goose_owner_thread = 1;
        initialized = 1;
    } else if (!goose_owner_thread) {
        pthread_mutex_unlock(&lock);
        return JNI_FALSE;
    }
    jfloat result[4];
    result[0] = newcatroid_velocity_y(vy, gravity, delta);
    result[1] = newcatroid_integrate(x, vx, delta);
    result[2] = newcatroid_integrate(y, result[0], delta);
    result[3] = newcatroid_integrate(rotation, angularVelocity, delta);
    pthread_mutex_unlock(&lock);
    (*env)->SetFloatArrayRegion(env, out, 0, 4, result);
    return (*env)->ExceptionCheck(env) ? JNI_FALSE : JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_org_catrobat_catroid_fast2d_GooseMovement_batch(JNIEnv *env, jclass type,
        jfloatArray data, jint count, jfloat delta) {
    (void)type;
    if (!data || count < 0 || count > (*env)->GetArrayLength(env, data) / 7) return JNI_FALSE;
    pthread_mutex_lock(&lock);
    if (!initialized) {
        goose_init(0, NULL);
        goose_owner_thread = 1;
        initialized = 1;
    } else if (!goose_owner_thread) {
        pthread_mutex_unlock(&lock);
        return JNI_FALSE;
    }
    jfloat *values = (*env)->GetFloatArrayElements(env, data, NULL);
    if (!values) {
        pthread_mutex_unlock(&lock);
        return JNI_FALSE;
    }
    sl_f32 slice = { values, (int64_t)count * 7 };
    newcatroid_movement_batch(slice, delta);
    (*env)->ReleaseFloatArrayElements(env, data, values, 0);
    pthread_mutex_unlock(&lock);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_org_catrobat_catroid_fast2d_GooseMovement_distance3d(JNIEnv *env, jclass type,
        jfloat ax, jfloat ay, jfloat az, jfloat bx, jfloat by, jfloat bz, jfloatArray out) {
    (void)type;
    if (!out || (*env)->GetArrayLength(env, out) < 1) return JNI_FALSE;
    pthread_mutex_lock(&lock);
    if (!initialized) {
        goose_init(0, NULL);
        goose_owner_thread = 1;
        initialized = 1;
    } else if (!goose_owner_thread) {
        pthread_mutex_unlock(&lock);
        return JNI_FALSE;
    }
    jfloat result = newcatroid_distance3d(ax, ay, az, bx, by, bz);
    pthread_mutex_unlock(&lock);
    (*env)->SetFloatArrayRegion(env, out, 0, 1, &result);
    return (*env)->ExceptionCheck(env) ? JNI_FALSE : JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_org_catrobat_catroid_fast2d_GooseMovement_visibilityBatch(JNIEnv *env, jclass type,
        jfloatArray data, jint count, jfloat cam_x, jfloat cam_y, jfloat half_w, jfloat half_h) {
    (void)type;
    if (!data || count < 0 || count > (*env)->GetArrayLength(env, data) / 5) return JNI_FALSE;
    pthread_mutex_lock(&lock);
    if (!initialized) {
        goose_init(0, NULL);
        goose_owner_thread = 1;
        initialized = 1;
    } else if (!goose_owner_thread) {
        pthread_mutex_unlock(&lock);
        return JNI_FALSE;
    }
    jfloat *values = (*env)->GetFloatArrayElements(env, data, NULL);
    if (!values) {
        pthread_mutex_unlock(&lock);
        return JNI_FALSE;
    }
    sl_f32 slice = { values, (int64_t)count * 5 };
    newcatroid_visibility_batch(slice, cam_x, cam_y, half_w, half_h);
    (*env)->ReleaseFloatArrayElements(env, data, values, 0);
    pthread_mutex_unlock(&lock);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_org_catrobat_catroid_fast2d_GooseMovement_physicsPlanNative(JNIEnv *env, jclass type,
        jfloat accumulator, jfloat time_step, jfloatArray out) {
    (void)type;
    if (!out || (*env)->GetArrayLength(env, out) < 2 || !(time_step > 0.0f)) return JNI_FALSE;
    pthread_mutex_lock(&lock);
    if (!initialized) {
        goose_init(0, NULL);
        goose_owner_thread = 1;
        initialized = 1;
    } else if (!goose_owner_thread) {
        pthread_mutex_unlock(&lock);
        return JNI_FALSE;
    }
    jfloat values[2];
    sl_f32 slice = { values, 2 };
    newcatroid_physics_plan(accumulator, time_step, slice);
    pthread_mutex_unlock(&lock);
    (*env)->SetFloatArrayRegion(env, out, 0, 2, values);
    return (*env)->ExceptionCheck(env) ? JNI_FALSE : JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_org_catrobat_catroid_fast2d_GooseMovement_sortDepthNative(JNIEnv *env, jclass type,
        jintArray ids, jfloatArray depths, jint count) {
    (void)type;
    if (!ids || !depths || count < 0 || count > (*env)->GetArrayLength(env, ids) ||
            count > (*env)->GetArrayLength(env, depths)) return JNI_FALSE;
    pthread_mutex_lock(&lock);
    if (!initialized) {
        goose_init(0, NULL);
        goose_owner_thread = 1;
        initialized = 1;
    } else if (!goose_owner_thread) {
        pthread_mutex_unlock(&lock);
        return JNI_FALSE;
    }
    jint *values = (*env)->GetIntArrayElements(env, ids, NULL);
    if (!values) { pthread_mutex_unlock(&lock); return JNI_FALSE; }
    jfloat *z = (*env)->GetFloatArrayElements(env, depths, NULL);
    if (!z) {
        (*env)->ReleaseIntArrayElements(env, ids, values, JNI_ABORT);
        pthread_mutex_unlock(&lock);
        return JNI_FALSE;
    }
    sl_i32 ids_slice = { values, count };
    sl_f32 depths_slice = { z, count };
    newcatroid_sort_depth(ids_slice, depths_slice);
    (*env)->ReleaseFloatArrayElements(env, depths, z, 0);
    (*env)->ReleaseIntArrayElements(env, ids, values, 0);
    pthread_mutex_unlock(&lock);
    return JNI_TRUE;
}

/* A bounded program keeps JNI calls and Goose bounds checks safe for malformed inputs. */
JNIEXPORT jboolean JNICALL
Java_org_catrobat_catroid_fast2d_GooseMovement_formulaNumericNative(JNIEnv *env, jclass type,
        jintArray operations, jdoubleArray values, jint count, jdoubleArray output) {
    (void)type;
    enum { LIMIT = 256 };
    if (!operations || !values || !output || count <= 0 || count > LIMIT ||
            count > (*env)->GetArrayLength(env, operations) ||
            count > (*env)->GetArrayLength(env, values) || (*env)->GetArrayLength(env, output) < 1)
        return JNI_FALSE;
    jint op[LIMIT];
    jdouble input[LIMIT], stack[LIMIT];
    (*env)->GetIntArrayRegion(env, operations, 0, count, op);
    if ((*env)->ExceptionCheck(env)) return JNI_FALSE;
    (*env)->GetDoubleArrayRegion(env, values, 0, count, input);
    if ((*env)->ExceptionCheck(env)) return JNI_FALSE;
    int depth = 0;
    for (int i = 0; i < count; i++) {
        if (op[i] == 0) depth++;
        else if (op[i] == 5 || op[i] == 12) { if (depth < 1) return JNI_FALSE; }
        else if ((op[i] >= 1 && op[i] <= 4) || (op[i] >= 6 && op[i] <= 11)
                || op[i] == 13 || op[i] == 14) { if (depth < 2) return JNI_FALSE; depth--; }
        else return JNI_FALSE;
    }
    if (depth != 1) return JNI_FALSE;
    pthread_mutex_lock(&lock);
    /* Formula evaluation also occurs in editor/UI code. Never let it claim
       the runtime before the game/render thread has initialized Goose. */
    if (!initialized || !goose_owner_thread) {
        pthread_mutex_unlock(&lock);
        return JNI_FALSE;
    }
    sl_i32 code = { op, count };
    sl_f64 operands = { input, count }, scratch = { stack, count };
    jdouble result = newcatroid_formula_numeric(code, operands, scratch, NAN);
    pthread_mutex_unlock(&lock);
    (*env)->SetDoubleArrayRegion(env, output, 0, 1, &result);
    return (*env)->ExceptionCheck(env) ? JNI_FALSE : JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_org_catrobat_catroid_fast2d_GooseMovement_initializeForGameThread(JNIEnv *env, jclass type) {
    (void)env; (void)type;
    pthread_mutex_lock(&lock);
    if (!initialized) {
        goose_init(0, NULL);
        goose_owner_thread = 1;
        initialized = 1;
    }
    jboolean result = goose_owner_thread ? JNI_TRUE : JNI_FALSE;
    pthread_mutex_unlock(&lock);
    return result;
}
