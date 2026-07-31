package test.record.joml;

import org.joml.Vector2f;
import org.joml.Vector2i;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MultipleTypeJomlStoreTest {

    @Test
    void testConstants() {
        assertEquals(2, MultipleTypeJomlStore.F_SIZE);
        assertEquals(2, MultipleTypeJomlStore.I_SIZE);
        assertEquals(0, MultipleTypeJomlStore.ONE_OFFSET);
        assertEquals(2, MultipleTypeJomlStore.ONE_SIZE);
        assertEquals(0, MultipleTypeJomlStore.TOW_OFFSET);
        assertEquals(2, MultipleTypeJomlStore.TOW_SIZE);
    }

    @Test
    void testArrayTypes() {
        MultipleTypeJomlStore store = new MultipleTypeJomlStore(5);
        assertNotNull(store.fArray);
        assertNotNull(store.iArray);
        assertEquals(10, store.fArray.length);  // 5 * 2
        assertEquals(10, store.iArray.length);  // 5 * 2
    }

    @Test
    void testVector2fGetSet() {
        MultipleTypeJomlStore store = new MultipleTypeJomlStore(2);
        Vector2f v0 = new Vector2f(1.0f, 2.0f);
        Vector2f v1 = new Vector2f(3.0f, 4.0f);

        store.setOne(0, v0);
        store.setOne(1, v1);

        assertEquals(v0, store.getOne(0));
        assertEquals(v1, store.getOne(1));
    }

    @Test
    void testVector2iGetSet() {
        MultipleTypeJomlStore store = new MultipleTypeJomlStore(2);
        Vector2i v0 = new Vector2i(10, 20);
        Vector2i v1 = new Vector2i(30, 40);

        store.setTow(0, v0);
        store.setTow(1, v1);

        assertEquals(v0, store.getTow(0));
        assertEquals(v1, store.getTow(1));
    }

    @Test
    void testGetToDest() {
        MultipleTypeJomlStore store = new MultipleTypeJomlStore(1);
        store.setOne(0, new Vector2f(5.0f, 6.0f));

        Vector2f dest = new Vector2f();
        store.getOne(0, dest);
        assertEquals(new Vector2f(5.0f, 6.0f), dest);

        store.setTow(0, new Vector2i(7, 8));
        Vector2i destI = new Vector2i();
        store.getTow(0, destI);
        assertEquals(new Vector2i(7, 8), destI);
    }

    @Test
    void testPrimitiveSetter() {
        MultipleTypeJomlStore store = new MultipleTypeJomlStore(1);
        store.setOne(0, 1.5f, 2.5f);
        assertEquals(new Vector2f(1.5f, 2.5f), store.getOne(0));

        store.setTow(0, 10, 20);
        assertEquals(new Vector2i(10, 20), store.getTow(0));
    }

    @Test
    void testGroupSetter() {
        MultipleTypeJomlStore store = new MultipleTypeJomlStore(1);
        store.setF(0, new Vector2f(1.0f, 2.0f));
        assertEquals(new Vector2f(1.0f, 2.0f), store.getOne(0));

        store.setI(0, new Vector2i(10, 20));
        assertEquals(new Vector2i(10, 20), store.getTow(0));
    }

    @Test
    void testRecordGetSet() {
        MultipleTypeJomlStore store = new MultipleTypeJomlStore(1);
        MultipleTypeJoml original = new MultipleTypeJoml(
                new Vector2f(1.0f, 2.0f),
                new Vector2i(10, 20)
        );
        store.set(0, original);

        MultipleTypeJoml retrieved = store.get(0);
        assertEquals(original.one(), retrieved.one());
        assertEquals(original.tow(), retrieved.tow());
    }

    @Test
    void testRecordRoundTrip() {
        MultipleTypeJomlStore store = new MultipleTypeJomlStore(3);
        for (int i = 0; i < 3; i++) {
            MultipleTypeJoml m = new MultipleTypeJoml(
                    new Vector2f(i * 0.5f, i * 1.5f),
                    new Vector2i(i * 10, i * 20)
            );
            store.set(i, m);
        }
        for (int i = 0; i < 3; i++) {
            MultipleTypeJoml m = store.get(i);
            assertEquals(new Vector2f(i * 0.5f, i * 1.5f), m.one());
            assertEquals(new Vector2i(i * 10, i * 20), m.tow());
        }
    }

    @Test
    void testDifferentBackingArrays() {
        MultipleTypeJomlStore store = new MultipleTypeJomlStore(1);
        store.setOne(0, new Vector2f(1.0f, 2.0f));
        store.setTow(0, new Vector2i(10, 20));

        assertNotSame(store.fArray, store.iArray);
        assertEquals(1.0f, store.fArray[0]);
        assertEquals(2.0f, store.fArray[1]);
        assertEquals(10, store.iArray[0]);
        assertEquals(20, store.iArray[1]);
    }
}
