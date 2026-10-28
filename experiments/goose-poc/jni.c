#include <jni.h>
#include <pthread.h>
#include "goose_poc.h"

/* Goose requires all exported calls on the thread that initialized it. */
static pthread_mutex_t mutex = PTHREAD_MUTEX_INITIALIZER;
static pthread_t owner;
static int initialized;
static int enter_goose(JNIEnv *env) {
    pthread_mutex_lock(&mutex);
    if (!initialized) {
        goose_init(0, NULL);
        owner = pthread_self();
        initialized = 1;
    } else if (!pthread_equal(owner, pthread_self())) {
        pthread_mutex_unlock(&mutex);
        jclass error = (*env)->FindClass(env, "java/lang/IllegalStateException");
        if (error) (*env)->ThrowNew(env, error, "Goose must be called on its initialization thread");
        return 0;
    }
    return 1;
}
JNIEXPORT jint JNICALL
Java_org_catrobat_catroid_goosepoc_GooseProbe_add(JNIEnv *env, jclass type, jint a, jint b) {
    (void)type;
    if (!enter_goose(env)) return 0;
    jint result = (jint)newcatroid_goose_add((int32_t)a, (int32_t)b);
    pthread_mutex_unlock(&mutex);
    return result;
}
JNIEXPORT jfloatArray JNICALL
Java_org_catrobat_catroid_goosepoc_GooseProbe_movement(JNIEnv *env, jclass type,
    jfloat x, jfloat y, jfloat rotation, jfloat vx, jfloat vy, jfloat gravity,
    jfloat angular, jfloat dt) {
    (void)type;
    if (!enter_goose(env)) return NULL;
    jfloat values[4];
    values[0] = newcatroid_velocity_y(vy, gravity, dt);
    values[1] = newcatroid_integrate(x, vx, dt);
    values[2] = newcatroid_integrate(y, values[0], dt);
    values[3] = newcatroid_integrate(rotation, angular, dt);
    pthread_mutex_unlock(&mutex);
    jfloatArray result = (*env)->NewFloatArray(env, 4);
    if (result) (*env)->SetFloatArrayRegion(env, result, 0, 4, values);
    return result;
}
