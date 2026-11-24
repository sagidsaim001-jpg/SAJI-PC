#include <jni.h>

extern "C"
JNIEXPORT jstring JNICALL
Java_com_saji_pc_MainActivity_nativeEngineStatus(JNIEnv *env, jobject) {
    return env->NewStringUTF(
        "ARM32 native shell built. Windows runtime is not integrated yet."
    );
}
