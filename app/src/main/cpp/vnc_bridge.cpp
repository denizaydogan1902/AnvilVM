#include <jni.h>
#include <android/log.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <arpa/inet.h>
#include <unistd.h>
#include <string>
#include <vector>
#include <cstring>

#define LOG_TAG "AnvilVM-VNC"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static int vnc_socket = -1;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_anvilvm_app_engine_VncBridge_nativeConnect(
    JNIEnv *env,
    jobject /* this */,
    jstring host,
    jint port
) {
    const char *hostStr = env->GetStringUTFChars(host, nullptr);

    vnc_socket = socket(AF_INET, SOCK_STREAM, 0);
    if (vnc_socket < 0) {
        LOGE("Socket creation failed");
        env->ReleaseStringUTFChars(host, hostStr);
        return JNI_FALSE;
    }

    struct sockaddr_in addr = {};
    addr.sin_family = AF_INET;
    addr.sin_port = htons(static_cast<uint16_t>(port));
    inet_pton(AF_INET, hostStr, &addr.sin_addr);

    int result = connect(vnc_socket, (struct sockaddr*)&addr, sizeof(addr));
    if (result < 0) {
        LOGE("VNC connect to %s:%d failed: %s", hostStr, port, strerror(errno));
        close(vnc_socket);
        vnc_socket = -1;
        env->ReleaseStringUTFChars(host, hostStr);
        return JNI_FALSE;
    }

    LOGI("VNC connected to %s:%d", hostStr, port);
    env->ReleaseStringUTFChars(host, hostStr);
    return JNI_TRUE;
}

JNIEXPORT jbyteArray JNICALL
Java_com_anvilvm_app_engine_VncBridge_nativeReadFrame(
    JNIEnv *env,
    jobject /* this */,
    jint maxBytes
) {
    if (vnc_socket < 0) return nullptr;

    std::vector<char> buf(maxBytes);
    ssize_t bytesRead = recv(vnc_socket, buf.data(), maxBytes, 0);

    if (bytesRead <= 0) return nullptr;

    jbyteArray result = env->NewByteArray(static_cast<jsize>(bytesRead));
    env->SetByteArrayRegion(result, 0, static_cast<jsize>(bytesRead),
                            reinterpret_cast<jbyte*>(buf.data()));
    return result;
}

JNIEXPORT jboolean JNICALL
Java_com_anvilvm_app_engine_VncBridge_nativeSendInput(
    JNIEnv *env,
    jobject /* this */,
    jbyteArray data
) {
    if (vnc_socket < 0) return JNI_FALSE;

    int len = env->GetArrayLength(data);
    auto buf = new char[len];
    env->GetByteArrayRegion(data, 0, len, reinterpret_cast<jbyte*>(buf));

    ssize_t sent = send(vnc_socket, buf, len, 0);
    delete[] buf;

    return sent > 0 ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_anvilvm_app_engine_VncBridge_nativeDisconnect(
    JNIEnv * /* env */,
    jobject /* this */
) {
    if (vnc_socket >= 0) {
        close(vnc_socket);
        vnc_socket = -1;
        LOGI("VNC disconnected");
    }
}

} // extern "C"
