package com.olku.annotations;

import androidx.annotation.StringDef;

import java.lang.annotation.Retention;

import static java.lang.annotation.RetentionPolicy.SOURCE;


@Retention(SOURCE)
@StringDef({RetNumber.MIN, RetNumber.MAX, RetNumber.ZERO, RetNumber.MINUS_ONE})
public @interface RetNumber {

    String MIN = "MIN_VALUE";

    String MAX = "MAX_VALUE";

    String ZERO = "0";

    String MINUS_ONE = "-1";
}
