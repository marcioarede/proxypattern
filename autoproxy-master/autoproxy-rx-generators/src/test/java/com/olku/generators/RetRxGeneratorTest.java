package com.olku.generators;

import com.sun.tools.javac.code.Type;

import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

public class RetRxGeneratorTest {

    @Test
    public void extractGenericsFromReturnTypeComplex() {

        final String sample = "Single < Map < String, Pair < String, Boolean > > >";
        final String expected = "Map<String, Pair<String, Boolean>>";

        final Type mockType = Mockito.mock(Type.class);
        when(mockType.toString()).thenReturn(sample);


        final String result = RetRxGenerator.extractGenericsFromReturnType(mockType);


        assertEquals("Expected proper Generics extraction", expected, result);
    }

    @Test
    public void extractGenericsFromReturnTypeSimple() {

        final String sample = "Single<Map<String,Pair<String,Boolean>>>";
        final String expected = "Map<String, Pair<String, Boolean>>";

        final Type mockType = Mockito.mock(Type.class);
        when(mockType.toString()).thenReturn(sample);


        final String result = RetRxGenerator.extractGenericsFromReturnType(mockType);


        assertEquals("Expected proper Generics extraction", expected, result);
    }

    @Test
    public void extractGenericsFromReturnTypeTrivial() {

        final String sample = "Single<Boolean>";
        final String expected = "Boolean";

        final Type mockType = Mockito.mock(Type.class);
        when(mockType.toString()).thenReturn(sample);


        final String result = RetRxGenerator.extractGenericsFromReturnType(mockType);


        assertEquals("Expected proper Generics extraction", expected, result);
    }
}