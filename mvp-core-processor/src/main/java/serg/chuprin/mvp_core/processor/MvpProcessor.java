package serg.chuprin.mvp_core.processor;

import com.google.auto.service.AutoService;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.TypeParameterElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import javax.tools.Diagnostic;

import serg.chuprin.mvp_core.MvpPresenter;
import serg.chuprin.mvp_core.annotations.InjectViewState;
import serg.chuprin.mvp_core.view.MvpView;

/**
 * Generates MVPCore ViewState/ViewStateProvider sources.
 *
 * Important: this processor must not claim javax.inject.Inject. Dagger's
 * ComponentProcessor also consumes javax.inject annotations, so claiming them
 * here prevents Dagger from receiving the annotations it needs to generate
 * Dagger* component implementations.
 */
@AutoService(Processor.class)
@SuppressWarnings("WeakerAccess")
@SupportedAnnotationTypes("serg.chuprin.mvp_core.annotations.InjectViewState")
public class MvpProcessor extends AbstractProcessor {

    private static Messager messager;
    private static Types typeUtils;
    private Filer filer;
    private Elements elemUtils;
    private boolean providerGenerated;

    static void error(Element element, String message, Object... args) {
        message(Diagnostic.Kind.ERROR, element, message, args);
    }

    private static void warning(Element element, String message, Object... args) {
        message(Diagnostic.Kind.WARNING, element, message, args);
    }

    private static void message(Diagnostic.Kind kind, Element element, String message, Object... args) {
        if (element == null) {
            messager.printMessage(kind, String.format(message, args));
        } else {
            messager.printMessage(kind, String.format(message, args), element);
        }
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        typeUtils = processingEnv.getTypeUtils();
        messager = processingEnv.getMessager();
        filer = processingEnv.getFiler();
        elemUtils = processingEnv.getElementUtils();
    }

    @Override
    public boolean process(Set<? extends TypeElement> set, RoundEnvironment roundEnv) {
        List<Pair<TypeElement, String>> presenterViewParis = new ArrayList<>();
        List<String> generatedViewStates = new ArrayList<>();

        for (Element annotatedElem : roundEnv.getElementsAnnotatedWith(InjectViewState.class)) {

            if (annotatedElem.getKind().isInterface()) {
                error(annotatedElem, "Only classes can be annotated with @%s",
                        InjectViewState.class.getSimpleName());
                return true;
            }

            TypeElement presenterType = (TypeElement) annotatedElem;

            if (!Utils.isSubclass(typeUtils, presenterType, MvpPresenter.class)) {
                error(annotatedElem, "Cannot inject MvpViewState in class which are not child of MvpPresenter");
                return true;
            }

            TypeElement viewType = getViewType(presenterType);
            if (viewType == null) {
                error(presenterType,
                        "Your presenter is typed. Please specify your view in annotation",
                        presenterType);
                return true;
            }
            ViewStateGenerator generator = new ViewStateGenerator(
                    viewType,
                    filer,
                    typeUtils
            );

            String viewStateClassName = generator.getClassName();

            if (!generatedViewStates.contains(viewStateClassName)) {
                if (!generator.generate()) {
                    error(annotatedElem, "Failed to generate viewState: " + viewStateClassName);
                    return true;
                }

                generatedViewStates.add(viewStateClassName);
            }
            presenterViewParis.add(new Pair<>(presenterType, viewStateClassName));
        }
        if (!providerGenerated) {

            if (!new ViewStateBinderGenerator(filer, presenterViewParis).generate()) {
                error(null, "Failed to generate MvpViewState provider class");
                return true;
            }
            providerGenerated = true;
        }
        return true;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    private TypeElement getViewType(TypeElement presenterType) {

        TypeElement viewType = null;

        try {
            presenterType.getAnnotation(InjectViewState.class).view();
        } catch (MirroredTypeException mte) {
            TypeMirror typeMirror = mte.getTypeMirror();
            if (!typeMirror.toString().contains(MvpView.class.getName())) {
                viewType = (TypeElement) typeUtils.asElement(typeMirror);
            }
        }

        TypeElement viewFromType = getViewFromPresenterTypeParams(presenterType);
        TypeElement viewFromSuperclass = getViewFromPresenterSuperclassTypeArg(presenterType);

        if (viewFromType != null && viewType == null) {
            warning(presenterType,
                    "Your presenter is typed. Mvp-core will try to retrieve your view from type params.\n" +
                            " Better specify your view in annotation", presenterType);
        }

        if (viewType != null) {

            if (viewFromType != null) {
                checkViews(presenterType, viewType, viewFromType);
            }

            if (viewFromSuperclass != null) {
                checkViews(presenterType, viewType, viewFromSuperclass);
            }
            return viewType;
        }
        return viewFromType == null ? viewFromSuperclass : viewFromType;
    }

    private void checkViews(TypeElement presenter, TypeElement view1, TypeElement view2) {
        if (!typeUtils.isSubtype(view1.asType(), view2.asType())) {
            showViewNotSubclassError(presenter, view1, view2);
        }
    }

    /**
     * @param presenterType typeElement of presenter
     * @return TypeElement for View : MvpView or null if not found
     */
    private TypeElement getViewFromPresenterTypeParams(TypeElement presenterType) {

        for (TypeParameterElement typeParam : presenterType.getTypeParameters()) {
            for (TypeMirror bound : typeParam.getBounds()) {

                Element possibleViewType = typeUtils.asElement(bound);
                if (possibleViewType != null && possibleViewType.toString().contains(MvpView.class.getName())) {
                    return (TypeElement) possibleViewType;
                }
            }
        }
        return null;
    }

    /**
     * @param presenter typeElement of presenter
     * @return TypeElement for View : MvpView or null if not found
     */
    private TypeElement getViewFromPresenterSuperclassTypeArg(TypeElement presenter) {

        DeclaredType superclass = (DeclaredType) presenter.getSuperclass();

        for (TypeMirror arg : superclass.getTypeArguments()) {

            Element possibleView = typeUtils.asElement(arg);
            if (possibleView != null && possibleView instanceof TypeElement
                    && Utils.isImplementingInterface(elemUtils, typeUtils, (TypeElement) possibleView, MvpView.class)) {
                return (TypeElement) possibleView;
            }
        }
        return null;
    }

    private void showViewNotSubclassError(TypeElement presenter, TypeElement element1, TypeElement element2) {
        error(presenter,
                "You specified view in annotation: " + element1.getSimpleName().toString()
                        + " but it's not subclass of view specified in presenter's parameter: "
                        + element2.getSimpleName().toString()
                , presenter);
    }

}
