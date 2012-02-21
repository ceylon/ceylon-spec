package com.redhat.ceylon.compiler.typechecker.util;

import java.util.ArrayList;
import java.util.List;

import com.redhat.ceylon.compiler.typechecker.analyzer.AnalysisError;
import com.redhat.ceylon.compiler.typechecker.analyzer.AnalysisWarning;
import com.redhat.ceylon.compiler.typechecker.model.Declaration;
import com.redhat.ceylon.compiler.typechecker.model.ProducedType;
import com.redhat.ceylon.compiler.typechecker.parser.LexError;
import com.redhat.ceylon.compiler.typechecker.parser.ParseError;
import com.redhat.ceylon.compiler.typechecker.tree.Message;
import com.redhat.ceylon.compiler.typechecker.tree.NaturalVisitor;
import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.compiler.typechecker.tree.UnexpectedError;
import com.redhat.ceylon.compiler.typechecker.tree.Visitor;

public class AssertionVisitor extends Visitor implements NaturalVisitor {
    
    private boolean expectingError = false;
    private List<Message> foundErrors = new ArrayList<Message>();
    private int errors = 0;
    private int warnings = 0;

    @Override
    public void visit(Tree.TypedDeclaration that) {
        if (that.getType()!=null) {
            checkType(that, that.getType().getTypeModel(), that.getType());
        }
        super.visit(that);
    }

    @Override
    public void visit(Tree.ExpressionStatement that) {
        checkType(that, that.getExpression().getTypeModel(), that.getExpression());
        super.visit(that);
    }
    
    private void checkType(Tree.Statement that, ProducedType type, Node typedNode) {
        for (Tree.CompilerAnnotation c: that.getCompilerAnnotations()) {
            if (c.getIdentifier().getText().equals("type")) {
                String expectedType = c.getStringLiteral().getText();
                if (typedNode==null || type==null || 
                        type.getDeclaration()==null) {
                    out(that, "type not known");
                }
                else {
                    String actualType = type.getProducedTypeName(false);
                    if ( !actualType.equals(expectedType.substring(1,expectedType.length()-1)) )
                        out(that, "type " + actualType + " not of expected type " + expectedType);
                }
            }
        }
    }
    
    @Override
    public void visit(Tree.StatementOrArgument that) {
        if (that instanceof Tree.Variable) {
            if ( ( (Tree.Variable) that ).getType() instanceof Tree.SyntheticVariable ) {
                super.visit(that);
                return;
            }
        }
        boolean b = expectingError;
        List<Message> f = foundErrors;
        expectingError = false;
        initExpectingError(that.getCompilerAnnotations());
        super.visit(that);
        checkErrors(that);
        expectingError = b;
        foundErrors = f;
    }
    
    @Override
    public void visit(Tree.CompilationUnit that) {
    	expectingError = false;
    	initExpectingError(that.getCompilerAnnotations());
        foundErrors.addAll(that.getErrors());
        checkErrors(that);
    	expectingError = false;
    	super.visitAny(that);
    }
    
    @Override
    public void visit(Tree.Declaration that) {
        super.visit(that);
        for (Tree.CompilerAnnotation c: that.getCompilerAnnotations()) {
            if (c.getIdentifier().getText().equals("captured")) {
                Declaration d = that.getDeclarationModel();
                if (!d.isCaptured() && !d.isShared()) {
                    out(that, "not captured");
                }
            }
            if (c.getIdentifier().getText().equals("uncaptured")) {
                Declaration d = that.getDeclarationModel();
                if (d.isCaptured() || d.isShared()) {
                    out(that, "captured");
                }
            }
        }
    }

    protected void out(Node that, String message) {
        System.err.println(
            message + " at " + 
            that.getLocation() + " of " +
            that.getUnit().getFilename());
    }

    protected void out(Node that, LexError err) {
        errors++;
        System.err.println(
            "lex error encountered [" +
            err.getMessage() + "] at " + 
            err.getHeader() + " of " + 
            that.getUnit().getFilename());
    }

    protected void out(Node that, ParseError err) {
        errors++;
        System.err.println(
            "parse error encountered [" +
            err.getMessage() + "] at " + 
            err.getHeader() + " of " + 
            that.getUnit().getFilename());
    }

    protected void out(UnexpectedError err) {
        errors++;
        System.err.println(
            "unexpected error encountered [" +
            err.getMessage() + "] at " + 
            err.getTreeNode().getLocation() + " of " +
            err.getTreeNode().getUnit().getFilename());
    }

    protected void out(AnalysisError err) {
        errors++;
        System.err.println(
            "error encountered [" +
            err.getMessage() + "] at " + 
            err.getTreeNode().getLocation() + " of " +
            err.getTreeNode().getUnit().getFilename());
    }

    protected void out(AnalysisWarning err) {
        warnings++;
        System.out.println(
            "warning encountered [" +
            err.getMessage() + "] at " + 
            err.getTreeNode().getLocation() + " of " +
            err.getTreeNode().getUnit().getFilename());
    }

    private void checkErrors(Node that) {
        final List<Message> errs = that.getErrors();
    	for (Message err: errs) {
            if (err instanceof UnexpectedError) {
                out( (UnexpectedError) err );
            }
    	}
    	if (expectingError) {
            for (Message err: errs) {
                if (err instanceof AnalysisError ||
                		err instanceof LexError ||
                		err instanceof ParseError) {
                    return;
                }
            }
            out(that, "no error encountered");
        }
        else {
            for (Message err: errs) {
                if (err instanceof LexError) {
                    out( that, (LexError) err );
                }
                else if (err instanceof ParseError) {
                    out( that, (ParseError) err );
                }
                else if (err instanceof AnalysisError) {
                    out( (AnalysisError) err );
                }
                else if (err instanceof AnalysisWarning) {
                    if (includeWarnings()) {
                        out( (AnalysisWarning) err );
                    }
                }
            }
        }
    }
    
    protected void initExpectingError(List<Tree.CompilerAnnotation> annotations) {
        for (Tree.CompilerAnnotation c: annotations) {
            if (c.getIdentifier().getText().equals("error")) {
                expectingError = true;
            }
        }
    }
    
    protected boolean includeWarnings() {
        return true;
    }
    
    @Override
    public void visitAny(Node that) {
        foundErrors.addAll(that.getErrors());
        super.visitAny(that);
    }
    
    public void print(boolean verbose) {
    	if(!verbose && errors == 0 && warnings == 0)
    		return;
        System.out.println(errors + " errors, " + warnings + " warnings");
    }

	public List<Message> getFoundErrors() {
		return foundErrors;
	}

	public int getErrors() {
		return errors;
	}

	public int getWarnings() {
		return warnings;
	}
    
}
