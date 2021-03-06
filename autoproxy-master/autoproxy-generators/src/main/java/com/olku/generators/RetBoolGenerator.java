package com.olku.generators;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.olku.annotations.RetBool;
import com.squareup.javapoet.MethodSpec;
import com.sun.tools.javac.code.Type;


public class RetBoolGenerator implements ReturnsPoet {
    @NonNull
    public static RetBoolGenerator getInstance() {
        return Singleton.INSTANCE;
    }

    public boolean compose(@NonNull final Type returnType,
                           @Nullable @RetBool  final String type,
                           @NonNull final MethodSpec.Builder builder) {
        if (RetBool.FALSE.equals(type)) {
            builder.addStatement("return false");
            return true;
        } else if (RetBool.TRUE.equals(type)) {
            builder.addStatement("return true");
            return true;
        }

        return false;
    }

    private static final class Singleton {
         static final RetBoolGenerator INSTANCE = new RetBoolGenerator();
    }
}
