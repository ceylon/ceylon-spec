package com.redhat.ceylon.compiler.typechecker.analyzer;

import static com.redhat.ceylon.compiler.typechecker.analyzer.AnalyzerUtil.getLastExecutableStatement;
import static com.redhat.ceylon.compiler.typechecker.tree.TreeUtil.eliminateParensAndWidening;

import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.BaseMemberExpression;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.Parameter;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.Super;
import com.redhat.ceylon.compiler.typechecker.tree.Visitor;
import com.redhat.ceylon.model.typechecker.model.Constructor;
import com.redhat.ceylon.model.typechecker.model.Declaration;
import com.redhat.ceylon.model.typechecker.model.FunctionOrValue;
import com.redhat.ceylon.model.typechecker.model.TypeDeclaration;
import com.redhat.ceylon.model.typechecker.model.TypedDeclaration;
/**
 * Validates that the initializer of a class does not leak 
 * self-references to the instance being initialized.
 * 
 * @author Gavin King
 *
 */
public class SelfReferenceVisitor extends Visitor {
    
    private final TypeDeclaration typeDeclaration;
    private Tree.Statement lastExecutableStatement;
    private boolean declarationSection = false;
    private int nestedLevel = -1;
    private boolean defaultArgument;

    public SelfReferenceVisitor(TypeDeclaration td) {
        typeDeclaration = td;
    }
    
    private void visitExtendedType(Tree.ExtendedTypeExpression that) {
        Declaration member = that.getDeclaration();
        if (member!=null && 
                !typeDeclaration.isAlias() && 
                !(member instanceof Constructor)) {
            if (!declarationSection && 
                    isInherited(that, member)) {
                that.addError("inherited member class may not be extended in initializer of '" +
                    		typeDeclaration.getName() + "': '" + member.getName() + 
                    		"' is inherited from '" + 
                    		((Declaration) member.getContainer()).getName() + "'");
            }
        }
    }

    private void visitReference(Tree.Primary that) {
        if (that instanceof Tree.MemberOrTypeExpression) {
            Tree.MemberOrTypeExpression mte = 
                    (Tree.MemberOrTypeExpression) that;
            Declaration member = mte.getDeclaration();
            if (member!=null) {
                if (!declarationSection && 
                        isInherited(that, member)) {
                    that.addError("inherited member may not be used in initializer of '" +
                    		typeDeclaration.getName() + "': '" + member.getName() + 
                    		"' is inherited from '" + 
                    		((Declaration) member.getContainer()).getName() + "'");
                }
            }
        }
    }
    
    private boolean isInherited(Tree.Primary that, Declaration member) {
        return that.getScope().getInheritingDeclaration(member)==typeDeclaration;
    }

    @Override
    public void visit(Tree.AnnotationList that) {}

    @Override
    public void visit(Tree.ExtendedTypeExpression that) {
        super.visit(that);
        visitExtendedType(that);
    }

    @Override
    public void visit(Tree.BaseMemberExpression that) {
        super.visit(that);
        visitReference(that);
    }

    @Override
    public void visit(Tree.BaseTypeExpression that) {
        super.visit(that);
        visitReference(that);
    }

    @Override
    public void visit(Tree.QualifiedMemberExpression that) {
        super.visit(that);
        if (isSelfReference(that.getPrimary())) {
            visitReference(that);
        }
    }

    @Override
    public void visit(Tree.QualifiedTypeExpression that) {
        super.visit(that);
        if (isSelfReference(that.getPrimary())) {
            visitReference(that);
        }
    }

    private boolean isSelfReference(Tree.Term that) {
        Tree.Term term = eliminateParensAndWidening(that);
        return (directlyInBody() && (term instanceof Tree.This || term instanceof Tree.Super))
            || (directlyInNestedBody() && that instanceof Tree.Outer);
    }

    @Override
    public void visit(Tree.IsCondition that) {
        super.visit(that);
        if ( inBody() ) {
            Tree.Variable v = that.getVariable();
            if (v!=null && v.getSpecifierExpression()!=null) {
                Tree.Term term = v.getSpecifierExpression()
                        .getExpression().getTerm();
                if (directlyInBody() && term instanceof Tree.Super) {
                    term.addError("narrows super");
                }
                else if (mayNotLeakThis() && term instanceof Tree.This) {
                    term.addError("narrows this in initializer: '" + 
                            typeDeclaration.getName() + "'");
                }
                else if (mayNotLeakOuter() && term instanceof Tree.Outer) {
                    term.addError("narrows outer in initializer: '" + 
                            typeDeclaration.getName() + "'");
                }
            }
        }
    }
    
    @Override
    public void visit(Tree.ObjectDefinition that) {
        if (that.getAnonymousClass()==typeDeclaration) {
            nestedLevel=0;
            super.visit(that);
            nestedLevel=-1;
        }
        else if (inBody()){
            nestedLevel++;
            super.visit(that);
            nestedLevel--;
        }
        else {
            super.visit(that);
        }
    }
    
    @Override
    public void visit(Tree.ObjectArgument that) {
        if (that.getAnonymousClass()==typeDeclaration) {
            nestedLevel=0;
            super.visit(that);
            nestedLevel=-1;
        }
        else if (inBody()){
            nestedLevel++;
            super.visit(that);
            nestedLevel--;
        }
        else {
            super.visit(that);
        }
    }
    
    @Override
    public void visit(Tree.ObjectExpression that) {
        if (that.getAnonymousClass()==typeDeclaration) {
            nestedLevel=0;
            super.visit(that);
            nestedLevel=-1;
        }
        else if (inBody()){
            nestedLevel++;
            super.visit(that);
            nestedLevel--;
        }
        else {
            super.visit(that);
        }
    }
    
    @Override
    public void visit(Tree.TypeDeclaration that) {
        if (that.getDeclarationModel()==typeDeclaration) {
            nestedLevel=0;
            declarationSection = false;
            super.visit(that);
            nestedLevel=-1;
        }
        else if (inBody()){
            nestedLevel++;
            super.visit(that);
            nestedLevel--;
        }
        else {
            super.visit(that);
        }
    }
    
    @Override
    public void visit(Tree.InterfaceBody that) {
        if (directlyInBody()) {
            declarationSection = true;
            lastExecutableStatement = null;
            super.visit(that);
            declarationSection = false;
        }
        else {
            super.visit(that);
        }
    }

    private boolean directlyInBody() {
        return nestedLevel==0;
    }
    
    @Override
    public void visit(Tree.ClassBody that) {
        if (directlyInBody()) {
            Tree.Statement les = getLastExecutableStatement(that);
            declarationSection = les==null;
            lastExecutableStatement = les;
            super.visit(that);
            lastExecutableStatement = null;
            declarationSection = false;
        }
        else {
            super.visit(that);
        }
    }

    boolean mayNotLeakThis() {
        return !declarationSection && directlyInBody();
    }
    
    boolean mayNotLeakOuter() {
        return !declarationSection && directlyInNestedBody();
    }

    private boolean directlyInNestedBody() {
        return nestedLevel==1;
    }
    
    boolean inBody() {
        return nestedLevel>=0;
    }
    
    @Override
    public void visit(Tree.Statement that) {
        super.visit(that);
        if (inBody()) {
            declarationSection = declarationSection || 
                    that==lastExecutableStatement;
        }
    }
    
    private void checkSelfReference(Node that, Tree.Term term) {
        Tree.Term t = eliminateParensAndWidening(term);
        if (directlyInBody() && t instanceof Tree.Super) {
            that.addError("leaks super reference: '" + 
                    typeDeclaration.getName() + "'");
        }    
        if (mayNotLeakThis() && t instanceof Tree.This) {
            that.addError("leaks this reference in initializer: '" + 
                    typeDeclaration.getName() + "'");
        }    
        if (mayNotLeakOuter() && t instanceof Tree.Outer) {
            that.addError("leaks outer reference in initializer: '" + 
                    typeDeclaration.getName() + "'");
        }
        if (typeDeclaration.isObjectClass() && 
                mayNotLeakAnonymousClass() && 
        		t instanceof Tree.BaseMemberExpression) {
        	Tree.BaseMemberExpression bme = 
        	        (Tree.BaseMemberExpression) t;
            Declaration declaration = 
        	        bme.getDeclaration();
        	if (declaration instanceof TypedDeclaration) {
        		TypedDeclaration td = 
        		        (TypedDeclaration) declaration;
                if (td.getTypeDeclaration()==typeDeclaration) {
                    that.addError("object leaks self reference in initializer: '" + 
                            typeDeclaration.getName() + "'");
        		}
        	}
        }
        if (typeDeclaration.isObjectClass() && 
                mayNotLeakAnonymousClass() && t 
        		instanceof Tree.QualifiedMemberExpression) {
        	Tree.QualifiedMemberExpression qme = 
        	        (Tree.QualifiedMemberExpression) t;
        	if (qme.getPrimary() instanceof Tree.Outer) {
        		Declaration declaration = qme.getDeclaration();
        		if (declaration instanceof TypedDeclaration) {
        			TypedDeclaration td = 
        			        (TypedDeclaration) declaration;
                    if (td.getTypeDeclaration()==typeDeclaration) {
        				that.addError("object leaks self reference in initializer: '" + 
        						typeDeclaration.getName() + "'");
        			}
        		}
        	}
        }
    }
    
    boolean mayNotLeakAnonymousClass() {
    	return !declarationSection && inBody();
    }
    
    @Override
    public void visit(Parameter that) {
        boolean oda = defaultArgument;
        defaultArgument = true;
        super.visit(that);
        defaultArgument = oda;
    }
    
    @Override
    public void visit(Super that) {
        super.visit(that);
        if (defaultArgument) {
            that.addError("reference to super from default argument expression");
        }
    }

    @Override
    public void visit(Tree.Return that) {
        super.visit(that);
        Tree.Expression e = that.getExpression();
        if ( e!=null && inBody() ) {
            checkSelfReference(that, e.getTerm());    
        }
    }

    @Override
    public void visit(Tree.Throw that) {
        super.visit(that);
        Tree.Expression e = that.getExpression();
        if ( e!=null && inBody() ) {
            checkSelfReference(that, e.getTerm());    
        }
    }

    @Override
    public void visit(Tree.FunctionArgument that) {
        super.visit(that);
        Tree.Expression e = that.getExpression();
        if ( e!=null && inBody() ) {
            checkSelfReference(that, e.getTerm());    
        }
    }
    
    @Override
    public void visit(Tree.SpecifierOrInitializerExpression that) {
        super.visit(that);
        Tree.Expression e = that.getExpression();
        if ( e!=null && inBody() ) {
            checkSelfReference(that, e.getTerm());    
        }
    }
    
    @Override
    public void visit(Tree.SpecifierStatement that) {
    	if ( inBody() ) {
    		Tree.Term lt = that.getBaseMemberExpression();
			Tree.SpecifierExpression se = that.getSpecifierExpression();
    		if (lt instanceof Tree.MemberOrTypeExpression && se!=null) {
    			Tree.Expression e = se.getExpression();
    			if (e!=null) {
    				if (e.getTerm() instanceof Tree.This) {
    					Declaration d = ((Tree.MemberOrTypeExpression) lt).getDeclaration();
    					if (d instanceof FunctionOrValue) {
    						if (((FunctionOrValue) d).isLate()) {
    							lt.visit(this);
    							return; //NOTE: EARLY EXIT!!
    						}
    					}
    				}
    			}
    		}
    	}
    	super.visit(that);
    }

    @Override
    public void visit(Tree.AssignmentOp that) {
        super.visit(that);
        if ( inBody() ) {
        	Tree.Term lt = that.getLeftTerm();
			if (lt instanceof Tree.MemberOrTypeExpression &&
					that.getRightTerm() instanceof Tree.This) {
        		Declaration d = ((Tree.MemberOrTypeExpression) lt).getDeclaration();
        		if (d instanceof FunctionOrValue) {
        			if (((FunctionOrValue) d).isLate()) {
        				return; //NOTE: EARLY EXIT!!
        			}
        		}
        	}
            checkSelfReference(that, that.getRightTerm());    
        }
    }

    @Override
    public void visit(Tree.BinaryOperatorExpression that) {
        super.visit(that);
        if ( inBody() && !(that instanceof Tree.AssignmentOp) ) {
            checkSelfReference(that, that.getLeftTerm());
            checkSelfReference(that, that.getRightTerm());
        }
    }

    @Override
    public void visit(Tree.UnaryOperatorExpression that) {
        super.visit(that);
        if ( inBody() && !(that instanceof Tree.OfOp) ) {
            checkSelfReference(that, that.getTerm());
        }
    }

    @Override
    public void visit(Tree.WithinOp that) {
        super.visit(that);
        if ( inBody() ) {
            checkSelfReference(that, that.getTerm());
            checkSelfReference(that, that.getLowerBound());
            checkSelfReference(that, that.getUpperBound());
        }
    }

    @Override
    public void visit(Tree.ExpressionComprehensionClause that) {
        super.visit(that);
        if ( inBody() ) {
            Tree.Expression e = that.getExpression();
            if (e!=null) {
                checkSelfReference(that, e.getTerm());
            }
        }
    }

    @Override
    public void visit(Tree.ListedArgument that) {
        super.visit(that);
        if ( inBody() ) {
            Tree.Expression e = that.getExpression();
            if (e!=null) {
                checkSelfReference(that, e.getTerm());
            }
        }
    }

    @Override
    public void visit(Tree.SpreadArgument that) {
        super.visit(that);
        if ( inBody() ) {
            Tree.Expression e = that.getExpression();
            if (e!=null) {
            	checkSelfReference(that, e.getTerm());
            }
        }
    }
    
    @Override
    public void visit(Tree.StringTemplate that) {
        super.visit(that);
        if ( inBody() ) {
            for (Tree.Expression e: that.getExpressions()) {
                if (e!=null) {
                    checkSelfReference(e, e.getTerm());
                }
            }
        }
    }

}
