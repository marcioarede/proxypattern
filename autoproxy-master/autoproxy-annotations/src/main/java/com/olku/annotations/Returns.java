package com.olku.annotations;

import androidx.annotation.StringDef;

import java.lang.annotation.Retention;

import static java.lang.annotation.RetentionPolicy.SOURCE;


@Retention(SOURCE)
@StringDef({Returns.EMPTY, Returns.THROWS, Returns.NULL, Returns.DIRECT, Returns.THIS, Returns.SKIP})
public @interface Returns {

    String EMPTY = "empty";

    String THROWS = "throws";

    String NULL = "null";

    String DIRECT = "direct";

    String THIS = "this";

    String SKIP = "skipped";
}


