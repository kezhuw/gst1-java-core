package org.freedesktop.gstreamer;

import com.sun.jna.Pointer;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import org.freedesktop.gstreamer.glib.NativeObject;
import org.freedesktop.gstreamer.glib.Natives;
import org.freedesktop.gstreamer.lowlevel.GstCapsAPI;
import org.freedesktop.gstreamer.lowlevel.GstMiniObjectAPI;
import org.freedesktop.gstreamer.lowlevel.GstStructureAPI;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class NativeMemoryReuseExampleTest {
    @BeforeClass
    public static void setUpClass() throws Exception {
        Gst.init(Gst.getVersion(), "PromiseTest", new String[] {});
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        Gst.deinit();
    }

    private interface NativeResourceFactory<T> {
        String name();

        Pointer getNativePointer(T object);

        T newNativeObject();

        void disownNativeObject(T object);

        void freeNativeObject(T object);
    }

    private static abstract class AbstractNativeObjectFactory<T extends NativeObject> implements NativeResourceFactory<T> {
        @Override
        public final Pointer getNativePointer(NativeObject object) {
            return Natives.getRawPointer(object);
        }

        @Override
        public final void disownNativeObject(NativeObject object) {
            object.disown();
        }
    }

    private static abstract class AbstractNativeMiniObjectFactory<T extends MiniObject> extends AbstractNativeObjectFactory<T> {
        @Override
        public final void freeNativeObject(T object) {
            GstMiniObjectAPI.GSTMINIOBJECT_API.gst_mini_object_unref(getNativePointer(object));
        }
    }

    private static class StructureNativeObjectFactory extends AbstractNativeObjectFactory<Structure> {
        @Override
        public String name() {
            return "structure";
        }

        @Override
        public Structure newNativeObject() {
            return GstStructureAPI.GSTSTRUCTURE_API.gst_structure_new_empty("name");
        }

        @Override
        public void freeNativeObject(Structure object) {
            GstStructureAPI.GSTSTRUCTURE_API.gst_structure_free(getNativePointer(object));
        }
    }

    private static class CapsNativeObjectFactory extends AbstractNativeMiniObjectFactory<Caps> {
        @Override
        public String name() {
            return "caps";
        }

        @Override
        public Caps newNativeObject() {
            return GstCapsAPI.GSTCAPS_API.gst_caps_new_empty();
        }
    }

    private <T extends NativeObject> void testNativeObjectReuse(NativeResourceFactory<T> resourceFactory) {
        Map<Pointer, NativeObject> cachedObjects = new HashMap<>();
        IntStream.range(0, 1000).sequential().forEach(i -> {
            T object = resourceFactory.newNativeObject();
            Pointer pointer = resourceFactory.getNativePointer(object);
            NativeObject existingObject = cachedObjects.putIfAbsent(pointer, object);
            if (existingObject != null) {
                int refCount = object instanceof MiniObject ? ((MiniObject) object).getRefCount() : 1;
                Assert.fail(String.format("Resource %s: reuse pointer %s(refCount: %d) for object %s and %s",
                        resourceFactory.name(), pointer, refCount, object, existingObject));
            }
            resourceFactory.disownNativeObject(object);
            resourceFactory.freeNativeObject(object);
        });
    }

    @Test
    public void testNativeObjectStructureReuse() {
        testNativeObjectReuse(new StructureNativeObjectFactory());
    }

    @Test
    public void testNativeObjectCapsReuse() {
        testNativeObjectReuse(new CapsNativeObjectFactory());
    }

    private static abstract class AbstractNativePointerFactory implements NativeResourceFactory<Pointer> {
        @Override
        public final Pointer getNativePointer(Pointer object) {
            return object;
        }

        @Override
        public final void disownNativeObject(Pointer object) {
        }
    }

    private static class StructureNativePointerFactory extends AbstractNativePointerFactory {
        @Override
        public String name() {
            return "structure-pointer";
        }

        @Override
        public Pointer newNativeObject() {
            return GstStructureAPI.GSTSTRUCTURE_API.ptr_gst_structure_new_empty("test");
        }

        @Override
        public void freeNativeObject(Pointer object) {
            GstStructureAPI.GSTSTRUCTURE_API.gst_structure_free(object);
        }
    }

    private static class CapsNativePointerFactory extends AbstractNativePointerFactory {
        @Override
        public String name() {
            return "caps-pointer";
        }

        @Override
        public Pointer newNativeObject() {
            return GstCapsAPI.GSTCAPS_API.ptr_gst_caps_new_empty();
        }

        @Override
        public void freeNativeObject(Pointer object) {
            GstMiniObjectAPI.GSTMINIOBJECT_API.gst_mini_object_unref(getNativePointer(object));
        }
    }

    private void testNativePointerReuse(NativeResourceFactory<Pointer> resourceFactory) {
        Set<Pointer> cachedPointers = new HashSet<>();
        IntStream.range(0, 1000).forEach(i -> {
            Pointer pointer = resourceFactory.newNativeObject();
            if (!cachedPointers.add(pointer)) {
                Assert.fail(String.format("Resource %s: %s got freed and reused.", resourceFactory.name(), pointer));
            }
            resourceFactory.freeNativeObject(pointer);
        });
    }

    @Test
    public void testNativePointerStructureReuse() {
        testNativePointerReuse(new StructureNativePointerFactory());
    }

    @Test
    public void testNativePointerCapsReuse() {
        testNativePointerReuse(new CapsNativePointerFactory());
    }
}
