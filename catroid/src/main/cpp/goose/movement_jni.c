#include <jni.h>
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
