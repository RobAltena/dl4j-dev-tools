package org.nd4j.codegen.impl.java;

import com.squareup.javapoet.CodeBlock;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.TypeSpec;
import org.apache.commons.io.FileUtils;
import org.nd4j.base.Preconditions;
import org.nd4j.codegen.api.*;
import org.nd4j.codegen.api.doc.DocSection;
import org.nd4j.codegen.api.doc.DocTokens;
import org.nd4j.codegen.api.generator.ConstraintCodeGenerator;
import org.nd4j.codegen.api.generator.GeneratorConfig;
import org.nd4j.codegen.util.GenUtil;
import org.nd4j.linalg.factory.NDValidation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.factory.Nd4j;
import org.nd4j.linalg.indexing.conditions.Condition;

import javax.lang.model.element.Modifier;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

public class Nd4jNamespaceGenerator {
    private static Map<DataType, Class> typeMapping = new HashMap<>();
    private static Map<DataType, Class> arrayTypeMapping = new HashMap<>();
    private static Map<DataType, String> validationMapping = new HashMap<>();
    private static Count exactlyOne = new Exactly(1);

    static {
        typeMapping.put(DataType.BOOL, boolean.class);
        typeMapping.put(DataType.FLOATING_POINT, double.class);
        typeMapping.put(DataType.NUMERIC, double.class);
        typeMapping.put(DataType.INT, int.class);
        typeMapping.put(DataType.LONG, long.class);
        typeMapping.put(DataType.DATA_TYPE, org.nd4j.linalg.api.buffer.DataType.class);
        typeMapping.put(DataType.CONDITION, Condition.class);

        arrayTypeMapping.put(DataType.BOOL, boolean[].class);
        arrayTypeMapping.put(DataType.FLOATING_POINT, double[].class);
        arrayTypeMapping.put(DataType.NUMERIC, double[].class);
        arrayTypeMapping.put(DataType.INT, int[].class);
        arrayTypeMapping.put(DataType.LONG, long[].class);
        arrayTypeMapping.put(DataType.DATA_TYPE, org.nd4j.linalg.api.buffer.DataType[].class);

        validationMapping.put(DataType.BOOL, "validateBool");
        validationMapping.put(DataType.FLOATING_POINT, "validateFloatingPoint");
        validationMapping.put(DataType.NUMERIC, "validateNumerical");
        validationMapping.put(DataType.INT, "validateInteger");
        validationMapping.put(DataType.LONG, "validateInteger");
    }

    private static ConstraintCodeGenerator constraintCodeGenerator = new JavaConstraintCodeGenerator();

    private Nd4jNamespaceGenerator() { }

    public static void generate(NamespaceOps namespace, GeneratorConfig config, File directory, String className) throws IOException {

        TypeSpec.Builder builder = TypeSpec.classBuilder(className)
                .addModifiers(Modifier.PUBLIC);

        addDefaultConstructor(builder);

        //Add ops
        namespace.getOps()
                .stream()
                .filter(it -> !it.isAbstract())
                .sorted(Comparator.comparing(Op::getOpName))
                .forEachOrdered(o -> generateMethods(builder, o));


        TypeSpec ts = builder.build();

        JavaFile jf = JavaFile.builder("org.nd4j.linalg.factory.ops", ts)
                .addStaticImport(NDValidation.class, "isSameType")
                .build();

        StringBuilder sb = new StringBuilder();
        sb.append("/* ******************************************************************************\n" +
                " * Copyright (c) 2019 Konduit K.K.\n" +
                " *\n" +
                " * This program and the accompanying materials are made available under the\n" +
                " * terms of the Apache License, Version 2.0 which is available at\n" +
                " * https://www.apache.org/licenses/LICENSE-2.0.\n" +
                " *\n" +
                " * Unless required by applicable law or agreed to in writing, software\n" +
                " * distributed under the License is distributed on an \"AS IS\" BASIS, WITHOUT\n" +
                " * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the\n" +
                " * License for the specific language governing permissions and limitations\n" +
                " * under the License.\n" +
                " *\n" +
                " * SPDX-License-Identifier: Apache-2.0\n" +
                " ******************************************************************************/\n");
        sb.append("\n//================== GENERATED CODE - DO NOT MODIFY THIS FILE ==================\n\n");
        jf.writeTo(sb);

        File outFile = new File(directory, "org/nd4j/linalg/factory/ops/" + className + ".java");
        FileUtils.writeStringToFile(outFile, sb.toString(), StandardCharsets.UTF_8);
    }

    private static void addDefaultConstructor(TypeSpec.Builder builder) {
        //Add private no-arg constructor
        MethodSpec noArg = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .build();

        builder.addMethod(noArg);
    }

    private static void generateMethods(TypeSpec.Builder builder, Op op ){
        List<Signature> l = op.getSignatures();
        for(Signature s : l){
            builder.addMethod(signatureCreatorMethod(op, s));
        }
    }

    private static MethodSpec signatureCreatorMethod(Op op, Signature s){
        MethodSpec.Builder c = MethodSpec.methodBuilder(GenUtil.ensureFirstIsNotCap(op.getOpName()))
                .addModifiers(Modifier.PUBLIC);
        enableVarargsOnLastArg(c, op, s);

        buildJavaDoc(op, s, c);
        List<String> inNames = buildParameters(c, op, s);
        buildConstraints(c, op, s);
        buildExecution(c, op, inNames);

        return c.build();
    }



    private static void  buildJavaDoc(Op op, Signature s, MethodSpec.Builder c) {
        //Method javadoc:
        List<DocSection> doc = op.getDoc();
        if(doc != null && !doc.isEmpty()){
            for(DocSection ds : doc){
                if(ds.applies(Language.JAVA, CodeComponent.OP_CREATOR)){
                    String text = DocTokens.processDocText(ds.getText(), op, DocTokens.GenerationType.ND4J);
                    //Add <br> tags at the end of each line, where none already exists
                    String[] lines = text.split("\n");
                    for( int i=0; i<lines.length; i++ ){
                        if(!lines[i].endsWith("<br>")){
                            lines[i] = lines[i] + "<br>";
                        }
                    }
                    text = String.join("\n", lines);
                    c.addJavadoc(text + "\n\n");
                }
            }
        }


        // Document Constraints:
        //TODO what if constraint is on default value arg/s - no point specifying them here...
        final List<Constraint> constraints = op.getConstraints();
        if(constraints != null && !constraints.isEmpty()){
            c.addJavadoc("Inputs must satisfy the following constraints: <br>\n");
            for (Constraint constraint : constraints) {
                c.addJavadoc(constraint.getMessage() +": " + constraintCodeGenerator.generateExpression(constraint.getCheck()) + "<br>\n");
            }

            c.addJavadoc("\n");
        }

        List<Parameter> params = s.getParameters();
        if(params != null && !params.isEmpty()){
            for(Parameter p : params){
                if(p instanceof Input){
                    Input i = (Input)p;
                    c.addJavadoc("@param " + i.getName() + " " + (i.getDescription() == null ? "" : DocTokens.processDocText(i.getDescription(), op, DocTokens.GenerationType.ND4J)) + " (" + i.getType() + " type)\n");
                } else if(p instanceof Arg){
                    Arg arg = (Arg)p;
                    final Count count = arg.getCount();
                    if (count == null || count.equals(exactlyOne)) {
                        c.addJavadoc("@param " + arg.getName() + " " + (arg.getDescription() == null ? "" : DocTokens.processDocText(arg.getDescription(), op, DocTokens.GenerationType.ND4J)) + "\n");
                    } else {
                        c.addJavadoc("@param " + arg.getName() + " " + (arg.getDescription() == null ? "" : DocTokens.processDocText(arg.getDescription(), op, DocTokens.GenerationType.ND4J)) + " (Size: " + count.toString() + ")\n");
                    }
                } else {
                    throw new RuntimeException("Unknown parameter type: " + p + " - " + p.getClass() + " - op = " + op.getOpName());
                }
            }


        }

        //Outputs:
        List<Output> outputs = op.getOutputs();
        if(outputs != null && !outputs.isEmpty()){
            if(outputs.size() == 1){
                Output o = outputs.get(0);
                c.addJavadoc("@return " + o.getName() + " " + (o.getDescription() == null ? "" : DocTokens.processDocText(o.getDescription(), op, DocTokens.GenerationType.ND4J)) + " (" + o.getType() + " type)\n");
            } else {
                throw new UnsupportedOperationException("Javadoc for multi-output ops not yet implemented");
            }
        }
    }

    private static List<String> buildParameters(MethodSpec.Builder c, Op op, Signature s) {
        List<String> inNames = new ArrayList<>();

        List<Parameter> params = s.getParameters();
        if(params != null && !params.isEmpty()){
            for(Parameter p : params){
                if(p instanceof Input){
                    Input i = (Input)p;
                    final String inputName = i.getName();
                    inNames.add(inputName);

                    final Count count = i.getCount();
                    if(count == null || count.equals(exactlyOne)) {
                        //Single input
                        c.addParameter(INDArray.class, inputName);
                    } else {
                        //Array input
                        c.addParameter(INDArray[].class, inputName);
                    }
                    // Check for parameter types
                    c.addStatement(CodeBlock.of("$T.$L($S, $S, $L)", NDValidation.class, validationMapping.get(i.getType()), op.getOpName(), inputName, inputName));
                    checkParameterCount(c, count, inputName);
                } else if(p instanceof Arg){
                    Arg arg = (Arg)p;
                    final String argName = arg.getName();
                    if(argName == null || argName.isEmpty()){
                        throw new IllegalStateException("Got null argument name for op " + op.getOpName());
                    }
                    inNames.add(argName);

                    final Count count = arg.getCount();
                    if (count == null || count.equals(exactlyOne)) {
                        // single arg
                        if(!typeMapping.containsKey(arg.getType())){
                            throw new IllegalStateException("No type mapping has been specified for type " + arg.getType() + " (op=" + op.getOpName() + ", arg=" + arg.getName() + ")" );
                        }
                        c.addParameter(typeMapping.get(arg.getType()), argName);
                    } else {
                        // array Arg
                        if(!arrayTypeMapping.containsKey(arg.getType())){
                            throw new IllegalStateException("No array type mapping has been specified for type " + arg.getType() + " (op=" + op.getOpName() + ", arg=" + arg.getName() + ")" );
                        }
                        c.addParameter(arrayTypeMapping.get(arg.getType()), argName);
                    }

                    checkParameterCount(c, count, argName);
                } else {
                    throw new IllegalStateException("Unknown parameter type: " + p + " - " + p.getClass());
                }

            }
        }

        return inNames;
    }

    private static void buildConstraints(MethodSpec.Builder c, Op op, Signature s) {
        if(op.getConstraints() == null || op.getConstraints().isEmpty())
            return;

        //TODO not all contsraints apply to all signatures?

        // Don't materialize the Backend Constraints
        final List<Constraint> constraints = op.getConstraints();
        for (Constraint constraint : constraints.stream().filter(it -> !(it instanceof BackendConstraint)).collect(Collectors.toList())) {
            c.addStatement(CodeBlock.of("$T.checkArgument($L, $S)", Preconditions.class, constraintCodeGenerator.generateExpression(constraint.getCheck()), constraint.getMessage()));
        }
    }

    private static void buildExecution(MethodSpec.Builder c, Op op, List<String> inNames) {
        boolean singleOut = op.getOutputs().size() == 1;
        if(singleOut){
            c.returns(INDArray.class);
        } else {
            c.returns(INDArray[].class);
        }

        //Op execution:
        StringBuilder sb = new StringBuilder();
        sb.append("return $T.exec(new ")
                .append(op.getJavaPackage())
                .append(".")
                .append(op.getJavaOpClass() == null ? GenUtil.ensureFirstIsCap(op.getOpName()) : op.getJavaOpClass())
                .append("(")
                .append(String.join(", ", inNames))
                .append("))");
        if(!op.getLegacy() && singleOut)        //Note: legacy ops Nd4j.exec(Op) returns INDArray; Nd4j.exec(CustomOp) returns INDArray[]
            sb.append("[0]");

        c.addStatement(sb.toString(), Nd4j.class);
    }

    private static void enableVarargsOnLastArg(MethodSpec.Builder c, Op op) {
        if(op.getArgs() != null && !op.getArgs().isEmpty()){
            final Arg lastArg = op.getArgs().get(op.getArgs().size() - 1);
            final Count count = lastArg.getCount();
            if(count != null && !count.equals(exactlyOne)){
                c.varargs(true);
            }
        }
    }

    private static void enableVarargsOnLastArg(MethodSpec.Builder c, Op op, Signature s) {
        List<Parameter> p = s.getParameters();
        if(p != null && !p.isEmpty()){
            Parameter lastP = p.get(p.size() - 1);
            if (lastP instanceof Arg) {
                Arg arg = (Arg) lastP;
                final Count count = arg.getCount();
                if (count != null && !count.equals(exactlyOne)) {
                    c.varargs(true);
                }
            }
        }
    }

    private static String countToJava(Count count,String paramName) {
        final String paramLength = paramName + ".length";
        if(count instanceof Exactly){
            return paramLength + " == " + ((Exactly) count).getCount();
        }else if(count instanceof AtLeast){
            return paramLength + " >= " + ((AtLeast) count).getMin();
        }else if(count instanceof AtMost){
            return paramLength + " <= "+ ((AtMost) count).getMax();
        }else if(count instanceof Range){
            return ((Range) count).getFrom() + " <= " + paramLength + " && " + paramLength + " <= " + ((Range) count).getTo();
        }else{
            throw new IllegalArgumentException("Can not deal with Count of type " + count.getClass().getName());
        }
    }

    private static void checkParameterCount(MethodSpec.Builder c, Count count, String paramName) {
        // Check for parameter counts
        if(count != null && !count.equals(exactlyOne)){
            final String errorMessage = paramName + " has incorrect size/length. Expected: " + countToJava(count, paramName) + ", got %s";
            if(count instanceof Exactly){
                c.addStatement(CodeBlock.of("$T.checkArgument($L.length == $L, $S, $L)", Preconditions.class, paramName, ((Exactly) count).getCount(), errorMessage, paramName + ".length"));
            }else if(count instanceof AtLeast){
                c.addStatement(CodeBlock.of("$T.checkArgument($L.length >= $L, $S, $L)", Preconditions.class, paramName, ((AtLeast) count).getMin(), errorMessage, paramName + ".length"));
            }else if(count instanceof AtMost){
                c.addStatement(CodeBlock.of("$T.checkArgument($L.length <= $L, $S, $L)", Preconditions.class, paramName, ((AtMost) count).getMax(), errorMessage, paramName + ".length"));
            }else if(count instanceof Range){
                c.addStatement(CodeBlock.of("$T.checkArgument($L.length >= $L && $L.length <= $L, $S, $L)", Preconditions.class, paramName, ((Range) count).getFrom(), paramName, ((Range) count).getTo(), errorMessage, paramName + ".length"));
            }
        }
    }
}
