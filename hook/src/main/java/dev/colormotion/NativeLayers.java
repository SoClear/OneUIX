package dev.colormotion;

import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.nio.ByteBuffer;

import dalvik.system.InMemoryDexClassLoader;

/** Loads the small typed bridge against SystemUI's classes, without bundling its Kotlin/Compose. */
final class NativeLayers {
    private final Constructor<?> layer, batch;
    private final ClassLoader loader;

    private NativeLayers(ClassLoader loader) throws ReflectiveOperationException {
        this.loader = loader;
        layer = loader.loadClass("dev.colormotion.nativebridge.LayerBlock")
                .getConstructor(Object.class, float[].class);
        batch = loader.loadClass("dev.colormotion.nativebridge.LayerBatch")
                .getConstructor(Runnable[].class, int[].class);
    }

    static NativeLayers load(ClassLoader systemUi, Object probeState) throws Exception {
        byte[] code;
        try (InputStream in = NativeLayers.class.getClassLoader()
                .getResourceAsStream("assets/colormotion-native.dex")) {
            if (in == null) throw new IllegalStateException("Native layer asset missing");
            code = in.readAllBytes();
        }
        ClassLoader loader = new InMemoryDexClassLoader(ByteBuffer.wrap(code), systemUi);
        loader.loadClass("dev.colormotion.nativebridge.Probe").getMethod("verify", Object.class)
                .invoke(null, probeState);
        return new NativeLayers(loader);
    }

    Object layer(Object state, float[] pose) throws ReflectiveOperationException {
        return layer.newInstance(state, pose);
    }

    Runnable batch(Runnable[] pending, int[] count) throws ReflectiveOperationException {
        return (Runnable) batch.newInstance(pending, count);
    }

    Runnable notifications(Object[] stack, float[] poses, int[] command) throws ReflectiveOperationException {
        return (Runnable) loader.loadClass("dev.colormotion.nativebridge.NotificationBlock")
                .getConstructor(Object[].class, float[].class, int[].class).newInstance(stack, poses, command);
    }
}
