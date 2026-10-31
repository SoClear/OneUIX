#include "traffic.h"

#include <jni.h>
#include <math.h>
#include <stdbool.h>
#include <stdio.h>

#define JNI_METHOD(name) Java_io_github_soclear_oneuix_hook_systemui_statusbar_NetWorkSpeed_##name

enum Layout { SYSTEM, SPLIT_HORIZONTAL, SPLIT_VERTICAL, ACTIVE_DIRECTION };

static struct {
    enum Layout layout;
    jstring up, down;
    bool bits, show_per_second;
    int base;
    float text_size, marker_size, marker_gap, line_spacing, padding_end;
    double threshold;
    jlong interval;
} config;

static struct {
    jfieldID content, updated, manager;
    jclass builder, size_span, scale_span, typed_value;
    jobject typeface;
    jmethodID builder_init, append, length, set_span, size_init, scale_init;
    jmethodID count_observers, set_changed, notify_observers, send_message;
    jmethodID set_text, set_size, set_lines, set_gravity, set_alignment;
    jmethodID set_typeface, set_line_spacing, set_font_padding;
    jmethodID get_resources, get_metrics, apply_dimension, get_paint, measure_text;
    jmethodID get_padding_start, get_padding_top, get_padding_bottom, set_padding;
    jmethodID set_visibility;
} java;

static TrafficSpeed speed;

static jclass global_class(JNIEnv *env, const char *name) {
    jclass local = (*env)->FindClass(env, name);
    jclass global = (*env)->NewGlobalRef(env, local);
    (*env)->DeleteLocalRef(env, local);
    return global;
}

JNIEXPORT void JNICALL JNI_METHOD(initialize)(
        JNIEnv *env, jobject self, jobject content, jobject updated, jobject manager, jint layout,
        jstring up, jstring down, jboolean bits, jint base, jboolean show_per_second, jfloat text_size,
        jfloat marker_size, jfloat marker_gap, jfloat line_spacing, jint threshold,
        jlong interval, jfloat padding_end) {
    (void)self;
    config.layout = layout;
    config.up = (*env)->NewGlobalRef(env, up);
    config.down = (*env)->NewGlobalRef(env, down);
    config.bits = bits;
    config.base = base;
    config.show_per_second = show_per_second;
    config.text_size = text_size;
    config.marker_size = marker_size;
    config.marker_gap = marker_gap;
    config.line_spacing = line_spacing;
    config.threshold = (double)threshold * base / (bits ? 8 : 1);
    config.interval = interval;
    config.padding_end = padding_end;
    java.content = (*env)->FromReflectedField(env, content);
    java.updated = (*env)->FromReflectedField(env, updated);
    java.manager = (*env)->FromReflectedField(env, manager);

    java.builder = global_class(env, "android/text/SpannableStringBuilder");
    java.builder_init = (*env)->GetMethodID(env, java.builder, "<init>", "()V");
    java.append = (*env)->GetMethodID(env, java.builder, "append", "(Ljava/lang/CharSequence;)Landroid/text/SpannableStringBuilder;");
    java.length = (*env)->GetMethodID(env, java.builder, "length", "()I");
    java.set_span = (*env)->GetMethodID(env, java.builder, "setSpan", "(Ljava/lang/Object;III)V");
    java.size_span = global_class(env, "android/text/style/AbsoluteSizeSpan");
    java.size_init = (*env)->GetMethodID(env, java.size_span, "<init>", "(I)V");
    java.scale_span = global_class(env, "android/text/style/ScaleXSpan");
    java.scale_init = (*env)->GetMethodID(env, java.scale_span, "<init>", "(F)V");
    java.typed_value = global_class(env, "android/util/TypedValue");
    java.apply_dimension = (*env)->GetStaticMethodID(env, java.typed_value, "applyDimension", "(IFLandroid/util/DisplayMetrics;)F");

    jclass observable = (*env)->FindClass(env, "java/util/Observable");
    java.count_observers = (*env)->GetMethodID(env, observable, "countObservers", "()I");
    java.set_changed = (*env)->GetMethodID(env, observable, "setChanged", "()V");
    java.notify_observers = (*env)->GetMethodID(env, observable, "notifyObservers", "(Ljava/lang/Object;)V");
    jclass handler = (*env)->FindClass(env, "android/os/Handler");
    java.send_message = (*env)->GetMethodID(env, handler, "sendEmptyMessageDelayed", "(IJ)Z");
    jclass text = (*env)->FindClass(env, "android/widget/TextView");
    java.set_text = (*env)->GetMethodID(env, text, "setText", "(Ljava/lang/CharSequence;)V");
    java.set_size = (*env)->GetMethodID(env, text, "setTextSize", "(IF)V");
    java.set_lines = (*env)->GetMethodID(env, text, "setLines", "(I)V");
    java.set_gravity = (*env)->GetMethodID(env, text, "setGravity", "(I)V");
    java.set_alignment = (*env)->GetMethodID(env, text, "setTextAlignment", "(I)V");
    java.set_typeface = (*env)->GetMethodID(env, text, "setTypeface", "(Landroid/graphics/Typeface;I)V");
    java.set_line_spacing = (*env)->GetMethodID(env, text, "setLineSpacing", "(FF)V");
    java.set_font_padding = (*env)->GetMethodID(env, text, "setIncludeFontPadding", "(Z)V");
    java.get_resources = (*env)->GetMethodID(env, text, "getResources", "()Landroid/content/res/Resources;");
    java.get_paint = (*env)->GetMethodID(env, text, "getPaint", "()Landroid/text/TextPaint;");
    java.get_padding_start = (*env)->GetMethodID(env, text, "getPaddingStart", "()I");
    java.get_padding_top = (*env)->GetMethodID(env, text, "getPaddingTop", "()I");
    java.get_padding_bottom = (*env)->GetMethodID(env, text, "getPaddingBottom", "()I");
    java.set_padding = (*env)->GetMethodID(env, text, "setPaddingRelative", "(IIII)V");
    jclass view = (*env)->FindClass(env, "android/view/View");
    java.set_visibility = (*env)->GetMethodID(env, view, "setVisibility", "(I)V");
    jclass resources = (*env)->FindClass(env, "android/content/res/Resources");
    java.get_metrics = (*env)->GetMethodID(env, resources, "getDisplayMetrics", "()Landroid/util/DisplayMetrics;");
    jclass paint = (*env)->FindClass(env, "android/graphics/Paint");
    java.measure_text = (*env)->GetMethodID(env, paint, "measureText", "(Ljava/lang/String;)F");
    jclass typeface = (*env)->FindClass(env, "android/graphics/Typeface");
    jfieldID monospace = (*env)->GetStaticFieldID(env, typeface, "MONOSPACE", "Landroid/graphics/Typeface;");
    jobject font = (*env)->GetStaticObjectField(env, typeface, monospace);
    java.typeface = (*env)->NewGlobalRef(env, font);
}

JNIEXPORT void JNICALL JNI_METHOD(tick)(JNIEnv *env, jobject self, jobject handler, jint what) {
    (void)self;
    if (what != 1 && what != 2) return;
    jobject manager = (*env)->GetObjectField(env, handler, java.manager);
    if ((*env)->CallIntMethod(env, manager, java.count_observers) == 0) return;
    if (what == 1) traffic_reset();
    traffic_sample(&speed);
    (*env)->CallVoidMethod(env, manager, java.set_changed);
    (*env)->CallVoidMethod(env, manager, java.notify_observers, NULL);
    (*env)->CallBooleanMethod(env, handler, java.send_message, 2, config.interval);
}

static jobject metrics_for(JNIEnv *env, jobject text) {
    jobject resources = (*env)->CallObjectMethod(env, text, java.get_resources);
    jobject metrics = (*env)->CallObjectMethod(env, resources, java.get_metrics);
    (*env)->DeleteLocalRef(env, resources);
    return metrics;
}

static float dimension(JNIEnv *env, jobject metrics, int unit, float value) {
    return (*env)->CallStaticFloatMethod(env, java.typed_value, java.apply_dimension, unit, (jdouble)value, metrics);
}

JNIEXPORT void JNICALL JNI_METHOD(applyStyle)(JNIEnv *env, jobject self, jobject view) {
    (void)self;
    jobject text = (*env)->GetObjectField(env, view, java.content);
    jobject metrics = metrics_for(env, text);
    bool two_lines = config.layout == SYSTEM || config.layout == SPLIT_VERTICAL;
    (*env)->CallVoidMethod(env, text, java.set_lines, two_lines ? 2 : 1);
    (*env)->CallVoidMethod(env, text, java.set_gravity, 0x800013); // START | CENTER_VERTICAL
    (*env)->CallVoidMethod(env, text, java.set_alignment, 1); // GRAVITY
    (*env)->CallVoidMethod(env, text, java.set_font_padding, JNI_FALSE);
    if (config.layout != SYSTEM) (*env)->CallVoidMethod(env, text, java.set_typeface, java.typeface, 1);
    if (config.text_size > 0) (*env)->CallVoidMethod(env, text, java.set_size, 2, (jdouble)config.text_size);
    (*env)->CallVoidMethod(env, text, java.set_line_spacing,
                          (jdouble)dimension(env, metrics, 1, config.line_spacing), 1.0);
    int start = (*env)->CallIntMethod(env, text, java.get_padding_start);
    int top = (*env)->CallIntMethod(env, text, java.get_padding_top);
    int bottom = (*env)->CallIntMethod(env, text, java.get_padding_bottom);
    int end = (int)lroundf(dimension(env, metrics, 1, config.padding_end));
    (*env)->CallVoidMethod(env, text, java.set_padding, start, top, end, bottom);
}

static void format_speed(double bytes, char *value, size_t size, bool split_unit) {
    double rate = bytes * (config.bits ? 8 : 1);
    const char *suffixes[] = {config.bits ? "b" : "B", "K", "M", "G", "T"};
    int power = 0;
    while (rate >= config.base && power < 4) {
        rate /= config.base;
        ++power;
    }
    const char *bit_suffix = power && config.bits ? (config.base == 1024 ? "ib" : "b") : "";
    int precision = !power ? 0 : rate < 100 ? 2 : 1;
    snprintf(value, size, "%.*f%s%s%s%s", precision, rate, split_unit ? "\n" : "",
             suffixes[power], bit_suffix, config.show_per_second ? "/s" : "");
}

static void append(JNIEnv *env, jobject builder, jstring value) {
    jobject result = (*env)->CallObjectMethod(env, builder, java.append, value);
    (*env)->DeleteLocalRef(env, result);
}

static void append_chars(JNIEnv *env, jobject builder, const char *value) {
    jstring string = (*env)->NewStringUTF(env, value);
    append(env, builder, string);
    (*env)->DeleteLocalRef(env, string);
}

static void append_reading(JNIEnv *env, jobject builder, jstring marker, jstring reading,
                           int marker_size, float extra_gap) {
    if ((*env)->GetStringLength(env, marker) > 0) {
        int start = (*env)->CallIntMethod(env, builder, java.length);
        append(env, builder, marker);
        int end = (*env)->CallIntMethod(env, builder, java.length);
        if (marker_size > 0) {
            jobject span = (*env)->NewObject(env, java.size_span, java.size_init, marker_size);
            (*env)->CallVoidMethod(env, builder, java.set_span, span, start, end, 33);
            (*env)->DeleteLocalRef(env, span);
        }
        extra_gap += config.marker_gap;
    }
    if (extra_gap > 0) {
        int start = (*env)->CallIntMethod(env, builder, java.length);
        append_chars(env, builder, " ");
        jobject span = (*env)->NewObject(env, java.scale_span, java.scale_init, (jdouble)extra_gap);
        (*env)->CallVoidMethod(env, builder, java.set_span, span, start, start + 1, 33);
        (*env)->DeleteLocalRef(env, span);
    }
    append(env, builder, reading);
}

JNIEXPORT void JNICALL JNI_METHOD(render)(JNIEnv *env, jobject self, jobject view) {
    (void)self;
    jobject text = (*env)->GetObjectField(env, view, java.content);
    jobject builder = (*env)->NewObject(env, java.builder, java.builder_init);
    double fastest = fmax(speed.tx, speed.rx);
    if ((config.threshold > 0 && fastest <= config.threshold) ||
        (config.layout == ACTIVE_DIRECTION && fastest < 1)) {
        (*env)->CallVoidMethod(env, text, java.set_text, builder);
        (*env)->CallVoidMethod(env, view, java.set_visibility, 8);
        return;
    }
    (*env)->CallVoidMethod(env, view, java.set_visibility, 0);
    char upload[64], download[64];
    if (config.layout == SYSTEM) {
        format_speed(speed.tx + speed.rx, download, sizeof(download), true);
        append_chars(env, builder, download);
    } else {
        jobject metrics = metrics_for(env, text);
        int marker_size = (int)lroundf(dimension(env, metrics, 2, config.marker_size));
        format_speed(speed.tx, upload, sizeof(upload), false);
        format_speed(speed.rx, download, sizeof(download), false);
        jstring tx = (*env)->NewStringUTF(env, upload);
        jstring rx = (*env)->NewStringUTF(env, download);
        if (config.layout == ACTIVE_DIRECTION) {
            bool sending = speed.tx >= speed.rx;
            append_reading(env, builder, sending ? config.up : config.down, sending ? tx : rx, marker_size, 0);
        } else {
            float tx_gap = 0, rx_gap = 0;
            if (config.layout == SPLIT_VERTICAL) {
                jobject paint = (*env)->CallObjectMethod(env, text, java.get_paint);
                float tx_width = (*env)->CallFloatMethod(env, paint, java.measure_text, tx);
                float rx_width = (*env)->CallFloatMethod(env, paint, java.measure_text, rx);
                jstring space = (*env)->NewStringUTF(env, " ");
                float space_width = (*env)->CallFloatMethod(env, paint, java.measure_text, space);
                tx_gap = fmaxf(rx_width - tx_width, 0) / space_width;
                rx_gap = fmaxf(tx_width - rx_width, 0) / space_width;
            }
            append_reading(env, builder, config.up, tx, marker_size, tx_gap);
            append_chars(env, builder, config.layout == SPLIT_HORIZONTAL ? " " : "\n");
            append_reading(env, builder, config.down, rx, marker_size, rx_gap);
        }
    }
    (*env)->CallVoidMethod(env, text, java.set_text, builder);
    (*env)->SetBooleanField(env, view, java.updated, JNI_TRUE);
}
