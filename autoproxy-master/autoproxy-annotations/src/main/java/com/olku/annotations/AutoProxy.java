package com.olku.annotations;

import androidx.annotation.NonNull;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.HashMap;
import java.util.Map;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.CLASS;


@Retention(CLASS)
@Target(value = TYPE)
public @interface AutoProxy {

    String PROXY = "Proxy_";


    Class<? extends AutoProxyClassGenerator> value() default Common.class;


    int flags() default Flags.NONE;


    String defaultYield() default Returns.THROWS;


    Class<?> innerType() default Defaults.class;


    String prefix() default PROXY;


    abstract class Common implements AutoProxyClassGenerator {
    }


    abstract class Defaults {

         static final String INNER_TYPE = "innerType";

         static final String DEFAULT_YIELD = "defaultYield";

         static final String FLAGS = "flags";

         static final String VALUE = "value";

         static final String PREFIX = "prefix";
    }


    @Retention(CLASS)
    @Target(value = ElementType.METHOD)
    @interface Yield {

        Class<?> adapter() default Returns.class;


        String value() default Returns.THROWS;
    }


    @Retention(CLASS)
    @Target(value = ElementType.METHOD)
    @interface AfterCall {
    }


    @interface Flags {

        int NONE = 0x0000;

        int CREATOR = 0x0001;

        int AFTER_CALL = 0x0002;

        int MAPPING = 0x004;


        int ALL = CREATOR | AFTER_CALL | MAPPING;
    }


    @SuppressWarnings("ClassExplicitlyAnnotation")
    abstract class DefaultAutoProxy implements AutoProxy {
        @Override
        public final Class<? extends AutoProxyClassGenerator> value() {
            return Common.class;
        }

        @Override
        public final int flags() {
            return Flags.NONE;
        }

        @Override
        public final String defaultYield() {
            return Returns.THROWS;
        }


        @NonNull
        public static Map<String, Object> asMap() {
            final Map<String, Object> map = new HashMap<>();


            map.put(Defaults.VALUE, Common.class);
            map.put(Defaults.FLAGS, AutoProxy.Flags.NONE);
            map.put(Defaults.DEFAULT_YIELD, Returns.THROWS);
            map.put(Defaults.INNER_TYPE, Defaults.class);
            map.put(Defaults.PREFIX, PROXY);

            return map;
        }
    }


    @SuppressWarnings("ClassExplicitlyAnnotation")
    abstract class DefaultYield implements Yield {
        @Override
        public final Class<?> adapter() {
            return Returns.class;
        }

        @Override
        public final String value() {
            return Returns.THROWS;
        }


        @NonNull
        public static Map<String, Object> asMap() {
            final Map<String, Object> map = new HashMap<>();

            map.put("adapter", Returns.class);
            map.put("value", Returns.THROWS);

            return map;
        }

    }
}
