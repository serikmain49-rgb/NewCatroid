#include <jni.h>
#include <pthread.h>
#include "goose_poc.h"

/* Goose requires all exported calls on the thread that initialized it. */
static pthread_mutex_t mutex = PTHREAD_MUTEX_INITIALIZER;
static pthread_t owner;
static int initialized;

JNIEXPORT jint JNICALL
Java_org_catrobat_catroid_goosepoc_GooseProbe_add(JNIEnv *env, jclass type, jint a, jint b) {
    (void)type;
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
    jint result = (jint)newcatroid_goose_add((int32_t)a, (int32_t)b);
    pthread_mutex_unlock(&mutex);
    return result;
}
