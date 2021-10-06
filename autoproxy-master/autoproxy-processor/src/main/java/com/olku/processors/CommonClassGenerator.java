package com.olku.processors;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringDef;

import com.olku.annotations.AutoProxy;
import com.olku.annotations.AutoProxy.Flags;
import com.olku.annotations.AutoProxyClassGenerator;
import com.olku.annotations.RetBool;
import com.olku.annotations.RetNumber;
import com.olku.annotations.Returns;
import com.olku.generators.RetBoolGenerator;
import com.olku.generators.RetNumberGenerator;
import com.olku.generators.ReturnsGenerator;
import com.olku.generators.ReturnsPoet;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import com.sun.tools.javac.code.Attribute;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiFunction;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.Name;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;

import sun.reflect.annotation.AnnotationParser;

import static javax.tools.Diagnostic.Kind.NOTE;


@SuppressWarnings({"WeakerAccess", "SameParameterValue", "UnnecessaryLocalVariable"})
public class CommonClassGenerator implements AutoProxyClassGenerator {

    public static boolean IS_DEBUG = AutoProxyProcessor.IS_DEBUG;


    public static final Attribute.Compound GLOBAL_AFTER_CALL = new Attribute.Compound(null, com.sun.tools.javac.util.List.nil());

    protected static final String PREDICATE = "predicate";

    protected static final String AFTER_CALL = "afterCall";

    protected static final String CREATOR = "create";

    protected static final String MAPPER = "dispatchByName";

    protected static final String BINDER = "bind";

    protected static final String METHODS = "M";

    protected static final String METHOD_NAME = "methodName";
    private static final String BI_FUNCTION_FIX = "Copy this declaration to fix method demands for old APIs:\n\n" +
            "<pre>\n" +
            "package java.util.function;\n" +
            "\n" +
            "public interface BiFunction&lt;T, U, R&gt; {\n" +
            "    R apply(T t, U u);\n" +
            "}\n" +
            "</pre>";

    private static final Modifier DEFAULT_VISIBILITY = Modifier.DEFAULT;

    protected final TypeProcessor type;
    protected final StringWriter errors = new StringWriter();
    protected final TypeName superType;
    protected final AtomicBoolean isAnyAfterCalls = new AtomicBoolean();
    protected final Map<String, Symbol.MethodSymbol> mappedCalls = new TreeMap<>();
    protected final Set<String> knownMethods = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
    protected JavaFile javaFile;

    public CommonClassGenerator(@NonNull final TypeProcessor type) {
        this.type = type;

        superType = TypeName.get(this.type.element.asType());
    }

    @Override
    public boolean compose(@NonNull final Filer filer) {
        try {
            composeInternal(filer);
        } catch (final Throwable ex) {
            ex.printStackTrace(new PrintWriter(errors));
            return false;
        }

        return true;
    }


    protected void composeInternal(@NonNull Filer filer) throws Exception {
        final boolean hasAfterCalls = ((this.type.annotation.flags() & Flags.AFTER_CALL) == Flags.AFTER_CALL);
        final boolean hasBinding = !superType.toString().equals(getSuperTypeName().toString());
        isAnyAfterCalls.set(hasAfterCalls);

        final FieldSpec[] members = createMembers();
        final TypeSpec.Builder classSpec = createClass(members);

        classSpec.addMethod(createConstructor().build());
        if (hasBinding) classSpec.addMethod(createBindConstructor().build());
        classSpec.addMethod(createPredicate().build());

        createMethods(classSpec);

        if (isAnyAfterCalls.get()) {
            classSpec.addMethod(createAfterCall().build());
        }

        if ((this.type.annotation.flags() & Flags.CREATOR) == Flags.CREATOR) {
            classSpec.addMethod(createCreator().build());
            if (hasBinding) classSpec.addMethod(createBindCreator().build());
        }

        if (hasBinding) {
            classSpec.addMethod(createBinder().build());
        }

        if ((this.type.annotation.flags() & Flags.MAPPING) == Flags.MAPPING) {
            classSpec.addMethod(createMapper().build());
        }

        classSpec.addType(createMethodsNamesMapper().build());

        classSpec.addOriginatingElement(type.element);

        javaFile = JavaFile.builder(type.packageName.toString(), classSpec.build()).build();
        javaFile.writeTo(filer);
    }

    @Override
    @NonNull
    public String getErrors() {
        return errors.toString();
    }

    @NonNull
    @Override
    public String getName() {
        if (null == javaFile) return "";

        return javaFile.toJavaFileObject().getName();
    }

    @NonNull
    @Override
    public List<Element> getOriginating() {
        if (null == javaFile) return Collections.emptyList();

        return javaFile.typeSpec.originatingElements;
    }

    @NonNull
    protected FieldSpec[] createMembers() {
        final List<FieldSpec> fields = new ArrayList<>();

        final TypeName typeOfField = this.superType; //getSuperTypeName();
        final FieldSpec.Builder builder = FieldSpec.builder(typeOfField, "inner", Modifier.PROTECTED, Modifier.FINAL);
        fields.add(builder.build());

        return fields.toArray(new FieldSpec[0]);
    }

    @NonNull
    private TypeName getSuperTypeName() {
        return this.type.getAnnotationSuperTypeAsTypeName();
    }

    @NonNull
    protected TypeSpec.Builder createClass(@NonNull final FieldSpec... members) {
        final String prefix = this.type.annotation.prefix();
        final TypeSpec.Builder builder = TypeSpec.classBuilder(prefix + type.flatClassName)
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT);


        builder.addAnnotation(AnnotationSpec.builder(javax.annotation.Generated.class)
                .addMember("value", "$S", "AutoProxy Auto Generated Code")
                .build());

        if (ElementKind.INTERFACE == type.element.getKind()) {
            builder.addSuperinterface(superType);

            copyTypeGenericVariables(builder);
        } else if (ElementKind.CLASS == type.element.getKind()) {
            builder.superclass(superType);

            copyTypeGenericVariables(builder);
        } else {
            final String message = "Unsupported data type: " + type.element.getKind() + ", " + type.elementType;
            errors.write(message + "\n");

            throw new UnsupportedOperationException(message);
        }

        for (final FieldSpec member : members) {
            builder.addField(member);
        }

        return builder;
    }

    private void copyTypeGenericVariables(final TypeSpec.Builder builder) {
        if (!(superType instanceof ParameterizedTypeName)) return;

        ParameterizedTypeName ptn = (ParameterizedTypeName) superType;

        for (final TypeName typeName : ptn.typeArguments) {
            if (!(typeName instanceof TypeVariableName)) continue;

            builder.addTypeVariable((TypeVariableName) typeName);
        }
    }

    @SuppressWarnings("unused")
    private void copyMethodGenericVariables(final MethodSpec.Builder builder) {
        if (!(superType instanceof ParameterizedTypeName)) return;

        ParameterizedTypeName ptn = (ParameterizedTypeName) superType;

        for (final TypeName typeName : ptn.typeArguments) {
            if (!(typeName instanceof TypeVariableName)) continue;

            builder.addTypeVariable((TypeVariableName) typeName);
        }
    }

    @NonNull
    protected MethodSpec.Builder createConstructor() {
        final TypeName innerMemberSuperType = this.superType; //getSuperTypeName();
        final ParameterSpec.Builder param = ParameterSpec.builder(innerMemberSuperType, "instance", Modifier.FINAL)
                .addAnnotation(NonNull.class);

        final MethodSpec.Builder builder = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(param.build())
                .addStatement("this.inner = $N", "instance");

        return builder;
    }

    @NonNull
    protected MethodSpec.Builder createBindConstructor() {
        final TypeName innerMemberSuperType = getSuperTypeName();
        final ParameterSpec.Builder param = ParameterSpec.builder(innerMemberSuperType, "instance", Modifier.FINAL)
                .addAnnotation(NonNull.class);

        final MethodSpec.Builder builder = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(param.build())
                .addStatement("this($L($N))", BINDER, "instance");

        return builder;
    }


    protected void createMethods(@NonNull final TypeSpec.Builder classSpec) throws Exception {
        RuntimeException runtimeError = null;
        for (final Element method : type.methods) {
            if (!(method instanceof Symbol.MethodSymbol)) {
                final String message = "Unexpected method type: " + method.getClass().getSimpleName();
                errors.write(message + "\n");

                runtimeError = new UnsupportedOperationException(message);
                continue;
            }

            classSpec.addMethod(createMethod((Symbol.MethodSymbol) method).build());
        }

        if (null != runtimeError) {
            throw runtimeError;
        }
    }


    @NonNull
    protected MethodSpec.Builder createPredicate() {

        final String methodName = PREDICATE;
        final MethodSpec.Builder builder = MethodSpec.methodBuilder(methodName);
        builder.addModifiers(Modifier.PROTECTED, Modifier.ABSTRACT);
        builder.returns(boolean.class);

        final ParameterSpec pMethodNames = ParameterSpec.builder(String.class, METHOD_NAME, Modifier.FINAL)
                .addAnnotation(AnnotationSpec.builder(ClassName.bestGuess(METHODS)).build())
                .addAnnotation(AnnotationSpec.builder(NonNull.class).build())
                .build();

        builder.addParameter(pMethodNames);


        builder.varargs(true);
        builder.addParameter(Object[].class, "args", Modifier.FINAL);

        return builder;
    }


    @NonNull
    protected MethodSpec.Builder createAfterCall() {
        final MethodSpec.Builder builder = MethodSpec.methodBuilder(AFTER_CALL);
        builder.addModifiers(Modifier.PROTECTED, Modifier.ABSTRACT);

        builder.addTypeVariable(TypeVariableName.get("T", Object.class));

        builder.returns(TypeVariableName.get("T"));

        final ParameterSpec pMethodNames = ParameterSpec.builder(String.class, METHOD_NAME, Modifier.FINAL)
                .addAnnotation(AnnotationSpec.builder(ClassName.bestGuess(METHODS)).build())
                .addAnnotation(AnnotationSpec.builder(NonNull.class).build())
                .build();
        builder.addParameter(pMethodNames);

        builder.addParameter(TypeVariableName.get("T"), "result", Modifier.FINAL);

        return builder;
    }


    @NonNull
    protected MethodSpec.Builder createCreator() {


        final MethodSpec.Builder builder = MethodSpec.methodBuilder(CREATOR);
        builder.addModifiers(Modifier.PUBLIC, Modifier.STATIC);
        builder.addAnnotation(NonNull.class);

        copyMethodGenericVariables(builder);
        builder.returns(superType);

        final TypeName innerMemberType = this.superType; //getSuperTypeName();
        final ParameterSpec.Builder param1 = ParameterSpec.builder(innerMemberType, "instance", Modifier.FINAL)
                .addAnnotation(NonNull.class);
        final ParameterSpec.Builder param2 = ParameterSpec.builder(ParameterizedTypeName.get(
                BiFunction.class, String.class, Object[].class, Boolean.class), "action", Modifier.FINAL)
                .addAnnotation(NonNull.class);
        builder.addParameter(param1.build());
        builder.addParameter(param2.build());
        builder.addJavadoc(BI_FUNCTION_FIX);

        final String afterCallOverride = '\n' +
                "  @Override\n" +
                "  public <T> T afterCall(final String methodName, final T result) {\n" +
                "    return result;\n" +
                "  };\n";

        final String predicateOverride = '\n' +
                "  @Override\n" +
                "  public boolean predicate(final String methodName, final Object... args) {\n" +
                "    return action.apply(methodName, args);\n" +
                "  }\n";

        final String prefix = this.type.annotation.prefix();
        builder.addCode("" +
                "return new $L(instance) {\n" +
                predicateOverride +
                (isAnyAfterCalls.get() ? afterCallOverride : "") +
                "};\n", prefix + type.flatClassName);

        return builder;
    }

    @NonNull
    protected MethodSpec.Builder createBindCreator() {
        final MethodSpec.Builder builder = MethodSpec.methodBuilder(CREATOR);
        builder.addModifiers(Modifier.PUBLIC, Modifier.STATIC);
        builder.addAnnotation(NonNull.class);

        copyMethodGenericVariables(builder);
        builder.returns(superType);

        final TypeName innerMemberType = getSuperTypeName();
        final ParameterSpec.Builder param1 = ParameterSpec.builder(innerMemberType, "instance", Modifier.FINAL)
                .addAnnotation(NonNull.class);
        final ParameterSpec.Builder param2 = ParameterSpec.builder(ParameterizedTypeName.get(
                BiFunction.class, String.class, Object[].class, Boolean.class), "action", Modifier.FINAL)
                .addAnnotation(NonNull.class);
        builder.addParameter(param1.build());
        builder.addParameter(param2.build());

        builder.addCode("return $L($L($L), $L);", CREATOR, BINDER, "instance", "action");

        return builder;
    }


    @NonNull
    protected MethodSpec.Builder createBinder() throws Exception {
        final MethodSpec.Builder builder = MethodSpec.methodBuilder(BINDER);
        builder.addAnnotation(NonNull.class);
        builder.returns(superType);
        builder.addModifiers(Modifier.PROTECTED, Modifier.STATIC);

        final TypeName innerMemberType = getSuperTypeName();
        final ParameterSpec.Builder param = ParameterSpec.builder(innerMemberType, "instance", Modifier.FINAL)
                .addAnnotation(NonNull.class);
        builder.addParameter(param.build());

        final TypeSpec.Builder anonymousClass = TypeSpec.anonymousClassBuilder("")
                .addSuperinterface(superType);

        for (final Element method : type.methods) {
            anonymousClass.addMethod(createDirectCall((Symbol.MethodSymbol) method).build());
        }

        builder.addCode("return $L;", anonymousClass.build());

        return builder;
    }

    @NonNull
    private MethodSpec.Builder createDirectCall(@NonNull Symbol.MethodSymbol ms) throws Exception {
        final String methodName = ms.getSimpleName().toString();
        final MethodSpec.Builder builder = MethodSpec.methodBuilder(methodName);
        builder.addModifiers(Modifier.FINAL, Modifier.PUBLIC);

        mimicMethodAnnotations(builder, ms);
        builder.addAnnotation(Override.class);

        final Type returnType = ms.getReturnType();
        final boolean hasReturn = returnType.getKind() != TypeKind.VOID;
        builder.returns(TypeName.get(returnType));

        final StringBuilder arguments = mimicParameters(builder, ms);

        mimicThrows(builder, ms);

        builder.addStatement((hasReturn ? "return " : "") + "instance.$L($L)",
                methodName, arguments.toString());

        return builder;
    }


    @NonNull
    protected MethodSpec.Builder createMapper() {
        final MethodSpec.Builder builder = MethodSpec.methodBuilder(MAPPER);
        builder.addModifiers(Modifier.PROTECTED);

        builder.addTypeVariable(TypeVariableName.get("T", Object.class));
        builder.returns(TypeVariableName.get("T"));

        final ParameterSpec pMethodNames = ParameterSpec.builder(String.class, METHOD_NAME, Modifier.FINAL)
                .addAnnotation(AnnotationSpec.builder(ClassName.bestGuess(METHODS)).build())
                .addAnnotation(AnnotationSpec.builder(NonNull.class).build())
                .build();
        builder.addParameter(pMethodNames);

        builder.varargs(true);
        builder.addParameter(Object[].class, "args", Modifier.FINAL);

        builder.addCode("final Object result;\n");

        for (final String name : mappedCalls.keySet()) {
            final Symbol.MethodSymbol ms = mappedCalls.get(name);
            final Type returnType = ms.getReturnType();
            final boolean hasReturn = returnType.getKind() != TypeKind.VOID;
            final String methodName = ms.getSimpleName().toString();
            final String params = composeCallParamsFromArray(ms, "args");

            builder.beginControlFlow("if($L.$L.equals(methodName))", METHODS, toConstantName(name));
            if (hasReturn) {
                builder.addStatement("return (T)(result = this.inner.$N($L))", methodName, params);
            } else {
                builder.addStatement("this.inner.$N($L)", methodName, params);
                builder.addStatement("return (T)null");
            }
            builder.endControlFlow();
        }


        builder.addCode("return (T)null;\n");

        return builder;
    }


    @NonNull
    protected TypeSpec.Builder createMethodsNamesMapper() {
        final TypeSpec.Builder builder = TypeSpec.annotationBuilder(METHODS)
                .addModifiers(Modifier.PUBLIC);

        final List<String> constants = new ArrayList<>(knownMethods.size());
        final StringBuilder format = new StringBuilder().append("{");

        String prefix = "";

        for (final String name : mappedCalls.keySet()) {
            final Symbol.MethodSymbol ms = mappedCalls.get(name);
            final String methodName = ms.getSimpleName().toString();

            constants.add(METHODS + "." + toConstantName(name));
            format.append(prefix).append("$L");

            builder.addField(FieldSpec.builder(String.class, toConstantName(name))
                    .addJavadoc("{@link #$L($L)}", methodName, composeCallParamsTypes(ms))
                    .addModifiers(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)
                    .initializer("$S", name)
                    .build());

            prefix = ", ";
        }

        format.append("}");

        builder.addAnnotation(AnnotationSpec.builder(StringDef.class)
                .addMember("value", format.toString(), constants.toArray())
                .build());

        return builder;
    }

    @NonNull
    private String composeCallParamsTypes(@NonNull final Symbol.MethodSymbol ms) {
        String delimiter = "";
        final StringBuilder result = new StringBuilder();

        final com.sun.tools.javac.util.List<Symbol.VarSymbol> parameters = ms.getParameters();

        for (int i = 0, len = parameters.size(); i < len; i++) {
            final Symbol.VarSymbol param = parameters.get(i);
            final TypeName paramType = TypeName.get(param.asType());


            result.append(delimiter).append(paramType.toString());
            delimiter = ", ";
        }

        return result.toString();
    }

    @NonNull
    private String composeCallParamsFromArray(@NonNull final Symbol.MethodSymbol ms,
                                              @NonNull final String arrayName) {
        String delimiter = "";
        final StringBuilder result = new StringBuilder();

        final com.sun.tools.javac.util.List<Symbol.VarSymbol> parameters = ms.getParameters();

        for (int i = 0, len = parameters.size(); i < len; i++) {
            final Symbol.VarSymbol param = parameters.get(i);


            final TypeName paramType = TypeName.get(param.asType());
            final String parameterName = param.name.toString();
            final String parameterExtract = String.format(Locale.US, "(%s)%s[%d] /*%s*/", paramType.toString(), arrayName, i, parameterName);


            result.append(delimiter).append(parameterExtract);
            delimiter = ", ";
        }

        return result.toString();
    }


    @NonNull
    protected MethodSpec.Builder createMethod(final Symbol.MethodSymbol ms) throws Exception {


        final String methodName = ms.getSimpleName().toString();
        final MethodSpec.Builder builder = MethodSpec.methodBuilder(methodName);

        builder.addModifiers(Modifier.FINAL, Modifier.PUBLIC);


        mimicMethodAnnotations(builder, ms);
        builder.addAnnotation(Override.class);


        final Attribute.Compound yield = findYieldMethodAnnotation(ms);
        final Attribute.Compound after = withGlobalOverride(findAfterMethodAnnotation(ms));


        final Type returnType = ms.getReturnType();
        final boolean hasReturn = returnType.getKind() != TypeKind.VOID;
        builder.returns(TypeName.get(returnType));


        final StringBuilder arguments = mimicParameters(builder, ms);


        final String uniqueMethodName = methodName + asMethodNamePart(arguments);
        mappedCalls.put(uniqueMethodName, ms);
        knownMethods.add(uniqueMethodName);
        knownMethods.add(methodName);


        mimicThrows(builder, ms);


        builder.beginControlFlow("if (!$L( $L.$L$L ))", PREDICATE,
                METHODS, toConstantName(uniqueMethodName),
                (arguments.length() == 0 ? "" : ", ") + arguments);


        if (hasReturn || null != yield) {
            if (null != yield) builder.addComment("" + yield);
            createYieldPart(builder, returnType, yield);
        } else {

            builder.addStatement("throw new $T($S)", UnsupportedOperationException.class, "cannot resolve return value.");
        }

        builder.endControlFlow();

        final AutoProxy.Yield annotation = extractYield(yield);
        final boolean hasSkipped = Returns.SKIP.equals(annotation.value());


        if (null == after && !hasSkipped) { // no afterCall
            builder.addStatement((hasReturn ? "return " : "") + "this.inner.$N($L)", methodName, arguments);
        } else {
            isAnyAfterCalls.set(true);

            if (hasSkipped) {
                builder.addStatement("final $T forAfterCall = null", returnType);
            } else if (hasReturn) {
                builder.addStatement("final $T forAfterCall = this.inner.$N($L)",
                        returnType, methodName, arguments);
            } else {
                builder.addStatement("final $T forAfterCall = null", Object.class);
                builder.addStatement("this.inner.$N($L)", methodName, arguments);
            }

            builder.addStatement((hasReturn ? "return " : "") + "$L($L.$L, forAfterCall)",
                    AFTER_CALL, METHODS, toConstantName(uniqueMethodName));
        }

        return builder;
    }


    @NonNull
    protected String toConstantName(@NonNull final String name) {
        return name.toUpperCase(Locale.US);
    }


    @NonNull
    private String asMethodNamePart(@NonNull final StringBuilder arguments) {
        return ((arguments.length() > 0) ? "_" : "") + // delimiter
                arguments.toString().replaceAll(", ", "_");
    }

    @Nullable
    private Attribute.Compound withGlobalOverride(@Nullable final Attribute.Compound afterMethodAnnotation) {
        if (null != afterMethodAnnotation) return afterMethodAnnotation;

        final boolean hasAfterCalls = ((this.type.annotation.flags() & Flags.AFTER_CALL) == Flags.AFTER_CALL);

        if (hasAfterCalls) {
            return GLOBAL_AFTER_CALL;
        }

        return null;
    }


    protected void createYieldPart(@NonNull final MethodSpec.Builder builder,
                                   @NonNull final Type returnType,
                                   @Nullable final Attribute.Compound yield) throws Exception {
        // create return based on @Yield annotation values
        final AutoProxy.Yield annotation = extractYield(yield);
        final String value = annotation.value();
        final Class<?> adapter = annotation.adapter();
        final ReturnsPoet poet;

        if (RetBool.class == adapter || RetBoolGenerator.class == adapter) {
            poet = RetBoolGenerator.getInstance();
        } else if (Returns.class == adapter && isRetBoolValue(value)) {
            poet = RetBoolGenerator.getInstance();
        } else if (RetNumber.class == adapter || RetNumberGenerator.class == adapter) {
            poet = RetNumberGenerator.getInstance();
        } else if (Returns.class == adapter && isRetNumberValue(value)) {
            poet = RetNumberGenerator.getInstance();
        } else if (Returns.class == adapter || ReturnsGenerator.class == adapter) {
            poet = ReturnsGenerator.getInstance();
        } else {

            final Constructor<?> ctr = adapter.getConstructor();
            poet = (ReturnsPoet) ctr.newInstance();
        }

        final boolean composed = poet.compose(returnType, value, builder);

        if (!composed) {
            ReturnsGenerator.getInstance().compose(returnType, Returns.THROWS, builder);
        }
    }

    private boolean isRetBoolValue(String value) {
        return RetBool.TRUE.equals(value) || RetBool.FALSE.equals(value);
    }

    private boolean isRetNumberValue(String value) {
        return RetNumber.ZERO.equals(value) || RetNumber.MAX.equals(value) || RetNumber.MIN.equals(value) || RetNumber.MINUS_ONE.equals(value);
    }

    @NonNull
    protected AutoProxy.Yield extractYield(@Nullable final Attribute.Compound yield) throws Exception {

        final Map<String, Object> map = AutoProxy.DefaultYield.asMap();


        if (null != yield) {

            if (IS_DEBUG) type.logger.printMessage(NOTE, "extracting: " + yield.toString());

            for (final Map.Entry<Symbol.MethodSymbol, Attribute> entry : yield.getElementValues().entrySet()) {
                final String key = entry.getKey().name.toString();
                Object value = entry.getValue().getValue();

                if (value instanceof Type.ClassType) {
                    final Name name = ((Type.ClassType) value).asElement().getQualifiedName();

                    value = Class.forName(name.toString());
                }

                map.put(key, value);
            }
        } else {
            if (IS_DEBUG)
                type.logger.printMessage(NOTE, "used global config: " + this.type.annotation.defaultYield());

            map.put("value", this.type.annotation.defaultYield());
        }


        return (AutoProxy.Yield) AnnotationParser.annotationForMap(AutoProxy.Yield.class, map);
    }

    public static void mimicMethodAnnotations(@NonNull final MethodSpec.Builder builder,
                                              @NonNull final Symbol.MethodSymbol ms) throws Exception {
        if (ms.hasAnnotations()) {
            for (final Attribute.Compound am : ms.getAnnotationMirrors()) {
                if (extractClass(am) == AutoProxy.Yield.class) continue;
                if (extractClass(am) == AutoProxy.AfterCall.class) continue;
                if (extractClass(am) == Override.class) continue;

                final AnnotationSpec.Builder builderAnnotation = mimicAnnotation(am);
                if (null != builderAnnotation) {
                    builder.addAnnotation(builderAnnotation.build());
                }
            }
        }
    }

    @Nullable
    public static Attribute.Compound findAfterMethodAnnotation(@NonNull final Symbol.MethodSymbol ms) throws Exception {
        if (ms.hasAnnotations()) {
            for (final Attribute.Compound am : ms.getAnnotationMirrors()) {
                if (extractClass(am) == AutoProxy.AfterCall.class) return am;
            }
        }

        return null;
    }

    @Nullable
    public static Attribute.Compound findYieldMethodAnnotation(@NonNull final Symbol.MethodSymbol ms) throws Exception {
        if (ms.hasAnnotations()) {
            for (final Attribute.Compound am : ms.getAnnotationMirrors()) {
                if (extractClass(am) == AutoProxy.Yield.class) return am;
            }
        }

        return null;
    }


    public static void mimicThrows(@NonNull final MethodSpec.Builder builder,
                                   @NonNull final Symbol.MethodSymbol ms) {
        for (final Type typeThrown : ms.getThrownTypes()) {
            builder.addException(TypeName.get(typeThrown));
        }
    }


    @NonNull
    public static StringBuilder mimicParameters(@NonNull final MethodSpec.Builder builder,
                                                @NonNull final Symbol.MethodSymbol ms) throws Exception {
        String delimiter = "";
        final StringBuilder arguments = new StringBuilder();

        final com.sun.tools.javac.util.List<Symbol.VarSymbol> parameters = ms.getParameters();

        for (int i = 0, len = parameters.size(); i < len; i++) {
            final Symbol.VarSymbol param = parameters.get(i);


            final TypeName paramType = TypeName.get(param.asType());
            final String parameterName = param.name.toString();
            final ParameterSpec.Builder parameter = ParameterSpec.builder(paramType, parameterName, Modifier.FINAL);

            if (param.hasAnnotations()) {

                for (final Attribute.Compound am : param.getAnnotationMirrors()) {
                    final AnnotationSpec.Builder builderAnnotation = mimicAnnotation(am);

                    if (null != builderAnnotation) {
                        parameter.addAnnotation(builderAnnotation.build());
                    }
                }
            }


            builder.varargs(ms.isVarArgs() && i == len - 1);
            builder.addParameter(parameter.build());


            arguments.append(delimiter).append(parameterName);
            delimiter = ", ";
        }

        return arguments;
    }


    @SuppressWarnings("RedundantThrows")
    @Nullable
    public static AnnotationSpec.Builder mimicAnnotation(@NonNull final Attribute.Compound am) throws Exception {
        final Class<?> clazz;

        try {
            clazz = extractClass(am);
            return AnnotationSpec.builder(clazz);
        } catch (final Throwable ignored) {

        }

        return null;
    }


    @NonNull
    public static Class<?> extractClass(@NonNull final Attribute.Compound am) throws ClassNotFoundException {
        final TypeElement te = (TypeElement) am.getAnnotationType().asElement();

        return extractClass(te);
    }


    @NonNull
    public static Class<?> extractClass(@NonNull final TypeElement te) throws ClassNotFoundException {
        final Name name;

        if (te instanceof Symbol.ClassSymbol) {
            final Symbol.ClassSymbol cs = (Symbol.ClassSymbol) te;


            name = cs.flatName();
        } else {
            name = te.getQualifiedName();
        }

        final String className = name.toString();

        try {
            return Class.forName(className).asSubclass(Annotation.class);
        } catch (ClassNotFoundException ex) {

        }

        final int dot = className.lastIndexOf(".");
        final String innerFix2 = className.substring(0, dot) + "$" + className.substring(dot + 1);
        return Class.forName(innerFix2).asSubclass(Annotation.class);
    }
    //endregion
}
