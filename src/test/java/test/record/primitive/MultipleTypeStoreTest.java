package test.record.primitive;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class MultipleTypeStoreTest {

    @Test
    void testConstants() {
        assertEquals(3, MultipleTypeStore.F_SIZE);
        assertEquals(2, MultipleTypeStore.I_SIZE);
        assertEquals(0, MultipleTypeStore.A_OFFSET);
        assertEquals(1, MultipleTypeStore.B_OFFSET);
        assertEquals(2, MultipleTypeStore.C_OFFSET);
        assertEquals(0, MultipleTypeStore.D_OFFSET);
        assertEquals(1, MultipleTypeStore.E_OFFSET);
    }

    @Test
    void testArrayTypes() {
        MultipleTypeStore store = new MultipleTypeStore(5);
        assertArrayEquals(new float[15], store.fArray);  // 5 * 3
        assertArrayEquals(new int[10], store.iArray);    // 5 * 2
    }

    @Test
    void testFloatGroup() {
        MultipleTypeStore store = new MultipleTypeStore(2);
        store.setA(0, 1.0f);
        store.setB(0, 2.0f);
        store.setC(0, 3.0f);
        store.setA(1, 4.0f);
        store.setB(1, 5.0f);
        store.setC(1, 6.0f);

        assertEquals(1.0f, store.getA(0));
        assertEquals(2.0f, store.getB(0));
        assertEquals(3.0f, store.getC(0));
        assertEquals(4.0f, store.getA(1));
        assertEquals(5.0f, store.getB(1));
        assertEquals(6.0f, store.getC(1));
    }

    @Test
    void testIntGroup() {
        MultipleTypeStore store = new MultipleTypeStore(2);
        store.setD(0, 100);
        store.setE(0, 200);
        store.setD(1, 300);
        store.setE(1, 400);

        assertEquals(100, store.getD(0));
        assertEquals(200, store.getE(0));
        assertEquals(300, store.getD(1));
        assertEquals(400, store.getE(1));
    }

    @Test
    void testGroupSetters() {
        MultipleTypeStore store = new MultipleTypeStore(1);
        store.setF(0, 1.0f, 2.0f, 3.0f);
        store.setI(0, 10, 20);

        assertEquals(1.0f, store.getA(0));
        assertEquals(2.0f, store.getB(0));
        assertEquals(3.0f, store.getC(0));
        assertEquals(10, store.getD(0));
        assertEquals(20, store.getE(0));
    }

    @Test
    void testRecordGetSet() {
        MultipleTypeStore store = new MultipleTypeStore(1);
        MultipleType original = new MultipleType(1.0f, 2.0f, 3.0f, 10, 20);
        store.set(0, original);

        MultipleType retrieved = store.get(0);
        assertEquals(original, retrieved);
    }

    @Test
    void testRecordRoundTrip() {
        MultipleTypeStore store = new MultipleTypeStore(3);
        for (int i = 0; i < 3; i++) {
            MultipleType mt = new MultipleType(i * 0.5f, i * 1.5f, i * 2.5f, i * 10, i * 20);
            store.set(i, mt);
        }
        for (int i = 0; i < 3; i++) {
            MultipleType mt = store.get(i);
            assertEquals(i * 0.5f, mt.a());
            assertEquals(i * 1.5f, mt.b());
            assertEquals(i * 2.5f, mt.c());
            assertEquals(i * 10, mt.d());
            assertEquals(i * 20, mt.e());
        }
    }
}
