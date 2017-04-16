package org.javacs;

import com.sun.javadoc.MethodDoc;
import com.sun.javadoc.Parameter;
import com.sun.javadoc.ProgramElementDoc;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Type;
import java.util.Arrays;
import java.util.stream.Collectors;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import org.eclipse.lsp4j.Hover;
import org.eclipse.lsp4j.MarkedString;
import org.eclipse.lsp4j.jsonrpc.messages.Either;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.function.Function;

public class Hovers {
    
    public static Hover hoverText(Element el) {
        String content = Javadocs.global().doc(el)
            .map(Hovers::javadocHover)
            .orElseGet(() -> fallbackHover(el));

        return new Hover(
                Collections.singletonList(Either.forLeft(content)),
                null
        );
    }

    /**
     * Hover text if we found element on the source path and we can use the Doclet API
     */
    private static String javadocHover(ProgramElementDoc doc) {
        if (doc instanceof MethodDoc) {
            MethodDoc method = (MethodDoc) doc;

            return String.format(
                "```java\n%s\n```\n%s", 
                method.returnType().toString() + " " + method.name() + "(" + docParams(method.parameters()) + ")",
                Javadocs.commentText(method).orElse("")
            );
        }
        else return String.format(
            "```java\n%s\n```\n%s", 
            doc.qualifiedName(),
            doc.commentText()
        );
    }

    private static String docParams(Parameter[] params) {
        return Arrays.stream(params)
            .map(Hovers::docParam)
            .collect(Collectors.joining(", "));
    }

    private static String docParam(Parameter param) {
        return param.type().toString() + " " + param.name();
    }

    /**
     * Hover text if we can't find `el` on the source path
     */
    private static String fallbackHover(Element el) {
        // These strings are intentionally not formatted as ```java name ``, 
        // so that they appear less "rich" in the UI remind the user know that they could be improved by adding sourcePath
        
        if (el.getKind() == ElementKind.CONSTRUCTOR) {
            ExecutableElement method = (ExecutableElement) el;

            return method.getEnclosingElement().getSimpleName() + "(" + params(method.getParameters()) + ")";
        }
        if (el instanceof ExecutableElement) {
            ExecutableElement method = (ExecutableElement) el;

            return method.getReturnType() + " " + method.getSimpleName() + "(" + params(method.getParameters()) + ")";
        }        
        else if (el instanceof TypeElement) {
            TypeElement type = (TypeElement) el;

            return type.getQualifiedName().toString();
        }
        else return el.asType().toString();
    }

    private static String params(List<? extends VariableElement> params) {
        return params.stream()
            .map(p -> p.asType().toString())
            .collect(Collectors.joining(", "));
    }
    
    public static String methodSignature(ExecutableElement e, boolean showReturn, boolean showMethodName) {
        String name = e.getKind() == ElementKind.CONSTRUCTOR ? constructorName(e) : e.getSimpleName().toString();
        boolean varargs = e.isVarArgs();
        StringJoiner params = new StringJoiner(", ");

        List<? extends VariableElement> parameters = e.getParameters();
        for (int i = 0; i < parameters.size(); i++) {
            VariableElement p = parameters.get(i);
            String pName = shortName(p, varargs && i == parameters.size() - 1);

            params.add(pName);
        }

        String signature = "";
        
        if (showReturn)
            signature += ShortTypePrinter.print(e.getReturnType()) + " ";

        if (showMethodName)
            signature += name;

        signature += "(" + params + ")";

        if (!e.getThrownTypes().isEmpty()) {
            StringJoiner thrown = new StringJoiner(", ");

            for (TypeMirror t : e.getThrownTypes())
                thrown.add(ShortTypePrinter.print(t));

            signature += " throws " + thrown;
        }

        return signature;
    }

    public static String shortName(VariableElement p, boolean varargs) {
        TypeMirror type = p.asType();

        if (varargs) {
            Type.ArrayType array = (Type.ArrayType) type;

            type = array.getComponentType();
        }

        String acc = shortTypeName(type);
        String name = p.getSimpleName().toString();

        if (varargs)
            acc += "...";

        if (!name.matches("arg\\d+"))
            acc += " " + name;

        return acc;
    }

    private static String shortTypeName(TypeMirror type) {
        return ShortTypePrinter.print(type);
    }

    private static String constructorName(ExecutableElement e) {
        return e.getEnclosingElement().getSimpleName().toString();
    }
}
