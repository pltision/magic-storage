package test.record.primitive;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SimpleStoreTest {

    @Test
    void testConstants() {
        assertEquals(2, SimpleStore.SIMPLE_SIZE);
        assertEquals(0, SimpleStore.A_OFFSET);
        assertEquals(1, SimpleStore.B_OFFSET);
        assertEquals(1, SimpleStore.A_SIZE);
        assertEquals(1, SimpleStore.B_SIZE);
    }

    @Test
    void testArraySize() {
        SimpleStore store = new SimpleStore(5);
        assertEquals(5, store.size);
        assertEquals(10, store.simpleArray.length); // 5 * SIMPLE_SIZE(2)
    }

    @Test
    void testIndividualGetSet() {
        SimpleStore store = new SimpleStore(3);
        store.setA(0, 1.0f);
        store.setB(0, 2.0f);
        store.setA(1, 3.0f);
        store.setB(1, 4.0f);
        store.setA(2, 5.0f);
        store.setB(2, 6.0f);

        assertEquals(1.0f, store.getA(0));
        assertEquals(2.0f, store.getB(0));
        assertEquals(3.0f, store.getA(1));
        assertEquals(4.0f, store.getB(1));
        assertEquals(5.0f, store.getA(2));
        assertEquals(6.0f, store.getB(2));
    }

    @Test
    void testGroupSetter() {
        SimpleStore store = new SimpleStore(2);
        store.setSimple(0, 10.0f, 20.0f);
        store.setSimple(1, 30.0f, 40.0f);

        assertEquals(10.0f, store.getA(0));
        assertEquals(20.0f, store.getB(0));
        assertEquals(30.0f, store.getA(1));
        assertEquals(40.0f, store.getB(1));
    }

    @Test
    void testRecordGetSet() {
        SimpleStore store = new SimpleStore(2);
        Simple original = new Simple(7.0f, 8.0f);
        store.set(0, original);

        Simple retrieved = store.get(0);
        assertEquals(7.0f, retrieved.a());
        assertEquals(8.0f, retrieved.b());
    }

    @Test
    void testRecordRoundTrip() {
        SimpleStore store = new SimpleStore(4);
        for (int i = 0; i < 4; i++) {
            Simple s = new Simple(i * 1.5f, i * 2.5f);
            store.set(i, s);
        }
        for (int i = 0; i < 4; i++) {
            Simple s = store.get(i);
            assertEquals(i * 1.5f, s.a());
            assertEquals(i * 2.5f, s.b());
        }
    }

    @Test
    void testMultipleElementsIndependent() {
        SimpleStore store = new SimpleStore(3);
        store.setSimple(0, 1.0f, 2.0f);
        store.setSimple(1, 3.0f, 4.0f);
        store.setSimple(2, 5.0f, 6.0f);

        assertEquals(1.0f, store.getA(0));
        assertEquals(3.0f, store.getA(1));
        assertEquals(5.0f, store.getA(2));
    }
}
