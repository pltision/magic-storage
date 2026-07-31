package test.record.primitive;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PosUvRgbaStoreTest {

    @Test
    void testConstants() {
        assertEquals(3, PosUvRgbaStore.POS_SIZE);
        assertEquals(2, PosUvRgbaStore.UV_SIZE);
        assertEquals(4, PosUvRgbaStore.RGBA_SIZE);
        assertEquals(0, PosUvRgbaStore.X_OFFSET);
        assertEquals(1, PosUvRgbaStore.Y_OFFSET);
        assertEquals(2, PosUvRgbaStore.Z_OFFSET);
        assertEquals(0, PosUvRgbaStore.U_OFFSET);
        assertEquals(1, PosUvRgbaStore.V_OFFSET);
        assertEquals(0, PosUvRgbaStore.R_OFFSET);
        assertEquals(1, PosUvRgbaStore.G_OFFSET);
        assertEquals(2, PosUvRgbaStore.B_OFFSET);
        assertEquals(3, PosUvRgbaStore.A_OFFSET);
    }

    @Test
    void testArraySizes() {
        PosUvRgbaStore store = new PosUvRgbaStore(10);
        assertEquals(10, store.size);
        assertEquals(30, store.posArray.length);  // 10 * 3
        assertEquals(20, store.uvArray.length);   // 10 * 2
        assertEquals(40, store.rgbaArray.length);  // 10 * 4
    }

    @Test
    void testIndividualGetSet() {
        PosUvRgbaStore store = new PosUvRgbaStore(2);
        store.setX(0, 1.0f);
        store.setY(0, 2.0f);
        store.setZ(0, 3.0f);
        store.setU(0, 0.1f);
        store.setV(0, 0.2f);
        store.setR(0, 1.0f);
        store.setG(0, 0.0f);
        store.setB(0, 0.0f);
        store.setA(0, 1.0f);

        assertEquals(1.0f, store.getX(0));
        assertEquals(2.0f, store.getY(0));
        assertEquals(3.0f, store.getZ(0));
        assertEquals(0.1f, store.getU(0));
        assertEquals(0.2f, store.getV(0));
        assertEquals(1.0f, store.getR(0));
        assertEquals(0.0f, store.getG(0));
        assertEquals(0.0f, store.getB(0));
        assertEquals(1.0f, store.getA(0));
    }

    @Test
    void testGroupSetters() {
        PosUvRgbaStore store = new PosUvRgbaStore(2);
        store.setPos(0, 1.0f, 2.0f, 3.0f);
        store.setUv(0, 0.5f, 0.6f);
        store.setRgba(0, 1.0f, 0.5f, 0.25f, 0.125f);

        assertEquals(1.0f, store.getX(0));
        assertEquals(2.0f, store.getY(0));
        assertEquals(3.0f, store.getZ(0));
        assertEquals(0.5f, store.getU(0));
        assertEquals(0.6f, store.getV(0));
        assertEquals(1.0f, store.getR(0));
        assertEquals(0.5f, store.getG(0));
        assertEquals(0.25f, store.getB(0));
        assertEquals(0.125f, store.getA(0));
    }

    @Test
    void testRecordGetSet() {
        PosUvRgbaStore store = new PosUvRgbaStore(1);
        PosUvRgba original = new PosUvRgba(1.0f, 2.0f, 3.0f, 0.1f, 0.2f, 1.0f, 0.0f, 0.0f, 1.0f);
        store.set(0, original);

        PosUvRgba retrieved = store.get(0);
        assertEquals(original, retrieved);
    }

    @Test
    void testMultipleElementsIndependent() {
        PosUvRgbaStore store = new PosUvRgbaStore(3);
        store.setPos(0, 1, 2, 3);
        store.setPos(1, 4, 5, 6);
        store.setPos(2, 7, 8, 9);

        assertEquals(1.0f, store.getX(0));
        assertEquals(4.0f, store.getX(1));
        assertEquals(7.0f, store.getX(2));
        assertEquals(5.0f, store.getY(1));
        assertEquals(9.0f, store.getZ(2));
    }

    @Test
    void testRawArrayAccess() {
        PosUvRgbaStore store = new PosUvRgbaStore(1);
        store.posArray[0] = 10.0f;
        store.posArray[1] = 20.0f;
        store.posArray[2] = 30.0f;

        assertEquals(10.0f, store.getX(0));
        assertEquals(20.0f, store.getY(0));
        assertEquals(30.0f, store.getZ(0));
    }
}
