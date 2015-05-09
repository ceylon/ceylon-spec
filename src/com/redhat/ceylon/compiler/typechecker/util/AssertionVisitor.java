package com.redhat.ceylon.compiler.typechecker.util;

import java.util.ArrayList;
import java.util.List;

import com.redhat.ceylon.compiler.typechecker.analyzer.AnalysisError;
import com.redhat.ceylon.compiler.typechecker.analyzer.UnsupportedError;
import com.redhat.ceylon.compiler.typechecker.analyzer.UsageWarning;
import com.redhat.ceylon.compiler.typechecker.model.ProducedType;
import com.redhat.ceylon.compiler.typechecker.model.Unit;
import com.redhat.ceylon.compiler.typechecker.parser.LexError;
import com.redhat.ceylon.compiler.typechecker.parser.ParseError;
import com.redhat.ceylon.compiler.typechecker.tree.AnalysisMessage;
import com.redhat.ceylon.compiler.typechecker.tree.Message;
import com.redhat.ceylon.compiler.typechecker.tree.NaturalVisitor;
import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.compiler.typechecker.tree.UnexpectedError;
import com.redhat.ceylon.compiler.typechecker.tree.Visitor;

public class AssertionVisitor extends Visitor implements NaturalVisitor {
    
    private boolean expectingError = false;
	private String errMessage;
    private List<Message> foundErrors = new ArrayList<Message>();
    private int errors = 0;
    private int warnings = 0;
    private boolean usageWarnings = false;

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
    
    protected void checkType(Tree.Statement that, ProducedType type, Node typedNode) {
        for (Tree.CompilerAnnotation c: that.getCompilerAnnotations()) {
            if (c.getIdentifier().getText().equals("type")) {
                Tree.StringLiteral sl = c.getStringLiteral();
                if (sl==null) {
                	out(that, "missing asserted type");
                }
                else {
                	String expectedType = sl.getText();
                	if (typedNode==null || type==null || 
                			type.getDeclaration()==null) {
                		out(that, "type not known");
                	}
                	else {
                		String actualType = type.getProducedTypeName(false);
                		if ( !actualType.equals(expectedType) )
                			out(that, "type " + actualType + 
                					" not of expected type " + expectedType);
                	}
                }
            }
        }
    }
    
    @Override
    public void visit(Tree.StatementOrArgument that) {
        if (ignore) {
            super.visit(that);
            return;
        }
        if (that instanceof Tree.Variable) {
            if (((Tree.Variable) that).getType() 
                    instanceof Tree.SyntheticVariable) {
                super.visit(that);
                return;
            }
        }
        if (that instanceof Tree.ForIterator) {
            super.visit(that);
            return;
        }
        boolean b = expectingError;
        List<Message> f = foundErrors;
        expectingError = false;
        foundErrors = new ArrayList<Message>();
        initExpectingError(that.getCompilerAnnotations());
        super.visit(that);
        checkErrors(that);
        expectingError = b;
        errMessage = null;
        foundErrors = f;
    }
    
    boolean ignore;
    
    @Override
    public void visit(Tree.ParameterDeclaration that) {
        boolean b = expectingError;
        List<Message> f = foundErrors;
        expectingError = false;
        errMessage = null;
        foundErrors = new ArrayList<Message>();
        initExpectingError(that.getTypedDeclaration().getCompilerAnnotations());
        ignore=true;
        super.visit(that);
        ignore=false;
        checkErrors(that);
        expectingError = b;
        errMessage = null;
        foundErrors = f;
    }
    
    @Override
    public void visit(Tree.CompilationUnit that) {
    	expectingError = false;
        foundErrors = new ArrayList<Message>();
    	initExpectingError(that.getCompilerAnnotations());
        foundErrors.addAll(that.getErrors());
        checkErrors(that);
        foundErrors = new ArrayList<Message>();
    	expectingError = false;
    	errMessage = null;
    	super.visitAny(that);
    }
    
//    @Override
//    public void visit(Tree.Declaration that) {
//        super.visit(that);
//        for (Tree.CompilerAnnotation c: that.getCompilerAnnotations()) {
//            if (c.getIdentifier().getText().equals("captured")) {
//                Declaration d = that.getDeclarationModel();
//                if (!d.isCaptured() && !d.isShared()) {
//                    out(that, "not captured");
//                }
//            }
//            if (c.getIdentifier().getText().equals("uncaptured")) {
//                Declaration d = that.getDeclarationModel();
//                if (d.isCaptured() || d.isShared()) {
//                    out(that, "captured");
//                }
//            }
//        }
//    }

    protected void out(Node that, String message) {
        System.err.println(
            message + " at " + 
            that.getLocation() + " of " +
            file(that));
    }

    protected void out(Node that, LexError err) {
        errors++;
        System.err.println(
            "lex error [" +
            err.getMessage() + "] at " + 
            err.getHeader() + " of " + 
            file(that));
    }

    protected void out(Node that, ParseError err) {
        errors++;
        System.err.println(
            "parse error [" +
            err.getMessage() + "] at " + 
            err.getHeader() + " of " + 
            file(that));
    }

    protected void out(UnexpectedError err) {
        errors++;
        System.err.println(
            "unexpected error [" +
            err.getMessage() + "] at " + 
            loc(err));
    }

    protected void out(AnalysisError err) {
        errors++;
        System.err.println(
            "error [" +
            err.getMessage() + "] at " + 
            loc(err));
    }

    protected void out(UnsupportedError err) {
        warnings++;
        System.out.println(
            "warning [" + 
            err.getMessage() + "] at " + 
            loc(err));
    }

    /**
     * Prints warning messages for unused declarations.
     *
     * @param err error message
     */
    protected void out(UsageWarning err) {
        System.out.println(
            "warning [" +
            err.getMessage() + "] at " +
            loc(err));
    }

	private String loc(AnalysisMessage err) {
		return err.getTreeNode().getLocation() + " of " +
		            file(err.getTreeNode());
	}

	private String file(Node that) {
		Unit unit = that.getUnit();
		String relativePath = unit.getRelativePath();
        return !relativePath.isEmpty() ?
				relativePath : 
				unit.getFilename();
	}

    private void checkErrors(Node that) {
        try {
            for (Message err: foundErrors) {
                if (err instanceof UnexpectedError) {
                    out( (UnexpectedError) err );
                }
            }
            if (expectingError) {
            	boolean found = false;
                for (Message err: foundErrors) {
                    if (err instanceof AnalysisError ||
                            err instanceof LexError ||
                            err instanceof ParseError) {
                    	if (errMessage==null ||
                    			err.getMessage().contains(errMessage)) {
                    		return;
                    	}
                    	else {
                    		found = true;
                    	}
                    }
                }
                if (found) {
            		out(that, "error message should contain \"" + 
            				errMessage);
                }
                else {
                	out(that, "no errors");
                	return;
                }
            }
//            else {
                for (Message err: foundErrors) {
                    if (err instanceof LexError) {
                        out( that, (LexError) err );
                    }
                    else if (err instanceof ParseError) {
                        out( that, (ParseError) err );
                    }
                    else if (err instanceof UnsupportedError) {
                        if (includeUnsupportedErrors()) {
                            out( (UnsupportedError) err );
                        }
                    } 
                    else if (err instanceof AnalysisError) {
                        out( (AnalysisError) err );
                    }
                    else if (err instanceof UsageWarning) {
                        if (usageWarnings) {
                            out( (UsageWarning) err );
                        }
                    }
                }
//            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    protected void initExpectingError(List<Tree.CompilerAnnotation> annotations) {
        for (Tree.CompilerAnnotation c: annotations) {
            if (c.getIdentifier().getText().equals("error")) {
                expectingError = true;
                Tree.StringLiteral sl = c.getStringLiteral();
				if (sl!=null) {
                	errMessage = sl.getText();
                }
            }
        }
    }
    
    protected boolean includeUnsupportedErrors() {
        return true;
    }
    
    /**
     * Enables or disables output of the warnings for the unused declarations
     * 
     * @param usageWarnings true to enable output and false otherwise.
     */
    public void includeUsageWarnings(boolean usageWarnings) {
        this.usageWarnings = usageWarnings;
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
