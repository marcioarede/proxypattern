package com.olku.annotations;

import androidx.annotation.StringDef;

import java.lang.annotation.Retention;

import static java.lang.annotation.RetentionPolicy.SOURCE;


@Retention(SOURCE)
@StringDef({RetBool.TRUE, RetBool.FALSE})
public @interface RetBool {

    String TRUE = "true";

    String FALSE = "false";
}
