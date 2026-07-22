#include <jni.h>
#include <android/log.h>
#include <string>
#include <unistd.h>
#include <sys/wait.h>
#include <signal.h>

#define LOG_TAG "AnvilVM-QEMU"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static pid_t qemu_pid = -1;

extern "C" {

JNIEXPORT jint JNICALL
Java_com_anvilvm_app_engine_QemuEngine_nativeStartVM(
    JNIEnv *env,
    jobject /* this */,
    jstring qemuBinaryPath,
    jobjectArray args
) {
    const char *binaryPath = env->GetStringUTFChars(qemuBinaryPath, nullptr);

    int argc = env->GetArrayLength(args);
    std::vector<std::string> argStrings;
    std::vector<const char*> argv;

    argStrings.reserve(argc);
    argv.reserve(argc + 2);
    argv.push_back(binaryPath);

    for (int i = 0; i < argc; i++) {
        auto jstr = (jstring) env->GetObjectArrayElement(args, i);
        const char *str = env->GetStringUTFChars(jstr, nullptr);
        argStrings.emplace_back(str);
        argv.push_back(argStrings.back().c_str());
        env->ReleaseStringUTFChars(jstr, str);
    }
    argv.push_back(nullptr);

    LOGI("Starting QEMU: %s with %d args", binaryPath, argc);

    pid_t pid = fork();
    if (pid == 0) {
        execv(binaryPath, const_cast<char* const*>(argv.data()));
        LOGE("execv failed: %s", strerror(errno));
        _exit(127);
    } else if (pid > 0) {
        qemu_pid = pid;
        LOGI("QEMU started with PID: %d", pid);
    } else {
        LOGE("fork() failed: %s", strerror(errno));
        env->ReleaseStringUTFChars(qemuBinaryPath, binaryPath);
        return -1;
    }

    env->ReleaseStringUTFChars(qemuBinaryPath, binaryPath);
    return pid;
}

JNIEXPORT jboolean JNICALL
Java_com_anvilvm_app_engine_QemuEngine_nativeStopVM(
    JNIEnv * /* env */,
    jobject /* this */
) {
    if (qemu_pid <= 0) return JNI_FALSE;

    LOGI("Stopping QEMU PID: %d", qemu_pid);
    kill(qemu_pid, SIGTERM);

    int status;
    int result = waitpid(qemu_pid, &status, WNOHANG);
    if (result == 0) {
        usleep(500000);
        kill(qemu_pid, SIGKILL);
        waitpid(qemu_pid, &status, 0);
    }

    qemu_pid = -1;
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_anvilvm_app_engine_QemuEngine_nativeIsRunning(
    JNIEnv * /* env */,
    jobject /* this */
) {
    if (qemu_pid <= 0) return JNI_FALSE;
    int result = kill(qemu_pid, 0);
    return result == 0 ? JNI_TRUE : JNI_FALSE;
}

} // extern "C"
