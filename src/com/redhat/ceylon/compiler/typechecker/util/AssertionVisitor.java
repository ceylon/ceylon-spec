package com.redhat.ceylon.compiler.typechecker.util;

import java.util.ArrayList;
import java.util.List;

import com.redhat.ceylon.compiler.typechecker.analyzer.AnalysisError;
import com.redhat.ceylon.compiler.typechecker.model.Declaration;
import com.redhat.ceylon.compiler.typechecker.model.Value;
import com.redhat.ceylon.compiler.typechecker.model.ValueParameter;
import com.redhat.ceylon.compiler.typechecker.tree.NaturalVisitor;
import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.compiler.typechecker.tree.Visitor;

public class AssertionVisitor extends Visitor implements NaturalVisitor {
    
    boolean expectingError = false;
    List<AnalysisError> foundErrors = new ArrayList<AnalysisError>();

    @Override
    public void visit(Tree.TypedDeclaration that) {
        checkType(that, that.getType());
        super.visit(that);
    }

    @Override
    public void visit(Tree.ExpressionStatement that) {
        checkType(that, that.getExpression());
        super.visit(that);
    }
    
    private void checkType(Tree.Statement that, Tree.Term typedNode) {
        for (Tree.CompilerAnnotation c: that.getCompilerAnnotations()) {
            if (c.getIdentifier().getText().equals("type")) {
                String expectedType = c.getStringLiteral().getText();
                if (typedNode==null || typedNode.getTypeModel()==null || 
                        typedNode.getTypeModel().getDeclaration()==null) {
                    out(that, "type not known");
                }
                else {
                    String actualType = typedNode.getTypeModel().getProducedTypeName();
                    if ( !actualType.equals(expectedType.substring(1,expectedType.length()-1)) )
                        out(that, "type " + actualType + " not of expected type " + expectedType);
                }
            }
        }
    }
    
    @Override
    public void visit(Tree.StatementOrArgument that) {
        boolean b = expectingError;
        List<AnalysisError> f = foundErrors;
        expectingError = false;
        foundErrors = new ArrayList<AnalysisError>();
        initExpectingError(that);
        super.visit(that);
        checkErrors(that);
        expectingError = b;
        foundErrors = f;
    }
    
    @Override
    public void visit(Tree.ImportMemberOrType that) {
        boolean b = expectingError;
        List<AnalysisError> f = foundErrors;
        expectingError = false;
        foundErrors = new ArrayList<AnalysisError>();
        initExpectingError(that);
        super.visit(that);
        checkErrors(that);
        expectingError = b;
        foundErrors = f;
    }
    
    @Override
    public void visit(Tree.Declaration that) {
        super.visit(that);
        for (Tree.CompilerAnnotation c: that.getCompilerAnnotations()) {
            if (c.getIdentifier().getText().equals("captured")) {
                Declaration d = that.getDeclarationModel();
                if (d instanceof Value) {
                     if (!((Value) d).isCaptured() && !d.isShared()) {
                         out(that, "not captured");
                     }
                }
                else if (d instanceof ValueParameter) {
                    if (!((ValueParameter) d).isCaptured()) {
                        out(that, "not captured");
                    }
                }
                else {
                    out(that, "not a value");
                }
            }
            if (c.getIdentifier().getText().equals("uncaptured")) {
                Declaration d = that.getDeclarationModel();
                if (d instanceof Value) {
                     if (((Value) d).isCaptured() || d.isShared()) {
                         out(that, "captured");
                     }
                }
                else if (d instanceof ValueParameter) {
                    if (((ValueParameter) d).isCaptured()) {
                        out(that,"captured");
                    }
                }
                else {
                    out(that, "not a value");
                }
            }
        }
    }

    private void out(Node that, String message) {
        System.err.println(
            message + " at " + 
            that.getAntlrTreeNode().getLine() + ":" +
            that.getAntlrTreeNode().getCharPositionInLine() + " of " +
            that.getUnit().getFilename());
    }

    private void checkErrors(Node that) {
        if (expectingError && foundErrors.size()==0)
            out(that, "no error encountered");
        if (!expectingError && foundErrors.size()>0)
            out(that, "errors encountered " + foundErrors);
    }

    private void initExpectingError(Tree.ImportMemberOrType that) {
        for (Tree.CompilerAnnotation c: that.getCompilerAnnotations()) {
            if (c.getIdentifier().getText().equals("error")) {
                expectingError = true;
            }
        }
    }
    
    private void initExpectingError(Tree.StatementOrArgument that) {
        for (Tree.CompilerAnnotation c: that.getCompilerAnnotations()) {
            if (c.getIdentifier().getText().equals("error")) {
                expectingError = true;
            }
        }
    }
    
    @Override
    public void visitAny(Node that) {
        foundErrors.addAll(that.getErrors());
        super.visitAny(that);
    }
    
}
