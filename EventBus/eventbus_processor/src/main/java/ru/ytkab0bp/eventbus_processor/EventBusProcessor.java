package ru.ytkab0bp.eventbus_processor;

import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.FieldSpec;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeSpec;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.FilerException;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;

import ru.ytkab0bp.eventbus.Event;
import ru.ytkab0bp.eventbus.EventBusCompatConsumer;
import ru.ytkab0bp.eventbus.EventBusListenerImpl;
import ru.ytkab0bp.eventbus.EventHandler;

public class EventBusProcessor extends AbstractProcessor {
    private final static String IMPL_MAP = "objMap", PARENT_MAP = "parentMap";

    private ProcessingEnvironment processingEnvironment;

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        processingEnvironment = processingEnv;
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.getRootElements().isEmpty())
            return true;
        String pkg = null;
        for (Element el : roundEnv.getRootElements()) {
            String str = el.toString();
            if (str.endsWith(".BuildConfig")) {
                pkg = str.substring(0, str.lastIndexOf('.'));
                break;
            }
        }
        if (pkg == null) {
            return true;
        }

        CodeBlock.Builder staticBlock = CodeBlock.builder();
        MethodSpec.Builder canFireParent = MethodSpec.methodBuilder("canFireParent")
                .returns(boolean.class)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(Class.class, "clz")
                .addAnnotation(Override.class)
                .addCode("return " + PARENT_MAP + ".contains(clz);");

        for (Element el : roundEnv.getElementsAnnotatedWith(Event.class)) {
            Event e = el.getAnnotation(Event.class);
            if (e.canFireParent()) {
                staticBlock.add(PARENT_MAP + ".add(" + el + ".class);\n");
            }
        }

        TypeSpec.Builder tb = TypeSpec.classBuilder(pkg.replace(".", "_Z9_"))
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL)
                .addSuperinterface(EventBusListenerImpl.class)
                .addMethod(canFireParent.build())
                .addField(FieldSpec.builder(ParameterizedTypeName.get(Map.class, Class.class, EventBusCompatConsumer.class),
                        IMPL_MAP, Modifier.PRIVATE, Modifier.FINAL, Modifier.STATIC)
                        .initializer("new java.util.HashMap<>()")
                        .build())
                .addField(FieldSpec.builder(ParameterizedTypeName.get(List.class, Class.class),
                        PARENT_MAP, Modifier.PRIVATE, Modifier.FINAL, Modifier.STATIC)
                        .initializer("new java.util.ArrayList<>()")
                        .build());

        MethodSpec.Builder m = MethodSpec.methodBuilder("onEvent")
                .addModifiers(Modifier.PUBLIC)
                .addParameter(Object.class, "obj")
                .addParameter(Object.class, "event")
                .addAnnotation(Override.class);

        HashMap<String, CodeBlock.Builder> classData = new HashMap<>();

        for (Element e : roundEnv.getElementsAnnotatedWith(EventHandler.class)) {
            Element clzEl = e.getEnclosingElement();
            String clz = clzEl.toString();
            CodeBlock.Builder bl = classData.computeIfAbsent(clz, k -> CodeBlock.builder());

            EventHandler eh = e.getAnnotation(EventHandler.class);

            String mNameRaw = e.toString();
            String event = mNameRaw.substring(mNameRaw.indexOf('(') + 1, mNameRaw.indexOf(')'));
            CantProcessReason cantProcessReason;
            if (!e.getModifiers().contains(Modifier.PUBLIC) || e.getModifiers().contains(Modifier.STATIC))
                cantProcessReason = CantProcessReason.METHOD_ACCESS;
            else if (!clzEl.getModifiers().contains(Modifier.PUBLIC))
                cantProcessReason = CantProcessReason.CLASS_ACCESS;
            else if (event.isEmpty())
                cantProcessReason = CantProcessReason.NOT_ENOUGH_ARGUMENTS;
            else if (event.replaceAll("[^,]", "").length() > 0)
                cantProcessReason = CantProcessReason.TOO_MANY_ARGUMENTS;
            else cantProcessReason = null;

            if (cantProcessReason != null) {
                System.err.println("[!] EventBus can not process this method: " + clz + "@" + e + ", reason: " + cantProcessReason.reason);
                continue;
            }

            try {
                String mName = e.toString().substring(0, e.toString().lastIndexOf('('));

                bl.add("if (ru.ytkab0bp.eventbus.EventBus.canFire(" + event + ".class, event)) {\n");

                if (eh.runOnMainThread()) {
                    bl.add("ru.ytkab0bp.eventbus.EventBus.postOnUiThread(()->{\n");
                }

                bl.add(clz + " cObj = (" + clz + ") obj;\n");
                bl.add("cObj." + mName + "((" + event + ") event);\n");

                if (eh.runOnMainThread()) {
                    bl.add("});\n");
                }

                bl.add("}\n");
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
        for (Map.Entry<String, CodeBlock.Builder> en : classData.entrySet()) {
            staticBlock.add(IMPL_MAP + ".put(" + en.getKey() + ".class, (obj,event) -> {\n");
            staticBlock.add(en.getValue().build());
            staticBlock.add("});\n");
        }
        m.addCode("for (java.util.Map.Entry<Class, EventBusCompatConsumer> en : " + IMPL_MAP + ".entrySet()) {\n");
        m.addCode("if (en.getKey().isInstance(obj)) en.getValue().onEvent(obj, event);\n");
        m.addCode("}\n");

        tb.addMethod(m.build());
        tb.addStaticBlock(staticBlock.build());

        try {
            JavaFile jf = JavaFile.builder("ru.ytkab0bp.eventbus.impl", tb.build()).build();
            jf.writeTo(processingEnvironment.getFiler());
        } catch (FilerException ignored) {
        } catch (IOException e) {
            e.printStackTrace();
        }
        return true;
    }

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> s = new HashSet<>();
        s.add(Event.class.getCanonicalName());
        s.add(EventHandler.class.getCanonicalName());
        return s;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.RELEASE_8;
    }

    private enum CantProcessReason {
        NOT_ENOUGH_ARGUMENTS("Not enough arguments!"),
        TOO_MANY_ARGUMENTS("Too many arguments!"),
        CLASS_ACCESS("Class access is not public!"),
        METHOD_ACCESS("Method access is not public!");

        final String reason;

        CantProcessReason(String r) {
            reason = r;
        }
    }
}