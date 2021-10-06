package com.olku.autoproxy.sample;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;


public class ParkingAreaTest {
    @Test
    public void testToBuilder() throws Exception {
        final ParkingArea first = ParkingArea.builder().build();

        final ParkingArea second = first.toBuilder().build();


        assertTrue(second.id() == first.id());
        assertFalse(second.hashCode() != first.hashCode());
        assertTrue(second.runtimeData == first.runtimeData);
    }

}