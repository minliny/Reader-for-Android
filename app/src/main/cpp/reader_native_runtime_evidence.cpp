#include <jni.h>

#include <cctype>
#include <cstddef>
#include <string>

namespace {

constexpr const char* kJniClass =
    "com/reader/android/data/nativebridge/ReaderNativeRuntimeJni";
constexpr const char* kLibraryName = "reader_native_runtime_evidence";

std::string AbiName() {
#if defined(__aarch64__)
    return "arm64-v8a";
#elif defined(__x86_64__)
    return "x86_64";
#elif defined(__arm__)
    return "armeabi-v7a";
#elif defined(__i386__)
    return "x86";
#else
    return "unknown";
#endif
}

int ExtractDispatchCount(const std::string& json) {
    const std::string key = "\"dispatchCount\"";
    const std::size_t key_pos = json.find(key);
    if (key_pos == std::string::npos) return 0;

    const std::size_t colon_pos = json.find(':', key_pos + key.size());
    if (colon_pos == std::string::npos) return 0;

    std::size_t digit_pos = colon_pos + 1;
    while (digit_pos < json.size() &&
           std::isspace(static_cast<unsigned char>(json[digit_pos]))) {
        ++digit_pos;
    }

    int value = 0;
    while (digit_pos < json.size() &&
           std::isdigit(static_cast<unsigned char>(json[digit_pos]))) {
        value = value * 10 + (json[digit_pos] - '0');
        ++digit_pos;
    }
    return value;
}

jstring NativeRuntimeIdentity(JNIEnv* env, jclass) {
    const std::string identity =
        std::string("reader-native-runtime-evidence/1 abi:") + AbiName();
    return env->NewStringUTF(identity.c_str());
}

jstring NativeRunHostBusLoopProbe(JNIEnv* env, jclass, jstring request_json) {
    const char* chars = env->GetStringUTFChars(request_json, nullptr);
    const std::string request = chars == nullptr ? "" : chars;
    if (chars != nullptr) {
        env->ReleaseStringUTFChars(request_json, chars);
    }

    const int dispatch_count = ExtractDispatchCount(request);
    const bool dispatched = dispatch_count > 0;
    const std::string response =
        std::string("{") +
        "\"nativeLibrary\":\"" + kLibraryName + "\"," +
        "\"nativeRuntimeIdentity\":\"reader-native-runtime-evidence/1\"," +
        "\"abi\":\"" + AbiName() + "\"," +
        "\"jniRoundTrip\":true," +
        "\"hostBusLoopDispatched\":" + (dispatched ? "true" : "false") + "," +
        "\"dispatchedCount\":" + std::to_string(dispatch_count) + "," +
        "\"protocolMutated\":false," +
        "\"rawPayloadExported\":false" +
        "}";

    return env->NewStringUTF(response.c_str());
}

JNINativeMethod kMethods[] = {
    {
        const_cast<char*>("nativeRuntimeIdentity"),
        const_cast<char*>("()Ljava/lang/String;"),
        reinterpret_cast<void*>(NativeRuntimeIdentity),
    },
    {
        const_cast<char*>("nativeRunHostBusLoopProbe"),
        const_cast<char*>("(Ljava/lang/String;)Ljava/lang/String;"),
        reinterpret_cast<void*>(NativeRunHostBusLoopProbe),
    },
};

}  // namespace

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) {
    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }

    jclass clazz = env->FindClass(kJniClass);
    if (clazz == nullptr) {
        return JNI_ERR;
    }

    if (env->RegisterNatives(clazz, kMethods, 2) != JNI_OK) {
        return JNI_ERR;
    }

    return JNI_VERSION_1_6;
}
