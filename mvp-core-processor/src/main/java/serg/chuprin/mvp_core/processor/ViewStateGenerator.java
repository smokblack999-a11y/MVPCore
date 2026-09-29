package serg.chuprin.mvp_core.processor;

import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ArrayTypeName;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;
import com.squareup.javapoet.TypeSpec;
import com.squareup.javapoet.TypeVariableName;
import com.squareup.javapoet.WildcardTypeName;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import javax.annotation.processing.Filer;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;

import serg.chuprin.mvp_core.annotations.StateStrategyType;
import serg.chuprin.mvp_core.viewstate.MvpViewState;
import serg.chuprin.mvp_core.viewstate.ViewCommand;
import serg.chuprin.mvp_core.viewstate.strategy.AddToEndSingleOneExecutionStrategy;
import serg.chuprin.mvp_core.viewstate.strategy.StateStrategy;

import static java.lang.Character.isAlphabetic;

class ViewStateGenerator {

    private static final String VIEW_STATE_SUFFIX = "$$ViewState";
    private static final String COMMAND_SUFFIX = "Command";
    private static final String EXECUTE_METHOD_NAME = "execute";
    private static final String EXECUTE_COMMAND_METHOD = "executeCommand";
    private static final String VIEW_PARAM = "view";
    private final Filer filer;
    private final Types typeUtils;
    private final TypeElement viewElement;
    private final ClassName viewInterface;
    private final ClassName viewCommandName;
    private final Class<? extends StateStrategy> defaultStrategy;

    ViewStateGenerator(TypeElement viewElem, Filer filer, Types typeUtils) {
        this.filer = filer;
        this.typeUtils = typeUtils;

        viewElement = viewElem;
        viewInterface = ClassName.get(viewElement);
        viewCommandName = ClassName.get(ViewCommand.class);
        defaultStrategy = AddToEndSingleOneExecutionStrategy.class;
    }

    boolean generate() {
        String stateName = String.format("%s%s", viewElement.getSimpleName(), VIEW_STATE_SUFFIX);
        List<InterfaceMethods> allMethods = getAllMethods();
        List<TypeVariableName> typeVariables = getInterfaceTypeVariables(viewElement);

        TypeSpec stateClass = TypeSpec.classBuilder(stateName)
                .superclass(ParameterizedTypeName.get(ClassName.get(MvpViewState.class), viewInterface))
                .addSuperinterface(getGenericSuperinterface(typeVariables))
                .addTypeVariables(typeVariables)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addTypes(createInnerCommandClasses(allMethods))
                .addMethods(createViewMethods(allMethods))
                .build();


        String packageName = ClassName.bestGuess(viewElement.toString()).packageName();

        try {
            JavaFile.builder(packageName, stateClass).build().writeTo(filer);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    String getClassName() {
        return viewElement.toString() + VIEW_STATE_SUFFIX;
    }

    private TypeName getGenericSuperinterface(List<TypeVariableName> typeVariables) {
        if (typeVariables.isEmpty()) {
            return viewInterface;
        }
        TypeName[] typeNames = new TypeName[typeVariables.size()];

        for (int i = 0; i < typeVariables.size(); i++) {
            typeNames[i] = typeVariables.get(i).withoutAnnotations();
        }
        return ParameterizedTypeName.get(viewInterface, typeNames);
    }

    private Iterable<TypeSpec> createInnerCommandClasses(List<InterfaceMethods> allMethods) {


        List<TypeSpec> classes = new ArrayList<>();

        for (InterfaceMethods interfaceMethods : allMethods) {
            Element element = ((DeclaredType) interfaceMethods.getViewInterface()).asElement();

            Class<? extends StateStrategy> viewStrategy = getElemStrategyOrDefault(element, defaultStrategy);
            for (Method method : interfaceMethods.getMethods()) {
                classes.add(createCommandClass(method,
                        getElemStrategyOrDefault(method.getExecutableElement(), viewStrategy),
                        interfaceMethods.getTypesMap()));
            }
        }
        return classes;
    }

    private TypeSpec createCommandClass(Method method,
                                        Class<? extends StateStrategy> strategy,
                                        Map<String, String> typesMap) {

        ExecutableElement executableElement = method.getExecutableElement();
        Iterable<ParameterSpec> methodParams = getMethodParams(executableElement, typesMap);

        return TypeSpec.classBuilder(getCommandName(method))
                .superclass(ParameterizedTypeName.get(viewCommandName, viewInterface))
                .addTypeVariables(getMethodTypeVariables(executableElement, typesMap))
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addFields(createCommandFields(methodParams))
                .addMethod(createCommandConstructor(executableElement, methodParams, strategy))
                .addMethod(createExecuteMethod(executableElement, methodParams))
                .build();
    }

    private String getCommandName(Method method) {
        String name = method.getUniqueName();
        return name.substring(0, 1).toUpperCase() + name.substring(1) + COMMAND_SUFFIX;
    }

    private Iterable<FieldSpec> createCommandFields(Iterable<ParameterSpec> methodParams) {
        List<FieldSpec> fields = new ArrayList<>();

        for (ParameterSpec param : methodParams) {
            fields.add(FieldSpec.builder(
                    param.type,
                    param.name,
                    Modifier.FINAL,
                    Modifier.PUBLIC)
                    .addAnnotations(param.annotations)
                    .build());
        }

        return fields;
    }

    private MethodSpec createExecuteMethod(ExecutableElement method, Iterable<ParameterSpec> methodParams) {
        return MethodSpec.methodBuilder(EXECUTE_METHOD_NAME)
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addParameter(viewInterface, VIEW_PARAM)
                .addStatement("$N.$N($N)",
                        VIEW_PARAM,
                        method.getSimpleName(),
                        getMethodParamsForCaller(methodParams))
                .build();
    }

    private StringBuilder getMethodParamsForCaller(Iterable<ParameterSpec> methodParams) {
        StringBuilder paramsStr = new StringBuilder();

        for (Iterator<ParameterSpec> iterator = methodParams.iterator(); iterator.hasNext(); ) {

            paramsStr.append(iterator.next().name).append(iterator.hasNext() ? "," : "");
        }
        return paramsStr;
    }

    private MethodSpec createCommandConstructor(ExecutableElement method,
                                                Iterable<ParameterSpec> methodParams,
                                                Class<? extends StateStrategy> strategy) {
        MethodSpec.Builder builder = MethodSpec.constructorBuilder()
                .varargs(method.isVarArgs())
                .addModifiers(Modifier.PUBLIC)
                .addStatement("super(new $T())", strategy)
                .addParameters(methodParams);

        for (ParameterSpec param : methodParams) {
            builder.addStatement("this.$N = $N", param.name, param.name);
        }
        return builder.build();
    }

    private Iterable<MethodSpec> createViewMethods(List<InterfaceMethods> allMethods) {
        List<MethodSpec> methods = new ArrayList<>();

        for (InterfaceMethods interfaceMethods : allMethods) {

            for (Method method : interfaceMethods.getMethods()) {
                methods.add(createViewMethod(method, interfaceMethods.getTypesMap()));
            }
        }
        return methods;
    }

    private MethodSpec createViewMethod(Method method, Map<String, String> typesMap) {
        ExecutableElement executableElement = method.getExecutableElement();
        Iterable<ParameterSpec> methodParams = getMethodParams(executableElement, typesMap);

        CodeBlock code = CodeBlock.builder()
                .addStatement("$N(new " + getCommandName(method) + "($N))",
                        EXECUTE_COMMAND_METHOD,
                        getMethodParamsForCaller(methodParams).toString())
                .build();

        return MethodSpec.methodBuilder(executableElement.getSimpleName().toString())
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .addExceptions(getThrownTypes(executableElement))
                .varargs(executableElement.isVarArgs())
                .addParameters(methodParams)
                .addTypeVariables(getMethodTypeVariables(executableElement, typesMap))
                .returns(ClassName.get(executableElement.getReturnType()))
                .addCode(code)
                .build();
    }

    private List<TypeName> getThrownTypes(ExecutableElement method) {
        List<TypeName> exceptions = new ArrayList<>();

        for (TypeMirror thrownType : method.getThrownTypes()) {
            exceptions.add(TypeName.get(thrownType));
        }
        return exceptions;
    }

    private List<TypeVariableName> getMethodTypeVariables(ExecutableElement method, Map<String, String> typesMap) {
        List<TypeVariableName> typeVariableNames = new ArrayList<>();

        for (TypeParameterElement typeParam : method.getTypeParameters()) {

            List<? extends TypeMirror> typeBounds = typeParam.getBounds();

            TypeName[] resolvedBounds = new TypeName[typeBounds.size()];

            for (int i = 0; i < typeBounds.size(); i++) {
                resolvedBounds[i] = inferTypeName(typeBounds.get(i), typesMap);
            }
            typeVariableNames.add(TypeVariableName.get(typeParam.toString(), resolvedBounds));
        }
        return typeVariableNames;
    }

    private List<TypeVariableName> getInterfaceTypeVariables(TypeElement element) {
        List<TypeVariableName> typeVariableNames = new ArrayList<>();

        for (TypeParameterElement typeParam : element.getTypeParameters()) {
            typeVariableNames.add(TypeVariableName.get(typeParam));
        }
        return typeVariableNames;
    }

    private Iterable<ParameterSpec> getMethodParams(ExecutableElement method, Map<String, String> typesMap) {
        List<ParameterSpec> params = new ArrayList<>();

        for (VariableElement parameter : method.getParameters()) {
            TypeName inferTypeName = inferTypeName(parameter.asType(), typesMap);
            params.add(ParameterSpec.builder(
                    inferTypeName,
                    parameter.getSimpleName().toString())
                    .addAnnotations(getMethodParamAnnotations(parameter))
                    .build());
        }
        return params;
    }

    private List<AnnotationSpec> getMethodParamAnnotations(VariableElement parameter) {
        List<AnnotationSpec> annotations = new ArrayList<>();

        for (AnnotationMirror annotationMirror : parameter.getAnnotationMirrors()) {
            annotations.add(AnnotationSpec.get(annotationMirror));
        }
        return annotations;
    }

    @SuppressWarnings("unchecked")
    private Class<? extends StateStrategy> getElemStrategyOrDefault(
            Element element,
            Class<? extends StateStrategy> fallbackStrategy) {
        try {
            StateStrategyType annotation = element.getAnnotation(StateStrategyType.class);
            if (annotation != null) {
                annotation.value();
            }
        } catch (MirroredTypeException mte) {
            try {
                return (Class<? extends StateStrategy>) Class.forName(mte.getTypeMirror().toString());
            } catch (ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
        return fallbackStrategy;
    }

    /**
     * non-recursive getting methods from all super interfaces
     */
    private List<InterfaceMethods> getAllMethods() {
        Map<TypeMirror, InterfaceMethods> methodsMap = new LinkedHashMap<>();

        Deque<TypeMirror> elements = new ArrayDeque<>(Collections.singletonList(viewElement.asType()));

        TypeMirror previousInterface = null;

        while (!elements.isEmpty()) {

            TypeMirror anInterface = elements.poll();
            TypeElement interfaceType = (TypeElement) typeUtils.asElement(anInterface);

            methodsMap.put(anInterface, new InterfaceMethods(
                    anInterface,
                    getInterfaceMethods(interfaceType),
                    createInterfaceTypeArgsMapping(methodsMap, anInterface, previousInterface)));

            for (TypeMirror interfaceMirror : interfaceType.getInterfaces()) {
                elements.addFirst(interfaceMirror);
            }
            previousInterface = anInterface;
        }

        Collection<InterfaceMethods> values = methodsMap.values();
        Map<String, Integer> methodsCounter = new HashMap<>(values.size());

        for (InterfaceMethods methods : values) {

            for (Method method : methods.getMethods()) {

                String methodName = method.getExecutableElement().getSimpleName().toString();

                Integer counter = methodsCounter.get(methodName);
                if (counter == null) {
                    counter = 0;
                } else {
                    ++counter;
                    method.setUniqueName(method.getUniqueName() + counter);
                }
                methodsCounter.put(methodName, counter);
            }
        }
        return new ArrayList<>(values);
    }

    private Map<String, String> createInterfaceTypeArgsMapping(Map<TypeMirror, InterfaceMethods> methodsMap,
                                                               TypeMirror curInterface,
                                                               TypeMirror prevInterface) {

        InterfaceMethods interfaceMethods = methodsMap.get(prevInterface);
        if (interfaceMethods == null) {
            return Collections.emptyMap();
        }
        return replaceWithParentMapping(getInterfaceTypeMapping(curInterface), interfaceMethods.getTypesMap());
    }

    /**
     * @return Map<String , String> contains generic types mapping to arguments. I.e S -> String
     */
    private Map<String, String> getInterfaceTypeMapping(TypeMirror interfaceMirror) {
        DeclaredType declaredType = (DeclaredType) interfaceMirror;

        List<? extends TypeMirror> typeArguments = declaredType.getTypeArguments();
        if (typeArguments.isEmpty()) {
            return Collections.emptyMap();
        }

        TypeElement element = (TypeElement) declaredType.asElement();
        List<? extends TypeParameterElement> typeParameters = element.getTypeParameters();

        Map<String, String> typesMapping = new LinkedHashMap<>(typeArguments.size());
        for (int i = 0; i < typeArguments.size(); i++) {
            typesMapping.put(typeParameters.get(i).toString(), typeArguments.get(i).toString());
        }
        return typesMapping;
    }

    /**
     * @param currentMapping current interface mapping.
     *                       Contains generic types and args. I.e S -> P
     * @param parentMapping  Parent interface mapping.
     *                       Contains resolved args. I.e P -> String
     * @return current mapping without unresolved args.
     * So they are replaced to resolved args from parent mapping
     */
    private Map<String, String> replaceWithParentMapping(Map<String, String> currentMapping,
                                                         Map<String, String> parentMapping) {


        Map<String, String> argsToAdd = new LinkedHashMap<>();

        for (Map.Entry<String, String> entry : currentMapping.entrySet()) {

            String parentArg = parentMapping.get(entry.getValue());
            if (parentArg != null) {
                argsToAdd.put(entry.getKey(), parentArg);
                continue;
            }
            String genericMapping = entry.getValue();

            List<ReplacementBundle> replacementBundles = new ArrayList<>();

            for (Map.Entry<String, String> arg : parentMapping.entrySet()) {
                replacementBundles.addAll(findGenericTypes(genericMapping, arg.getKey(), arg.getValue()));
            }
            if (!replacementBundles.isEmpty()) {
                String inferredGeneric = replaceGenericWithArgs(genericMapping, replacementBundles);
                argsToAdd.put(genericMapping, inferredGeneric);
                argsToAdd.put(entry.getKey(), inferredGeneric);
            }
        }
        currentMapping.putAll(argsToAdd);
        return currentMapping;
    }

    /**
     * @param mirror   toString() gives: android.util.Pair<MODEL,API>
     * @param typesMap map with types and their arguments. For example: MODEL -> java.lang.Boolean
     * @return TypeName which represents type with resolved generic types.
     * typesMap: MODEL -> java.lang.Boolean, API -> serg.chuprin.sample.common.model.User
     * Input: android.util.Pair<MODEL,API>
     * Output: android.util.Pair<java.lang.Boolean,serg.chuprin.sample.common.model.User>
     */
    private TypeName inferTypeName(TypeMirror mirror, Map<String, String> typesMap) {

        String typeStr = mirror.toString();

        List<ReplacementBundle> replacementBundles = new ArrayList<>();

        for (Map.Entry<String, String> entry : typesMap.entrySet()) {

            if (typeStr.equals(entry.getKey())) {
                return parseTypeName(entry.getValue());
            }
            replacementBundles.addAll(findGenericTypes(typeStr, entry.getKey(), entry.getValue()));
        }
        if (!replacementBundles.isEmpty()) {
            return parseTypeName(replaceGenericWithArgs(typeStr, replacementBundles));
        }
        return TypeName.get(mirror);
    }

    /**
     * @param typeStr string which represents the type: android.util.Pair<MODEL,T>
     * @param arg     generic type: MODEL
     * @param value   generic type argument to replace: java.lang.Boolean
     */
    private TypeName parseTypeName(String type) {
        String value = type.trim();

        if (value.endsWith("[]")) {
            return ArrayTypeName.of(parseTypeName(value.substring(0, value.length() - 2)));
        }

        if ("boolean".equals(value)) return TypeName.BOOLEAN;
        if ("byte".equals(value)) return TypeName.BYTE;
        if ("char".equals(value)) return TypeName.CHAR;
        if ("double".equals(value)) return TypeName.DOUBLE;
        if ("float".equals(value)) return TypeName.FLOAT;
        if ("int".equals(value)) return TypeName.INT;
        if ("long".equals(value)) return TypeName.LONG;
        if ("short".equals(value)) return TypeName.SHORT;
        if ("void".equals(value)) return TypeName.VOID;

        if (value.startsWith("? extends ")) {
            return WildcardTypeName.subtypeOf(parseTypeName(value.substring("? extends ".length())));
        }
        if (value.startsWith("? super ")) {
            return WildcardTypeName.supertypeOf(parseTypeName(value.substring("? super ".length())));
        }
        if ("?".equals(value)) {
            return WildcardTypeName.subtypeOf(TypeName.OBJECT);
        }

        int genericStart = value.indexOf('<');
        if (genericStart == -1 || !value.endsWith(">")) {
            return ClassName.bestGuess(value);
        }

        String rawType = value.substring(0, genericStart).trim();
        String arguments = value.substring(genericStart + 1, value.length() - 1);

        List<TypeName> typeArguments = new ArrayList<>();
        int depth = 0;
        int start = 0;

        for (int i = 0; i < arguments.length(); i++) {
            char ch = arguments.charAt(i);
            if (ch == '<') depth++;
            else if (ch == '>') depth--;
            else if (ch == ',' && depth == 0) {
                typeArguments.add(parseTypeName(arguments.substring(start, i)));
                start = i + 1;
            }
        }

        if (start < arguments.length()) {
            typeArguments.add(parseTypeName(arguments.substring(start)));
        }

        TypeName raw = parseTypeName(rawType);
        if (!(raw instanceof ClassName)) {
            throw new IllegalArgumentException("Parameterized type must have a class name: " + value);
        }

        return ParameterizedTypeName.get(
                (ClassName) raw,
                typeArguments.toArray(new TypeName[0]));
    }

    private List<ReplacementBundle> findGenericTypes(String typeStr, String arg, String value) {
        List<ReplacementBundle> bundles = new LinkedList<>();

        if (arg.length() == 1) {

            char genericArg = arg.charAt(0);

            for (int i = 1; i < typeStr.length() - 1; ++i) {
                if (typeStr.charAt(i) == genericArg && isGenericType(typeStr, i, i)) {
                    bundles.add(new ReplacementBundle(i, String.valueOf(genericArg), value));
                }
            }
        } else {
            int lastIndex = 0;

            while (lastIndex != -1) {
                int startIndex = typeStr.indexOf(arg, lastIndex);

                if (startIndex == -1) {
                    break;
                }
                lastIndex = startIndex + arg.length() - 1;

                if (startIndex > 0 && lastIndex < typeStr.length()
                        && isGenericType(typeStr, startIndex, lastIndex)) {
                    bundles.add(new ReplacementBundle(startIndex, arg, value));
                }
            }
        }
        return bundles;
    }

    private boolean isGenericType(String typeStr, int startIndex, int lastIndex) {
        return !isAlphabetic(typeStr.charAt(startIndex - 1))
                && !isAlphabetic(typeStr.charAt(lastIndex + 1));
    }

    private String replaceGenericWithArgs(String typeStr, List<ReplacementBundle> replacementBundles) {
        StringBuilder builder = new StringBuilder(typeStr);

        int offset = 0;
        for (ReplacementBundle bundle : replacementBundles) {
            int startIndex = offset + bundle.getStartIndex();

            int typeLength = bundle.getGenericType().length();

            builder.replace(startIndex, startIndex + typeLength, bundle.getGenericTypeArg());
            offset = bundle.getGenericTypeArg().length() - typeLength;
        }
        return builder.toString();
    }

    private List<Method> getInterfaceMethods(TypeElement anInterface) {
        List<Method> methods = new ArrayList<>();

        for (Element element : anInterface.getEnclosedElements()) {

            if (element.getKind() == ElementKind.METHOD) {

                ExecutableElement method = (ExecutableElement) element;
                if (method.getReturnType().getKind() == TypeKind.VOID) {
                    methods.add(new Method(method, method.getSimpleName().toString()));
                } else {
                    MvpProcessor.error(method, "In canonical MVP all view methods should be 'void': ",
                            method.getSimpleName().toString());
                }
            }
        }
        return methods;
    }
}
