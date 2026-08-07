package test.record.primitive;

import yee.pltision.magicstorage.annotation.GenStore;

@GenStore
public record Simple(
        float a,
        float b
) {
}