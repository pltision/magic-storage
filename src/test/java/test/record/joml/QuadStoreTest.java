package test.record.joml;

import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QuadStoreTest {

    @Test
    void testConstants() {
        assertEquals(24, QuadStore.QUAD_SIZE);
        assertEquals(0, QuadStore.TRANSFORM_OFFSET);
        assertEquals(16, QuadStore.TRANSFORM_SIZE);
        assertEquals(16, QuadStore.TEX_CROOD_OFFSET);
        assertEquals(4, QuadStore.TEX_CROOD_SIZE);
        assertEquals(20, QuadStore.RGBA_OFFSET);
        assertEquals(4, QuadStore.RGBA_SIZE);
    }

    @Test
    void testArraySize() {
        QuadStore store = new QuadStore(3);
        assertEquals(3, store.size);
        assertEquals(72, store.quadArray.length); // 3 * 24
    }

    @Test
    void testMatrix4fGetSet() {
        QuadStore store = new QuadStore(1);
        Matrix4f m = new Matrix4f().identity();
        store.setTransform(0, m);

        Matrix4f retrieved = store.getTransform(0);
        assertEquals(m, retrieved);
    }

    @Test
    void testMatrix4fNonIdentity() {
        QuadStore store = new QuadStore(1);
        Matrix4f m = new Matrix4f(
                1, 2, 3, 4,
                5, 6, 7, 8,
                9, 10, 11, 12,
                13, 14, 15, 16
        );
        store.setTransform(0, m);

        Matrix4f retrieved = store.getTransform(0);
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 4; col++) {
                assertEquals(m.get(row, col), retrieved.get(row, col),
                        "mismatch at [" + row + "][" + col + "]");
            }
        }
    }

    @Test
    void testVector4fGetSet() {
        QuadStore store = new QuadStore(2);
        Vector4f v0 = new Vector4f(1.0f, 2.0f, 3.0f, 4.0f);
        Vector4f v1 = new Vector4f(5.0f, 6.0f, 7.0f, 8.0f);

        store.setTexCrood(0, v0);
        store.setTexCrood(1, v1);
        store.setRgba(0, new Vector4f(0.1f, 0.2f, 0.3f, 0.4f));
        store.setRgba(1, new Vector4f(0.5f, 0.6f, 0.7f, 0.8f));

        assertEquals(v0, store.getTexCrood(0));
        assertEquals(v1, store.getTexCrood(1));
        assertEquals(new Vector4f(0.1f, 0.2f, 0.3f, 0.4f), store.getRgba(0));
        assertEquals(new Vector4f(0.5f, 0.6f, 0.7f, 0.8f), store.getRgba(1));
    }

    @Test
    void testGetToDest() {
        QuadStore store = new QuadStore(1);
        store.setTexCrood(0, new Vector4f(10.0f, 20.0f, 30.0f, 40.0f));

        Vector4f dest = new Vector4f();
        store.getTexCrood(0, dest);
        assertEquals(new Vector4f(10.0f, 20.0f, 30.0f, 40.0f), dest);
    }

    @Test
    void testPrimitiveSetter() {
        QuadStore store = new QuadStore(1);
        store.setTransform(0,
                1, 0, 0, 0,
                0, 1, 0, 0,
                0, 0, 1, 0,
                0, 0, 0, 1);

        Matrix4f retrieved = store.getTransform(0);
        assertEquals(new Matrix4f().identity(), retrieved);
    }

    @Test
    void testVectorPrimitiveSetter() {
        QuadStore store = new QuadStore(1);
        store.setTexCrood(0, 1.0f, 2.0f, 3.0f, 4.0f);
        assertEquals(new Vector4f(1.0f, 2.0f, 3.0f, 4.0f), store.getTexCrood(0));

        store.setRgba(0, 0.5f, 0.6f, 0.7f, 0.8f);
        assertEquals(new Vector4f(0.5f, 0.6f, 0.7f, 0.8f), store.getRgba(0));
    }

    @Test
    void testGroupSetter() {
        QuadStore store = new QuadStore(1);
        Matrix4f m = new Matrix4f().identity();
        Vector4f tex = new Vector4f(1, 2, 3, 4);
        Vector4f rgba = new Vector4f(0.1f, 0.2f, 0.3f, 0.4f);

        store.setQuad(0, m, tex, rgba);

        assertEquals(m, store.getTransform(0));
        assertEquals(tex, store.getTexCrood(0));
        assertEquals(rgba, store.getRgba(0));
    }

    @Test
    void testRecordGetSet() {
        QuadStore store = new QuadStore(1);
        Matrix4f m = new Matrix4f().translation(1, 2, 3);
        Vector4f tex = new Vector4f(0.1f, 0.2f, 0.3f, 0.4f);
        Vector4f rgba = new Vector4f(1.0f, 0.5f, 0.25f, 0.125f);

        Quad original = new Quad(m, tex, rgba);
        store.set(0, original);

        Quad retrieved = store.get(0);
        assertEquals(original.transform(), retrieved.transform());
        assertEquals(original.texCrood(), retrieved.texCrood());
        assertEquals(original.rgba(), retrieved.rgba());
    }

    @Test
    void testMultipleElementsIndependent() {
        QuadStore store = new QuadStore(3);
        for (int i = 0; i < 3; i++) {
            store.setTexCrood(i, new Vector4f(i, i + 1, i + 2, i + 3));
        }

        for (int i = 0; i < 3; i++) {
            Vector4f v = store.getTexCrood(i);
            assertEquals(i, v.x());
            assertEquals(i + 1, v.y());
            assertEquals(i + 2, v.z());
            assertEquals(i + 3, v.w());
        }
    }
}
