#include <jni.h>
#include <android/log.h>
#include <unistd.h>
#include <fcntl.h>
#include <termios.h>
#include <pty.h>
#include <string>

#define LOG_TAG "AnvilVM-PTY"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static int master_fd = -1;
static int slave_fd = -1;

extern "C" {

JNIEXPORT jint JNICALL
Java_com_anvilvm_app_engine_PtyBridge_nativeCreatePty(
    JNIEnv * /* env */,
    jobject /* this */,
    jint rows,
    jint cols
) {
    struct winsize ws = {};
    ws.ws_row = static_cast<unsigned short>(rows);
    ws.ws_col = static_cast<unsigned short>(cols);

    int result = openpty(&master_fd, &slave_fd, nullptr, nullptr, &ws);
    if (result < 0) {
        LOGE("openpty failed: %s", strerror(errno));
        return -1;
    }

    int flags = fcntl(master_fd, F_GETFL, 0);
    fcntl(master_fd, F_SETFL, flags | O_NONBLOCK);

    LOGI("PTY created: master_fd=%d, slave_fd=%d, size=%dx%d", master_fd, slave_fd, cols, rows);
    return master_fd;
}

JNIEXPORT jint JNICALL
Java_com_anvilvm_app_engine_PtyBridge_nativeReadPty(
    JNIEnv *env,
    jobject /* this */,
    jbyteArray buffer,
    jint maxLen
) {
    if (master_fd < 0) return -1;

    auto buf = new char[maxLen];
    ssize_t bytesRead = read(master_fd, buf, maxLen);

    if (bytesRead > 0) {
        env->SetByteArrayRegion(buffer, 0, static_cast<jsize>(bytesRead),
                                reinterpret_cast<jbyte*>(buf));
    }

    delete[] buf;
    return static_cast<jint>(bytesRead);
}

JNIEXPORT jint JNICALL
Java_com_anvilvm_app_engine_PtyBridge_nativeWritePty(
    JNIEnv *env,
    jobject /* this */,
    jbyteArray data,
    jint len
) {
    if (master_fd < 0) return -1;

    auto buf = new char[len];
    env->GetByteArrayRegion(data, 0, len, reinterpret_cast<jbyte*>(buf));

    ssize_t written = write(master_fd, buf, len);

    delete[] buf;
    return static_cast<jint>(written);
}

JNIEXPORT void JNICALL
Java_com_anvilvm_app_engine_PtyBridge_nativeResizePty(
    JNIEnv * /* env */,
    jobject /* this */,
    jint rows,
    jint cols
) {
    if (master_fd < 0) return;

    struct winsize ws = {};
    ws.ws_row = static_cast<unsigned short>(rows);
    ws.ws_col = static_cast<unsigned short>(cols);
    ioctl(master_fd, TIOCSWINSZ, &ws);
}

JNIEXPORT void JNICALL
Java_com_anvilvm_app_engine_PtyBridge_nativeClosePty(
    JNIEnv * /* env */,
    jobject /* this */
) {
    if (master_fd >= 0) close(master_fd);
    if (slave_fd >= 0) close(slave_fd);
    master_fd = -1;
    slave_fd = -1;
    LOGI("PTY closed");
}

JNIEXPORT jint JNICALL
Java_com_anvilvm_app_engine_PtyBridge_nativeGetSlaveFd(
    JNIEnv * /* env */,
    jobject /* this */
) {
    return slave_fd;
}

} // extern "C"
