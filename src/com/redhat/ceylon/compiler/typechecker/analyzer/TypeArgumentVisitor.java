package com.redhat.ceylon.compiler.typechecker.analyzer;

import java.util.List;

import com.redhat.ceylon.compiler.typechecker.model.ClassOrInterface;
import com.redhat.ceylon.compiler.typechecker.model.Declaration;
import com.redhat.ceylon.compiler.typechecker.model.MethodOrValue;
import com.redhat.ceylon.compiler.typechecker.model.ProducedType;
import com.redhat.ceylon.compiler.typechecker.model.TypeParameter;
import com.redhat.ceylon.compiler.typechecker.model.TypedDeclaration;
import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.compiler.typechecker.tree.Visitor;

/**
 * Validates the position in which covariant and contravariant
 * type parameters appear in the schemas of declarations.
 * 
 * @author Gavin King
 *
 */
public class TypeArgumentVisitor extends Visitor {
    
    private boolean contravariant = false;
    private Declaration parameterizedDeclaration;
    
    private void flip() {
        contravariant = !contravariant;
    }
        
    @Override public void visit(Tree.TypeConstraint that) {
        super.visit(that);
        TypeParameter dec = that.getDeclarationModel();
        if (dec!=null) {
            parameterizedDeclaration = dec.getDeclaration();
            flip();
            if (that.getSatisfiedTypes()!=null) {
                for (Tree.Type type: 
                        that.getSatisfiedTypes().getTypes()) {
                    check(type, false, null);
                }
            }
            flip();
            parameterizedDeclaration = null;
        }
    }
        
    @Override public void visit(Tree.TypedDeclaration that) {
        super.visit(that);
        TypedDeclaration dec = that.getDeclarationModel();
		if (!(that instanceof Tree.Variable)) {
            check(that.getType(), dec.isVariable(), dec);
        }
        if (dec.isParameter()) {
        	flip();
            boolean topLevel = 
                    parameterizedDeclaration==null; //i.e. toplevel parameter in a parameter declaration
            if (topLevel) {
            	parameterizedDeclaration = 
            	        ((MethodOrValue) dec).getInitializerParameter()
            	                .getDeclaration();
            }
			check(that.getType(), false, 
			        parameterizedDeclaration);
			super.visit(that);
			if (topLevel) {
				parameterizedDeclaration = null;
			}
        	flip();
        }
    }
    
    @Override public void visit(Tree.ClassOrInterface that) {
        super.visit(that);
        if (that.getSatisfiedTypes()!=null) {
            for (Tree.Type type: 
                    that.getSatisfiedTypes().getTypes()) {
                check(type, false, null);
            }
        }
    }
    
    @Override public void visit(Tree.ClassDeclaration that) {
        super.visit(that);
        if (that.getClassSpecifier()!=null) {
            check(that.getClassSpecifier().getType(), false, null);
        }
    }
    
    @Override public void visit(Tree.InterfaceDeclaration that) {
        super.visit(that);
        if (that.getTypeSpecifier()!=null) {
            check(that.getTypeSpecifier().getType(), false, null);
        }
    }
    
    @Override public void visit(Tree.TypeAliasDeclaration that) {
        super.visit(that);
        if (that.getTypeSpecifier()!=null) {
            check(that.getTypeSpecifier().getType(), false, null);
        }
    }
    
    @Override public void visit(Tree.AnyClass that) {
        super.visit(that);
        if (that.getExtendedType()!=null) {
            check(that.getExtendedType().getType(), false, null);
        }
    }
    
    @Override public void visit(Tree.ObjectDefinition that) {
        super.visit(that);
        if (that.getExtendedType()!=null) {
            check(that.getExtendedType().getType(), false, null);
        }
        if (that.getSatisfiedTypes()!=null) {
            for (Tree.Type type: 
                    that.getSatisfiedTypes().getTypes()) {
                check(type, false, null);
            }
        }
    }
    
    @Override public void visit(Tree.ObjectExpression that) {
        super.visit(that);
        if (that.getExtendedType()!=null) {
            check(that.getExtendedType().getType(), false, null);
        }
        if (that.getSatisfiedTypes()!=null) {
            for (Tree.Type type: 
                    that.getSatisfiedTypes().getTypes()) {
                check(type, false, null);
            }
        }
    }
    
    private ClassOrInterface constructorClass;
    
    @Override public void visit(Tree.Constructor that) {
        ClassOrInterface occ = constructorClass;
        constructorClass = 
                that.getDeclarationModel()
                    .getExtendedTypeDeclaration();
        super.visit(that);
        constructorClass = occ;
    }
    
//    @Override public void visit(Tree.FunctionArgument that) {}

    private void check(Tree.Type that, boolean variable, 
            Declaration d) {
        if (that!=null) {
            check(that.getTypeModel(), variable, d, that);
        }
    }

    private void check(ProducedType type, boolean variable, 
            Declaration d, Node that) {
        if (d==null || d.isShared() || 
                d.getOtherInstanceAccess()) {
            if (type!=null) {
                List<TypeParameter> errors = 
                        type.checkVariance(!contravariant && !variable, 
                                contravariant && !variable, 
                                parameterizedDeclaration);
                displayErrors(that, type, errors);
            }
        }
    }

    private void displayErrors(Node that, ProducedType type,
            List<TypeParameter> errors) {
        for (TypeParameter tp: errors) {
            if (constructorClass==null || !
                    constructorClass.getTypeParameters()
                            .contains(tp)) {
                String var; String loc;
                if (tp.isContravariant()) {
                    var = "contravariant ('in')";
                    loc = "covariant or invariant";
                }
                else if (tp.isCovariant()) {
                    var = "covariant ('out')";
                    loc = "contravariant or invariant";
                }
                else {
                    throw new RuntimeException();
                }
                that.addError(var + " type parameter '" + tp.getName() + 
                        "' of '" + tp.getDeclaration().getName() +
                        "' appears in " + loc + " location in type: '" + 
                        type.getProducedTypeName(that.getUnit()) + "'");
            }
        }
    }
    
}