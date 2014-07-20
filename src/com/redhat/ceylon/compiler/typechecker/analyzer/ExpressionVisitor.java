package com.redhat.ceylon.compiler.typechecker.analyzer;

import static com.redhat.ceylon.compiler.typechecker.analyzer.Util.checkAssignable;
import static com.redhat.ceylon.compiler.typechecker.analyzer.Util.checkAssignableWithWarning;
import static com.redhat.ceylon.compiler.typechecker.analyzer.Util.checkCallable;
import static com.redhat.ceylon.compiler.typechecker.analyzer.Util.checkIsExactly;
import static com.redhat.ceylon.compiler.typechecker.analyzer.Util.checkSupertype;
import static com.redhat.ceylon.compiler.typechecker.analyzer.Util.declaredInPackage;
import static com.redhat.ceylon.compiler.typechecker.analyzer.Util.eliminateParensAndWidening;
import static com.redhat.ceylon.compiler.typechecker.analyzer.Util.getTypeArguments;
import static com.redhat.ceylon.compiler.typechecker.analyzer.Util.getTypeDeclaration;
import static com.redhat.ceylon.compiler.typechecker.analyzer.Util.getTypeMember;
import static com.redhat.ceylon.compiler.typechecker.analyzer.Util.getTypedDeclaration;
import static com.redhat.ceylon.compiler.typechecker.analyzer.Util.getTypedMember;
import static com.redhat.ceylon.compiler.typechecker.analyzer.Util.hasError;
import static com.redhat.ceylon.compiler.typechecker.analyzer.Util.inLanguageModule;
import static com.redhat.ceylon.compiler.typechecker.analyzer.Util.isIndirectInvocation;
import static com.redhat.ceylon.compiler.typechecker.analyzer.Util.typeDescription;
import static com.redhat.ceylon.compiler.typechecker.analyzer.Util.typeNamesAsIntersection;
import static com.redhat.ceylon.compiler.typechecker.model.SiteVariance.IN;
import static com.redhat.ceylon.compiler.typechecker.model.SiteVariance.OUT;
import static com.redhat.ceylon.compiler.typechecker.model.Util.addToIntersection;
import static com.redhat.ceylon.compiler.typechecker.model.Util.addToUnion;
import static com.redhat.ceylon.compiler.typechecker.model.Util.findMatchingOverloadedClass;
import static com.redhat.ceylon.compiler.typechecker.model.Util.getContainingClassOrInterface;
import static com.redhat.ceylon.compiler.typechecker.model.Util.getOuterClassOrInterface;
import static com.redhat.ceylon.compiler.typechecker.model.Util.intersectionOfSupertypes;
import static com.redhat.ceylon.compiler.typechecker.model.Util.intersectionType;
import static com.redhat.ceylon.compiler.typechecker.model.Util.isAbstraction;
import static com.redhat.ceylon.compiler.typechecker.model.Util.isOverloadedVersion;
import static com.redhat.ceylon.compiler.typechecker.model.Util.isTypeUnknown;
import static com.redhat.ceylon.compiler.typechecker.model.Util.producedType;
import static com.redhat.ceylon.compiler.typechecker.model.Util.unionType;
import static com.redhat.ceylon.compiler.typechecker.tree.Util.MISSING_NAME;
import static com.redhat.ceylon.compiler.typechecker.tree.Util.hasUncheckedNulls;
import static com.redhat.ceylon.compiler.typechecker.tree.Util.name;
import static java.util.Collections.emptyList;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.redhat.ceylon.compiler.typechecker.model.Class;
import com.redhat.ceylon.compiler.typechecker.model.ClassOrInterface;
import com.redhat.ceylon.compiler.typechecker.model.Declaration;
import com.redhat.ceylon.compiler.typechecker.model.Functional;
import com.redhat.ceylon.compiler.typechecker.model.Generic;
import com.redhat.ceylon.compiler.typechecker.model.Interface;
import com.redhat.ceylon.compiler.typechecker.model.IntersectionType;
import com.redhat.ceylon.compiler.typechecker.model.Method;
import com.redhat.ceylon.compiler.typechecker.model.MethodOrValue;
import com.redhat.ceylon.compiler.typechecker.model.Module;
import com.redhat.ceylon.compiler.typechecker.model.NothingType;
import com.redhat.ceylon.compiler.typechecker.model.Package;
import com.redhat.ceylon.compiler.typechecker.model.Parameter;
import com.redhat.ceylon.compiler.typechecker.model.ParameterList;
import com.redhat.ceylon.compiler.typechecker.model.ProducedReference;
import com.redhat.ceylon.compiler.typechecker.model.ProducedType;
import com.redhat.ceylon.compiler.typechecker.model.ProducedTypedReference;
import com.redhat.ceylon.compiler.typechecker.model.Scope;
import com.redhat.ceylon.compiler.typechecker.model.Setter;
import com.redhat.ceylon.compiler.typechecker.model.TypeAlias;
import com.redhat.ceylon.compiler.typechecker.model.TypeDeclaration;
import com.redhat.ceylon.compiler.typechecker.model.TypeParameter;
import com.redhat.ceylon.compiler.typechecker.model.TypedDeclaration;
import com.redhat.ceylon.compiler.typechecker.model.UnionType;
import com.redhat.ceylon.compiler.typechecker.model.Unit;
import com.redhat.ceylon.compiler.typechecker.model.UnknownType;
import com.redhat.ceylon.compiler.typechecker.model.Value;
import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.PositionalArgument;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.TypeVariance;
import com.redhat.ceylon.compiler.typechecker.tree.Visitor;

/**
 * Third and final phase of type analysis.
 * Finally visit all expressions and determine their types.
 * Use type inference to assign types to declarations with
 * the local modifier. Finally, assigns types to the 
 * associated model objects of declarations declared using
 * the local modifier.
 * 
 * @author Gavin King
 *
 */
public class ExpressionVisitor extends Visitor {
    
    private Tree.Type returnType;
    private Tree.Expression switchExpression;
    private Declaration returnDeclaration;
    private boolean isCondition;
    private boolean dynamic;
    private boolean inExtendsClause = false;

    private Unit unit;
    
    @Override public void visit(Tree.CompilationUnit that) {
        unit = that.getUnit();
        super.visit(that);
    }
        
    private Declaration beginReturnDeclaration(Declaration d) {
        Declaration od = returnDeclaration;
        returnDeclaration = d;
        return od;
    }
    
    private void endReturnDeclaration(Declaration od) {
        returnDeclaration = od;
    }
    
    private Tree.Type beginReturnScope(Tree.Type t) {
        Tree.Type ort = returnType;
        returnType = t;
        if (returnType instanceof Tree.FunctionModifier || 
                returnType instanceof Tree.ValueModifier) {
            returnType.setTypeModel( unit.getNothingDeclaration().getType() );
        }
        return ort;
    }
    
    private void endReturnScope(Tree.Type t, TypedDeclaration td) {
        if (returnType instanceof Tree.FunctionModifier || 
                returnType instanceof Tree.ValueModifier) {
            td.setType( returnType.getTypeModel() );
        }
        returnType = t;
    }
    
    @Override public void visit(Tree.FunctionArgument that) {
        Tree.Expression e = that.getExpression();
        if (e==null) {
            Tree.Type rt = beginReturnScope(that.getType());           
            Declaration od = beginReturnDeclaration(that.getDeclarationModel());
            super.visit(that);
            endReturnDeclaration(od);
            endReturnScope(rt, that.getDeclarationModel());
        }
        else {
            super.visit(that);
            ProducedType t = unit.denotableType(e.getTypeModel());
            that.getDeclarationModel().setType(t);
            //if (that.getType() instanceof Tree.FunctionModifier) {
                that.getType().setTypeModel(t);
            /*}
            else {
                checkAssignable(t, that.getType().getTypeModel(), e, 
                        "expression type must be assignable to specified return type");
            }*/
            if (that.getType() instanceof Tree.VoidModifier &&
                    !isSatementExpression(e)) {
                e.addError("anonymous function is declared void so specified expression must be a statement");
            }
        }
        if (that.getType() instanceof Tree.VoidModifier) {
            ProducedType vt = unit.getType(unit.getAnythingDeclaration());
            that.getDeclarationModel().setType(vt);            
        }
        that.setTypeModel(that.getDeclarationModel()
                .getTypedReference()
                .getFullType());
    }
    
    @Override public void visit(Tree.ExpressionComprehensionClause that) {
        super.visit(that);
        that.setTypeModel(that.getExpression().getTypeModel());
        that.setFirstTypeModel(unit.getNothingDeclaration().getType());
    }
    
    @Override public void visit(Tree.ForComprehensionClause that) {
        super.visit(that);
        that.setPossiblyEmpty(true);
        Tree.ComprehensionClause cc = that.getComprehensionClause();
        if (cc!=null) {
            that.setTypeModel(cc.getTypeModel());
            Tree.ForIterator fi = that.getForIterator();
            if (fi!=null) {
                Tree.SpecifierExpression se = fi.getSpecifierExpression();
                if (se!=null) {
                    Tree.Expression e = se.getExpression();
                    if (e!=null) {
                        ProducedType it = e.getTypeModel();
                        if (it!=null) {
                            ProducedType et = unit.getIteratedType(it);
                            boolean nonemptyIterable = et!=null &&
                                    it.isSubtypeOf(unit.getNonemptyIterableType(et));
                            that.setPossiblyEmpty(!nonemptyIterable || 
                                    cc.getPossiblyEmpty());
                            ProducedType firstType = unionType(unit.getFirstType(it), 
                                    cc.getFirstTypeModel(), unit);
                            that.setFirstTypeModel(firstType);
                        }
                    }
                }
            }
        }
    }
    
    @Override public void visit(Tree.IfComprehensionClause that) {
        super.visit(that);
        that.setPossiblyEmpty(true);
        that.setFirstTypeModel(unit.getType(unit.getNullDeclaration()));
        Tree.ComprehensionClause cc = that.getComprehensionClause();
        if (cc!=null) {
            that.setTypeModel(cc.getTypeModel());
        }
    }
    
    @Override public void visit(Tree.Variable that) {
        super.visit(that);
        if (that.getSpecifierExpression()!=null) {
            inferType(that, that.getSpecifierExpression());
            if (that.getType()!=null) {
                ProducedType t = that.getType().getTypeModel();
                if (!isTypeUnknown(t)) {
                    checkType(t, that.getSpecifierExpression());
                }
            }
        }
    }
    
    @Override public void visit(Tree.ConditionList that) {
        if (that.getConditions().isEmpty()) {
            that.addError("empty condition list");
        }
        super.visit(that);
    }

    @Override public void visit(Tree.ResourceList that) {
        if (that.getResources().isEmpty()) {
            that.addError("empty resource list");
        }
        super.visit(that);
    }

    private void initOriginalDeclaration(Tree.Variable that) {
        if (that.getType() instanceof Tree.SyntheticVariable) {
            Tree.BaseMemberExpression bme = (Tree.BaseMemberExpression) that
                    .getSpecifierExpression().getExpression().getTerm();
            ((TypedDeclaration) that.getDeclarationModel())
                .setOriginalDeclaration((TypedDeclaration) bme.getDeclaration());
        }
    }
    
    @Override public void visit(Tree.IsCondition that) {
        //don't recurse to the Variable, since we don't
        //want to check that the specifier expression is
        //assignable to the declared variable type
        //(nor is it possible to infer the variable type)
        isCondition=true;
        Tree.Type t = that.getType();
        if (t!=null) {
            t.visit(this);
        }
        isCondition=false;
        Tree.Variable v = that.getVariable();
        ProducedType type = t==null ? null : t.getTypeModel();
        if (v!=null) {
//            if (type!=null && !that.getNot()) {
//                v.getType().setTypeModel(type);
//                v.getDeclarationModel().setType(type);
//            }
            //v.getType().visit(this);
            Tree.SpecifierExpression se = v.getSpecifierExpression();
            ProducedType knownType;
            if (se==null) {
                knownType = null;
            }
            else {
                se.visit(this);
                checkReferenceIsNonVariable(v, se);
                /*checkAssignable( se.getExpression().getTypeModel(), 
                        getOptionalType(getObjectDeclaration().getType()), 
                        se.getExpression(), 
                        "expression may not be of void type");*/
                initOriginalDeclaration(v);
                //this is a bit ugly (the parser sends us a SyntheticVariable
                //instead of the real StaticType which it very well knows!)
                Tree.Expression e = se.getExpression();
                knownType = e==null ? null : e.getTypeModel();
                //TODO: what to do here in case of !is
                if (knownType!=null) {
                    String help = " (expression is already of the specified type)";
                    if (that.getNot()) {
                        if (intersectionType(type,knownType, unit).isNothing()) {
                            that.addError("does not narrow type: intersection of " + type.getProducedTypeName(unit) + 
                                    " and " + knownType.getProducedTypeName(unit) + " is empty" + help);
                        }
                        else if (knownType.isSubtypeOf(type)) {
                            that.addError("tests assignability to Nothing type: " + knownType.getProducedTypeName(unit) + 
                                    " is a subtype of " + type.getProducedTypeName(unit));
                        }
                    } 
                    else {
                        if (knownType.isSubtypeOf(type)) {
                            that.addError("does not narrow type: " + knownType.getProducedTypeName(unit) + 
                                    " is a subtype of " + type.getProducedTypeName(unit) + help);
                        }
                    }
                }
            }
            defaultTypeToAnything(v);
            if (knownType==null) {
                knownType = unit.getType(unit.getAnythingDeclaration()); //or should we use unknown?
            }
            
            ProducedType it = narrow(that, type, knownType);
            //check for disjointness
            if (it.getDeclaration() instanceof NothingType) {
                if (that.getNot()) {
                    /*that.addError("tests assignability to Nothing type: " +
                            knownType.getProducedTypeName(unit) + " is a subtype of " + 
                            type.getProducedTypeName(unit));*/
                }
                else {
                    that.addError("tests assignability to Nothing type: intersection of " +
                            knownType.getProducedTypeName(unit) + " and " + 
                            type.getProducedTypeName(unit) + " is empty");
                }
            }
            //do this *after* checking for disjointness!
            knownType=unit.denotableType(knownType);
            //now recompute the narrowed type!
            it = narrow(that, type, knownType);
            
            v.getType().setTypeModel(it);
            v.getDeclarationModel().setType(it);
        }
    }
    
	private ProducedType narrow(Tree.IsCondition that, ProducedType type,
            ProducedType knownType) {
	    ProducedType it;
	    if (that.getNot()) {
	        //a !is condition, narrow to complement
	        it = unit.denotableType(knownType.minus(type));
	    }
	    else {
	        //narrow to the intersection of the outer type 
	        //and the type specified in the condition
	        it = intersectionType(type, knownType, that.getUnit());
	    }
	    return it;
    }
    
    @Override public void visit(Tree.SatisfiesCondition that) {
        super.visit(that);
        that.addUnsupportedError("satisfies conditions not yet supported");
    }
    
    @Override public void visit(Tree.ExistsOrNonemptyCondition that) {
        //don't recurse to the Variable, since we don't
        //want to check that the specifier expression is
        //assignable to the declared variable type
        //(nor is it possible to infer the variable type)
        ProducedType t = null;
        Node n = that;
        Tree.Term term = null;
        Tree.Variable v = that.getVariable();
        if (v!=null) {
            //v.getType().visit(this);
            defaultTypeToAnything(v);
            Tree.SpecifierExpression se = v.getSpecifierExpression();
            if (se!=null && se.getExpression()!=null) {
                se.visit(this);
                if (that instanceof Tree.ExistsCondition) {
                    inferDefiniteType(v, se);
                    checkOptionalType(v, se);
                }
                else if (that instanceof Tree.NonemptyCondition) {
                    inferNonemptyType(v, se);
                    checkEmptyOptionalType(v, se);
                }
                t = se.getExpression().getTypeModel();
                n = v;
                checkReferenceIsNonVariable(v, se);
                initOriginalDeclaration(v);
                term = se.getExpression().getTerm();
            }
        }
        if (that instanceof Tree.ExistsCondition) {
            checkOptional(t, term, n);
        }
        else if (that instanceof Tree.NonemptyCondition) {
            checkEmpty(t, term, n);
        }
    }

    private void defaultTypeToAnything(Tree.Variable v) {
        /*if (v.getType().getTypeModel()==null) {
            v.getType().setTypeModel( getAnythingDeclaration().getType() );
        }*/
        v.getType().visit(this);
        if (v.getDeclarationModel().getType()==null) {
            v.getDeclarationModel().setType( defaultType() );
        }
    }

    private void checkReferenceIsNonVariable(Tree.Variable v,
            Tree.SpecifierExpression se) {
        if (v.getType() instanceof Tree.SyntheticVariable) {
            Tree.BaseMemberExpression term = (Tree.BaseMemberExpression) se.getExpression().getTerm();
			checkReferenceIsNonVariable(term, false);
        }
    }

    private void checkReferenceIsNonVariable(Tree.BaseMemberExpression ref,
    		boolean isSwitch) {
        Declaration d = ref.getDeclaration();
        if (d!=null) {
        	int code = isSwitch ? 3101:3100;
            String help=" (assign to a new local value to narrow type)";
            if (!(d instanceof Value)) {
                ref.addError("referenced declaration is not a value: " + 
                        d.getName(unit), code);
            }
            else if (isNonConstant(d)) {
                ref.addError("referenced value is non-constant: " + 
                        d.getName(unit) + help, code);
            }
            else if (d.isDefault() || d.isFormal()) {
                ref.addError("referenced value may be refined by a non-constant value: " + 
                        d.getName(unit) + help, code);
            }
        }
    }

    private boolean isNonConstant(Declaration d) {
        return d instanceof Value && 
                (((Value) d).isVariable() || ((Value) d).isTransient());
    }
    
    private void checkEmpty(ProducedType t, Tree.Term term, Node n) {
        /*if (t==null) {
            n.addError("expression must be a type with fixed size: type not known");
        }
        else*/ if (!isTypeUnknown(t) && !unit.isPossiblyEmptyType(t)) {
            term.addError("expression must be a possibly-empty type: " + 
                    t.getProducedTypeName(unit) + " is not possibly-empty");
        }
    }
    
    private void checkOptional(ProducedType t, Tree.Term term, Node n) {
        /*if (t==null) {
            n.addError("expression must be of optional type: type not known");
        }
        else*/ 
        if (!isTypeUnknown(t) && !unit.isOptionalType(t) && 
                !hasUncheckedNulls(term)) {
            term.addError("expression must be of optional type: " +
                    t.getProducedTypeName(unit) + " is not optional");
        }
    }

    @Override public void visit(Tree.BooleanCondition that) {
        super.visit(that);
        if (that.getExpression()!=null) {
            ProducedType t = that.getExpression().getTypeModel();
            if (!isTypeUnknown(t)) {
                checkAssignable(t, unit.getType(unit.getBooleanDeclaration()), that, 
                        "expression must be of boolean type");
            }
        }
    }

    @Override public void visit(Tree.Resource that) {
        super.visit(that);
        ProducedType t = null;
        Node typedNode = null;
        Tree.Expression e = that.getExpression();
        Tree.Variable v = that.getVariable();
        if (e!=null) {
            t = e.getTypeModel();
            typedNode = e;
            
        } 
        else if (v!=null) {
            t = v.getType().getTypeModel();
            typedNode = v.getType();
            Tree.SpecifierExpression se = v.getSpecifierExpression();
            if (se==null) {
                v.addError("missing resource specifier");
            }
            else {
                e = se.getExpression();
                if (typedNode instanceof Tree.ValueModifier) {
                    typedNode = se.getExpression();
                }
            }
        }
        else {
            that.addError("missing resource expression");
        }
        if (typedNode!=null) {
            if (!isTypeUnknown(t)) {
                if (e != null) {
                    if (Util.isInstantiationExpression(e)) {
                        checkAssignable(t, unit.getType(unit.getDestroyableDeclaration()), typedNode, 
                                "resource must be destroyable");
                    } else {
                        checkAssignable(t, unit.getType(unit.getObtainableDeclaration()), typedNode, 
                                "resource must be obtainable");
                    }
                }
            }
        }
    }
    
    @Override public void visit(Tree.ForIterator that) {
        super.visit(that);
        Tree.SpecifierExpression se = that.getSpecifierExpression();
        if (se!=null) {
            Tree.Expression e = se.getExpression();
            if (e!=null) {
                ProducedType et = e.getTypeModel();
                if (!isTypeUnknown(et)) {
                    if (!unit.isIterableType(et)) {
                        se.addError("expression is not iterable: " + 
                                et.getProducedTypeName(unit) + 
                                " is not a subtype of Iterable");
                    }
                    else if (et!=null && unit.isEmptyType(et)) {
                        se.addError("iterated expression is definitely empty");
                    }
                }
            }
        }
    }

    @Override public void visit(Tree.ValueIterator that) {
        super.visit(that);
        Tree.Variable v = that.getVariable();
        if (v!=null) {
            inferContainedType(v, that.getSpecifierExpression());
            checkContainedType(v, that.getSpecifierExpression());
        }
    }

    @Override public void visit(Tree.KeyValueIterator that) {
        super.visit(that);
        Tree.SpecifierExpression se = that.getSpecifierExpression();
        if (se!=null) {
            Tree.Expression e = se.getExpression();
            if (e!=null) {
                ProducedType et = e.getTypeModel();
                if (!isTypeUnknown(et)) {
                    ProducedType it = unit.getIteratedType(et);
                    if (it!=null && !isTypeUnknown(it)) {
                        if (!unit.isEntryType(it)) {
                            se.addError("iterated element type is not an entry type: " + 
                                    it.getProducedTypeName(unit) + 
                                    " is not a subtype of Entry");
                        }
                    }
                }
            }
        }
        Tree.Variable kv = that.getKeyVariable();
        Tree.Variable vv = that.getValueVariable();
        if (kv!=null && vv!=null) {
            inferKeyType(kv, that.getSpecifierExpression());
            inferValueType(vv, that.getSpecifierExpression());
            checkKeyValueType(kv, vv, that.getSpecifierExpression());
        }
    }
    
    @Override public void visit(Tree.AttributeDeclaration that) {
        super.visit(that);
        Value dec = that.getDeclarationModel();
        Tree.SpecifierOrInitializerExpression sie = that.getSpecifierOrInitializerExpression();
        inferType(that, sie);
        Tree.Type type = that.getType();
        if (type!=null) {
        	ProducedType t = type.getTypeModel();
        	if (type instanceof Tree.LocalModifier) {
        		if (dec.isParameter()) {
        			type.addError("parameter may not have inferred type: " + 
        					dec.getName());
        		}
        		else {
        			if (sie==null) {
        				type.addError("value must specify an explicit type or definition", 200);
        			}
        			else if (isTypeUnknown(t)) {
        			    if (!hasError(sie)) {
        			        type.addError("value type could not be inferred");
        			    }
        			}
        		}
        	}
        	if (!isTypeUnknown(t)) {
        		checkType(t, dec.getName(), sie, 2100);
        	}
        }
    	Setter setter = dec.getSetter();
    	if (setter!=null) {
    		setter.getParameter().getModel().setType(dec.getType());
    	}
    }
    
    @Override public void visit(Tree.ParameterizedExpression that) {
        super.visit(that);
        Tree.Term p = that.getPrimary();
        if (p instanceof Tree.QualifiedMemberExpression ||
            p instanceof Tree.BaseMemberExpression) {
            Tree.MemberOrTypeExpression mte = (Tree.MemberOrTypeExpression) p;
            if (p.getTypeModel()!=null && mte.getDeclaration()!=null) {
                ProducedType pt = p.getTypeModel();
                if (pt!=null) {
                    for (int j=0; j<that.getParameterLists().size(); j++) {
                        Tree.ParameterList pl = that.getParameterLists().get(j);
                        ProducedType ct = pt.getSupertype(unit.getCallableDeclaration());
                        String refName = mte.getDeclaration().getName();
                        if (ct==null) {                        
                            pl.addError("no matching parameter list in referenced declaration: " + 
                                    refName);
                        }
                        else if (ct.getTypeArgumentList().size()>=2) {
                            ProducedType tupleType = ct.getTypeArgumentList().get(1);
                            List<ProducedType> argTypes = unit.getTupleElementTypes(tupleType);
                            boolean variadic = unit.isTupleLengthUnbounded(tupleType);
                            boolean atLeastOne = unit.isTupleVariantAtLeastOne(tupleType);
                            List<Tree.Parameter> params = pl.getParameters();
                            if (argTypes.size()!=params.size()) {
                                pl.addError("wrong number of declared parameters: " + refName  + 
                                        " has " + argTypes.size() + " parameters");
                            }
                            for (int i=0; i<argTypes.size()&&i<params.size(); i++) {
                                ProducedType at = argTypes.get(i);
                                Tree.Parameter param = params.get(i);
                                ProducedType t = param.getParameterModel().getModel()
                                        .getTypedReference()
                                        .getFullType();
                                checkAssignable(at, t, param, "type of parameter " + param.getParameterModel().getName() + 
                                        " must be a supertype of parameter type in declaration of " + refName);
                            }
                            if (!params.isEmpty()) {
                                Tree.Parameter lastParam = params.get(params.size()-1);
                                boolean refSequenced = lastParam.getParameterModel().isSequenced();
                                boolean refAtLeastOne = lastParam.getParameterModel().isAtLeastOne();
                                if (refSequenced && !variadic) {
                                    lastParam.addError("parameter list in declaration of " + refName + 
                                            " does not have a variadic parameter");
                                }
                                else if (!refSequenced && variadic) {
                                    lastParam.addError("parameter list in declaration of " + refName + 
                                            " has a variadic parameter");
                                }
                                else if (refAtLeastOne && !atLeastOne) {
                                    lastParam.addError("variadic parameter in declaration of " + refName + 
                                            " is optional");
                                }
                                else if (!refAtLeastOne && atLeastOne) {
                                    lastParam.addError("variadic parameter in declaration of " + refName + 
                                            " is not optional");
                                }
                            }
                            pt = ct.getTypeArgumentList().get(0);
                            that.setTypeModel(pt);
                        }
                    }
                }
            }
        }
    }
    
    @Override public void visit(Tree.SpecifierStatement that) {
        super.visit(that);
        boolean hasParams = false;
        Tree.Term me = that.getBaseMemberExpression();
        while (me instanceof Tree.ParameterizedExpression) {
            hasParams = true;
            me = ((Tree.ParameterizedExpression) me).getPrimary();
        }
        assign(me);
        Tree.SpecifierExpression sie = that.getSpecifierExpression();
        if (me instanceof Tree.BaseMemberExpression) {
            Declaration d = that.getDeclaration();
            if (d instanceof TypedDeclaration) {
                if (that.getRefinement()) {
                    // interpret this specification as a 
                    // refinement of an inherited member
                    if (d instanceof Value) {
                        refineValue(that);
                    }
                    else if (d instanceof Method) {
                        refineMethod(that);
                    }
                    Tree.BaseMemberExpression bme = (Tree.BaseMemberExpression) me;
                    bme.setDeclaration(that.getDeclaration());
                }
                else if (d instanceof MethodOrValue) {
                    MethodOrValue mv = (MethodOrValue) d;
                    if (mv.isShortcutRefinement()) {
                        that.getBaseMemberExpression().addError("already specified: " + 
                                d.getName(unit));
                    }
                    else if (d.isToplevel() && !mv.isVariable() && !mv.isLate()) {
                        that.addError("cannot specify non-variable toplevel value here: " + 
                                d.getName(unit), 803);
                    }
                }
                if (hasParams && d instanceof Method && 
                        ((Method) d).isDeclaredVoid() && 
                        !isSatementExpression(sie.getExpression())) {
                    that.addError("function is declared void so specified expression must be a statement: " + 
                            d.getName(unit));
                }
                if (d instanceof Value && 
                        that.getSpecifierExpression() instanceof Tree.LazySpecifierExpression) {
                    ((Value) d).setTransient(true);
                }
                
                ProducedType t = that.getBaseMemberExpression().getTypeModel();
                if (that.getBaseMemberExpression()==me && d instanceof Method) {
                    //if the declaration of the method has
                    //defaulted parameters, we should ignore
                    //that when determining if the RHS is
                    //an acceptable implementation of the
                    //method
                    //TODO: this is a pretty nasty way to
                    //      handle the problem
                    t = eraseDefaultedParameters(t);
                }
                if (!isTypeUnknown(t)) {
                    checkType(t, d.getName(unit), sie, 2100);
                }
            }
            if (that.getBaseMemberExpression() instanceof Tree.ParameterizedExpression) {
                if (!(sie instanceof Tree.LazySpecifierExpression)) {
                    that.addError("functions with parameters must be specified using =>");
                }
            }
            else {
                if (sie instanceof Tree.LazySpecifierExpression && d instanceof Method) {
                    that.addError("functions without parameters must be specified using =");
                }
            }
        }
        else {
            me.addError("illegal specification statement: only a function or value may be specified");
        }
    }
    
    boolean isSatementExpression(Tree.Expression e) {
        if (e==null) {
            return false;
        }
        else {
            Tree.Term t = e.getTerm();
            return t instanceof Tree.InvocationExpression ||
                    t instanceof Tree.PostfixOperatorExpression ||
                    t instanceof Tree.AssignmentOp ||
                    t instanceof Tree.PrefixOperatorExpression;
        }
    }
    
    /*boolean isVoidMethodReference(Tree.Expression e) {
        //TODO: correctly handle multiple parameter lists!
        Tree.Term term = e.getTerm();
        ProducedType tm = term.getTypeModel();
        if (tm!=null && tm.isExactly(unit.getType(unit.getAnythingDeclaration()))) {
            if (term instanceof Tree.InvocationExpression) {
                Tree.InvocationExpression ie = (Tree.InvocationExpression) term;
                if (ie.getPrimary() instanceof Tree.MemberOrTypeExpression) {
                    Tree.MemberOrTypeExpression mte = (Tree.MemberOrTypeExpression) ie.getPrimary();
                    if (mte.getDeclaration() instanceof Functional) {
                        return ((Functional) mte.getDeclaration()).isDeclaredVoid();
                    }
                }
            }
        }
        return false;
    }*/

    private ProducedType eraseDefaultedParameters(ProducedType t) {
        ProducedType ct = t.getSupertype(unit.getCallableDeclaration());
        if (ct!=null) {
            List<ProducedType> typeArgs = ct.getTypeArgumentList();
            if (typeArgs.size()>=2) {
                ProducedType rt = typeArgs.get(0);
                ProducedType pts = typeArgs.get(1);
                List<ProducedType> argTypes = unit.getTupleElementTypes(pts);
                boolean variadic = unit.isTupleLengthUnbounded(pts);
                boolean atLeastOne = unit.isTupleVariantAtLeastOne(pts);
                if (variadic) {
                    ProducedType spt = argTypes.get(argTypes.size()-1);
                    argTypes.set(argTypes.size()-1, unit.getIteratedType(spt));
                }
                return producedType(unit.getCallableDeclaration(), rt, 
                        unit.getTupleType(argTypes, variadic, atLeastOne, -1));
            }
        }
        return t;
    }
    
    static ProducedReference getRefinedMember(MethodOrValue d, 
            ClassOrInterface classOrInterface) {
        ProducedType supertype = classOrInterface.getType()
                .getSupertype((TypeDeclaration) d.getContainer());
        return d.getProducedReference(supertype, 
                Collections.<ProducedType>emptyList());
    }
    
    private void refineValue(Tree.SpecifierStatement that) {
        Value sv = (Value) that.getRefined();
        Value v = (Value) that.getDeclaration();
        ProducedReference rv = getRefinedMember(sv,
                (ClassOrInterface) v.getContainer());
        v.setType(rv.getType());
    }

    private void refineMethod(Tree.SpecifierStatement that) {
        Method sm = (Method) that.getRefined();
        Method m = (Method) that.getDeclaration();
        ClassOrInterface ci = (ClassOrInterface) m.getContainer();
        ProducedReference rm = getRefinedMember(sm, ci);
        m.setType(rm.getType());
        List<Tree.ParameterList> tpls;
        Tree.Term me = that.getBaseMemberExpression();
        if (me instanceof Tree.ParameterizedExpression) {
            tpls = ((Tree.ParameterizedExpression) me).getParameterLists();
        }
        else {
            tpls = emptyList();
        }
        for (int i=0; i<sm.getParameterLists().size(); i++) {
            ParameterList pl = sm.getParameterLists().get(i);
            ParameterList l = m.getParameterLists().get(i);
            Tree.ParameterList tpl = tpls.size()<=i ? 
                    null : tpls.get(i);
            for (int j=0; j<pl.getParameters().size(); j++) {
                Parameter p = pl.getParameters().get(j);
                ProducedType pt = rm.getTypedParameter(p).getFullType();
                if (tpl==null || tpl.getParameters().size()<=j) {
                    Parameter vp = l.getParameters().get(j);
                    vp.getModel().setType(pt);
                }
                else {
                    Tree.Parameter tp = tpl.getParameters().get(j);
                    Parameter rp = tp.getParameterModel();
                    ProducedType rpt = rp.getModel()
                    		.getTypedReference()
                    		.getFullType();
                    checkIsExactly(rpt, pt, tp, 
                            "type of parameter " + rp.getName() + " of " + m.getName() + 
                            " declared by " + ci.getName() +
                            " is different to type of corresponding parameter " +
                            p.getName() + " of refined method " + sm.getName() + " of " + 
                            ((Declaration) sm.getContainer()).getName());
                }
            }
        }
    }
    
    @Override public void visit(Tree.TypeParameterDeclaration that) {
        super.visit(that);
        TypeParameter tpd = that.getDeclarationModel();
        ProducedType dta = tpd.getDefaultTypeArgument();
        if (dta!=null) {
            for (ProducedType st: tpd.getSatisfiedTypes()) {
                checkAssignable(dta, st, that.getTypeSpecifier().getType(), 
                        "default type argument does not satisfy type constraint");
            }
        }
    }
    
    @Override public void visit(Tree.ParameterDeclaration that) {
        super.visit(that);
        Tree.Type type = that.getTypedDeclaration().getType();
        if (type instanceof Tree.LocalModifier) {
            Parameter p = that.getParameterModel();
            type.setTypeModel(new UnknownType(unit).getType());
            type.addError("parameter may not have inferred type: " + 
                    p.getName());
        }
    }
    
    @Override public void visit(Tree.InitializerParameter that) {
        super.visit(that);
        MethodOrValue model = that.getParameterModel().getModel();
        if (model!=null) {
        	ProducedType type = 
        	        model.getTypedReference().getFullType();
        	if (type!=null && !isTypeUnknown(type)) {
        		checkType(type, that.getSpecifierExpression());
        	}
        }
    }
    
    private void checkType(ProducedType declaredType, 
            Tree.SpecifierOrInitializerExpression sie) {
        if (sie!=null && sie.getExpression()!=null) {
            ProducedType t = sie.getExpression().getTypeModel();
            if (!isTypeUnknown(t)) {
                checkAssignable(t, declaredType, sie, 
                        "specified expression must be assignable to declared type");
            }
        }
    }

    private void checkType(ProducedType declaredType, String name,
            Tree.SpecifierOrInitializerExpression sie, int code) {
        if (sie!=null && sie.getExpression()!=null) {
            ProducedType t = sie.getExpression().getTypeModel();
            if (!isTypeUnknown(t)) {
                checkAssignable(t, declaredType, sie, 
                        "specified expression must be assignable to declared type of " + name,
                        code);
            }
        }
    }

    private void checkFunctionType(ProducedType et, Tree.Type that, 
            Tree.SpecifierExpression se) {
        if (!isTypeUnknown(et)) {
            checkAssignable(et, that.getTypeModel(), se, 
                    "specified expression type must be assignable to declared return type",
                    2100);
        }
    }

    private void checkOptionalType(Tree.Variable var, 
            Tree.SpecifierExpression se) {
        if (var.getType()!=null) {
            ProducedType vt = var.getType().getTypeModel();
            Tree.Expression e = se.getExpression();
            if (se!=null && e!=null) {
                ProducedType set = e.getTypeModel();
                if (set!=null) {
                    if (!isTypeUnknown(vt) && !isTypeUnknown(set)) {
                        checkAssignable(unit.getDefiniteType(set), vt, se, 
                                "specified expression must be assignable to declared type");
                    }
                }
            }
        }
    }

    private void checkEmptyOptionalType(Tree.Variable var, 
            Tree.SpecifierExpression se) {
        if (var.getType()!=null) {
            ProducedType vt = var.getType().getTypeModel();
            Tree.Expression e = se.getExpression();
            if (se!=null && e!=null) {
                ProducedType set = e.getTypeModel();
                if (!isTypeUnknown(vt) && !isTypeUnknown(set)) {
                    checkType(unit.getOptionalType(unit.getPossiblyNoneType(vt)), se);
                }
            }
        }
    }

    private void checkContainedType(Tree.Variable var, 
            Tree.SpecifierExpression se) {
        if (var.getType()!=null) {
            ProducedType vt = var.getType().getTypeModel();
            if (!isTypeUnknown(vt)) {
                checkType(unit.getIterableType(vt), se);
            }
        }
    }

    private void checkKeyValueType(Tree.Variable key, Tree.Variable value, 
            Tree.SpecifierExpression se) {
        if (key.getType()!=null && value.getType()!=null) {
            ProducedType kt = key.getType().getTypeModel();
            ProducedType vt = value.getType().getTypeModel();
            if (!isTypeUnknown(kt) && !isTypeUnknown(vt)) {
                checkType(unit.getIterableType(unit.getEntryType(kt, vt)), se);
            }
        }
    }
    
    @Override public void visit(Tree.AttributeGetterDefinition that) {
        Tree.Type type = that.getType();
        Tree.Type rt = beginReturnScope(type);
        Value dec = that.getDeclarationModel();
        Declaration od = beginReturnDeclaration(dec);
        super.visit(that);
        endReturnScope(rt, dec);
        endReturnDeclaration(od);
        Setter setter = dec.getSetter();
        if (setter!=null) {
            setter.getParameter().getModel().setType(dec.getType());
        }
        if (type instanceof Tree.LocalModifier) {
            if (isTypeUnknown(type.getTypeModel())) {
                type.addError("getter type could not be inferred");
            }
        }
    }

    @Override public void visit(Tree.AttributeArgument that) {
        Tree.SpecifierExpression se = that.getSpecifierExpression();
        Tree.Type type = that.getType();
        if (se==null) {
            Tree.Type rt = beginReturnScope(type);
            Declaration od = beginReturnDeclaration(that.getDeclarationModel());
            super.visit(that);
            endReturnDeclaration(od);
            endReturnScope(rt, that.getDeclarationModel());
        }
        else {
            super.visit(that);
            inferType(that, se);
            if (type!=null) {
                ProducedType t = type.getTypeModel();
                if (!isTypeUnknown(t)) {
                    checkType(t, that.getDeclarationModel().getName(), se, 2100);
                }
            }
        }
        if (type instanceof Tree.LocalModifier) {
            if (isTypeUnknown(type.getTypeModel())) {
                if (se==null || !hasError(se)) {
                    Node node = type.getToken()==null ? that : type;
                    node.addError("argument type could not be inferred");
                }
            }
        }
    }

    @Override public void visit(Tree.AttributeSetterDefinition that) {
        Tree.Type rt = beginReturnScope(that.getType());
        Setter sd = that.getDeclarationModel();
        Declaration od = beginReturnDeclaration(sd);
        super.visit(that);
        endReturnDeclaration(od);
        endReturnScope(rt, sd);
        Tree.SpecifierExpression se = that.getSpecifierExpression();
        if (se!=null) {
            Tree.Expression e = se.getExpression();
            if (e!=null) {
                if (!isSatementExpression(e)) {
                    se.addError("function is declared void so specified expression must be a statement: " +
                            sd.getName());
                }
            }
        }
    }

    @Override public void visit(Tree.MethodDeclaration that) {
        super.visit(that);
        Tree.Type type = that.getType();
        Tree.SpecifierExpression se = that.getSpecifierExpression();
        if (se!=null) {
            Tree.Expression e = se.getExpression();
            if (e!=null) {
                ProducedType returnType = e.getTypeModel();
                inferFunctionType(that, returnType);
                if (type!=null && 
                        !(type instanceof Tree.DynamicModifier)) {
                    checkFunctionType(returnType, type, se);
                }
                if (type instanceof Tree.VoidModifier && 
                        !isSatementExpression(e)) {
                    se.addError("function is declared void so specified expression must be a statement: " +
                            that.getDeclarationModel().getName());
                }
            }
        }
        if (type instanceof Tree.LocalModifier) {
            if (isTypeUnknown(type.getTypeModel())) {
                if (se==null) {
                    type.addError("function must specify an explicit return type or definition", 200);
                }
                else if (!hasError(se)) {
                    type.addError("function type could not be inferred");
                }
            }
        }
    }

    @Override public void visit(Tree.MethodDefinition that) {
        Tree.Type type = that.getType();
        Tree.Type rt = beginReturnScope(type);
        Declaration od = beginReturnDeclaration(that.getDeclarationModel());
        super.visit(that);
        endReturnDeclaration(od);
        endReturnScope(rt, that.getDeclarationModel());
        if (type instanceof Tree.LocalModifier) {
            if (isTypeUnknown(type.getTypeModel())) {
                type.addError("function type could not be inferred");
            }
        }
    }

    @Override public void visit(Tree.MethodArgument that) {
        Tree.SpecifierExpression se = that.getSpecifierExpression();
        Method d = that.getDeclarationModel();
        Tree.Type type = that.getType();
        if (se==null) {
            Tree.Type rt = beginReturnScope(type);           
            Declaration od = beginReturnDeclaration(d);
            super.visit(that);
            endReturnDeclaration(od);
            endReturnScope(rt, d);
        }
        else {
            super.visit(that);
            Tree.Expression e = se.getExpression();
            if (e!=null) {
                ProducedType returnType = e.getTypeModel();
                inferFunctionType(that, returnType);
                if (type!=null && 
                        !(type instanceof Tree.DynamicModifier)) {
                    checkFunctionType(returnType, type, se);
                }
                if (d.isDeclaredVoid() && !isSatementExpression(e)) {
                    se.addError("functional argument is declared void so specified expression must be a statement: " + 
                            d.getName());
                }
            }
        }
        if (type instanceof Tree.LocalModifier) {
            if (isTypeUnknown(type.getTypeModel())) {
                if (se==null || hasError(type)) {
                    Node node = type.getToken()==null ? that : type;
                    node.addError("argument type could not be inferred");
                }
            }
        }
    }

    @Override public void visit(Tree.ClassDefinition that) {
        Tree.Type rt = beginReturnScope(new Tree.VoidModifier(that.getToken()));
        Declaration od = beginReturnDeclaration(that.getDeclarationModel());
        super.visit(that);
        endReturnDeclaration(od);
        endReturnScope(rt, null);
        validateEnumeratedSupertypes(that, that.getDeclarationModel());
    }
    
    @Override public void visit(Tree.ClassOrInterface that) {
        super.visit(that);
        validateEnumeratedSupertypeArguments(that, that.getDeclarationModel());
    }

    @Override public void visit(Tree.InterfaceDefinition that) {
        Tree.Type rt = beginReturnScope(null);
        Declaration od = beginReturnDeclaration(that.getDeclarationModel());
        super.visit(that);
        endReturnDeclaration(od);
        endReturnScope(rt, null);
        validateEnumeratedSupertypes(that, that.getDeclarationModel());
    }

    @Override public void visit(Tree.ObjectDefinition that) {
        Tree.Type rt = beginReturnScope(new Tree.VoidModifier(that.getToken()));
        Declaration od = beginReturnDeclaration(that.getDeclarationModel());
        super.visit(that);
        endReturnDeclaration(od);
        endReturnScope(rt, null);
        validateEnumeratedSupertypes(that, that.getAnonymousClass());
    }

    @Override public void visit(Tree.ObjectArgument that) {
        Tree.Type rt = beginReturnScope(new Tree.VoidModifier(that.getToken()));
        Declaration od = beginReturnDeclaration(that.getDeclarationModel());
        super.visit(that);
        endReturnDeclaration(od);
        endReturnScope(rt, null);
        validateEnumeratedSupertypes(that, that.getAnonymousClass());
    }
    
    @Override public void visit(Tree.ClassDeclaration that) {
        super.visit(that);
        Class alias = that.getDeclarationModel();
        Class c = alias.getExtendedTypeDeclaration();
        if (c!=null) {
            if (c.isAbstract()) {
                if (!alias.isFormal() && !alias.isAbstract()) {
                    that.addError("alias of abstract class must be annotated abstract", 310);
                }
            }
            if (c.isAbstraction()) {
                that.addError("class alias may not alias overloaded class");
            }
            else {
                //TODO: all this can be removed once the backend
                //      implements full support for the new class 
                //      alias stuff
                ProducedType at = alias.getExtendedType();
                ParameterList cpl = c.getParameterList();
                ParameterList apl = alias.getParameterList();
                if (cpl!=null && apl!=null) {
                    int cps = cpl.getParameters().size();
                    int aps = apl.getParameters().size();
                    if (cps!=aps) {
                        that.getParameterList()
                                .addUnsupportedError("wrong number of initializer parameters declared by class alias: " + 
                                        alias.getName());
                    }
                    
                    for (int i=0; i<cps && i<aps; i++) {
                        Parameter ap = apl.getParameters().get(i);
                        Parameter cp = cpl.getParameters().get(i);
                        ProducedType pt = at.getTypedParameter(cp).getType();
                        //TODO: properly check type of functional parameters!!
                        checkAssignableWithWarning(ap.getType(), pt, that, "alias parameter " + 
                                ap.getName() + " must be assignable to corresponding class parameter " +
                                cp.getName());
                    }
                    
                    //temporary restrictions
                    if (that.getClassSpecifier()!=null) {
                        Tree.InvocationExpression ie = that.getClassSpecifier().getInvocationExpression();
                        if (ie!=null) {
                            Tree.PositionalArgumentList pal = ie.getPositionalArgumentList();
                            if (pal!=null) {
                                List<PositionalArgument> pas = pal.getPositionalArguments();
                                if (cps!=pas.size()) {
                                    pal.addUnsupportedError("wrong number of arguments for aliased class: " + 
                                            alias.getName() + " has " + cps + " parameters");
                                }
                                for (int i=0; i<pas.size() && i<cps && i<aps; i++) {
                                    Tree.PositionalArgument pa = pas.get(i);
                                    Parameter aparam = apl.getParameters().get(i);
                                    Parameter cparam = cpl.getParameters().get(i);
                                    if (pa instanceof Tree.ListedArgument) {
                                        if (cparam.isSequenced()) {
                                            pa.addUnsupportedError("argument to variadic parameter of aliased class must be spread");
                                        }
                                        Tree.Expression e = ((Tree.ListedArgument) pa).getExpression();
                                        checkAliasArg(aparam, e);
                                    }
                                    else if (pa instanceof Tree.SpreadArgument) {
                                        if (!cparam.isSequenced()) {
                                            pa.addUnsupportedError("argument to non-variadic parameter of aliased class may not be spread");
                                        }
                                        Tree.Expression e = ((Tree.SpreadArgument) pa).getExpression();
                                        checkAliasArg(aparam, e);
                                    }
                                    else if (pa!=null) {
                                        pa.addUnsupportedError("argument to parameter or aliased class must be listed or spread");
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void checkAliasArg(Parameter param, Tree.Expression e) {
        if (e!=null && param!=null) {
            MethodOrValue p = param.getModel();
            if (p!=null) {
                Tree.Term term = e.getTerm();
                if (term instanceof Tree.BaseMemberExpression) {
                    Declaration d = ((Tree.BaseMemberExpression) term).getDeclaration();
                    if (d!=null && !d.equals(p)) {
                        e.addUnsupportedError("argument must be a parameter reference to " +
                                p.getName());
                    }
                }
                else {
                    e.addUnsupportedError("argument must be a parameter reference to " +
                            p.getName());
                }
            }
        }
    }
    
    private void inferType(Tree.TypedDeclaration that, 
            Tree.SpecifierOrInitializerExpression spec) {
        if (that.getType() instanceof Tree.LocalModifier) {
            Tree.LocalModifier local = (Tree.LocalModifier) that.getType();
            if (spec!=null) {
                setType(local, spec, that);
            }
        }
    }

    private void inferType(Tree.AttributeArgument that, 
            Tree.SpecifierOrInitializerExpression spec) {
        if (that.getType() instanceof Tree.LocalModifier) {
            Tree.LocalModifier local = (Tree.LocalModifier) that.getType();
            if (spec!=null) {
                setType(local, spec, that);
            }
        }
    }

    private void inferFunctionType(Tree.TypedDeclaration that, ProducedType et) {
        if (that.getType() instanceof Tree.FunctionModifier) {
            Tree.FunctionModifier local = (Tree.FunctionModifier) that.getType();
            if (et!=null) {
                setFunctionType(local, et, that);
            }
        }
    }
    
    private void inferFunctionType(Tree.MethodArgument that, ProducedType et) {
        if (that.getType() instanceof Tree.FunctionModifier) {
            Tree.FunctionModifier local = (Tree.FunctionModifier) that.getType();
            if (et!=null) {
                setFunctionType(local, et, that);
            }
        }
    }
    
    private void inferDefiniteType(Tree.Variable that, 
            Tree.SpecifierExpression se) {
        if (that.getType() instanceof Tree.LocalModifier) {
            Tree.LocalModifier local = (Tree.LocalModifier) that.getType();
            if (se!=null) {
                setTypeFromOptionalType(local, se, that);
            }
        }
    }

    private void inferNonemptyType(Tree.Variable that, 
            Tree.SpecifierExpression se) {
        if (that.getType() instanceof Tree.LocalModifier) {
            Tree.LocalModifier local = (Tree.LocalModifier) that.getType();
            if (se!=null) {
                setTypeFromEmptyType(local, se, that);
            }
        }
    }

    private void inferContainedType(Tree.Variable that, 
            Tree.SpecifierExpression se) {
        if (that.getType() instanceof Tree.LocalModifier) {
            Tree.LocalModifier local = (Tree.LocalModifier) that.getType();
            if (se!=null) {
                setTypeFromIterableType(local, se, that);
            }
        }
    }

    private void inferKeyType(Tree.Variable key, 
            Tree.SpecifierExpression se) {
        if (key.getType() instanceof Tree.LocalModifier) {
            Tree.LocalModifier local = (Tree.LocalModifier) key.getType();
            if (se!=null) {
                setTypeFromKeyType(local, se, key);
            }
        }
    }

    private void inferValueType(Tree.Variable value, 
            Tree.SpecifierExpression se) {
        if (value.getType() instanceof Tree.LocalModifier) {
            Tree.LocalModifier local = (Tree.LocalModifier) value.getType();
            if (se!=null) {
                setTypeFromValueType(local, se, value);
            }
        }
    }
    
    private void setTypeFromOptionalType(Tree.LocalModifier local, 
            Tree.SpecifierExpression se, Tree.Variable that) {
        Tree.Expression e = se.getExpression();
        if (e!=null) {
            ProducedType expressionType = e.getTypeModel();
            if (!isTypeUnknown(expressionType)) {
                ProducedType t;
                if (unit.isOptionalType(expressionType)) {
                    t = unit.getDefiniteType(expressionType);
                }
                else {
                    t=expressionType;
                }
                local.setTypeModel(t);
                that.getDeclarationModel().setType(t);
            }
        }
    }
    
    private void setTypeFromEmptyType(Tree.LocalModifier local, 
            Tree.SpecifierExpression se, Tree.Variable that) {
        Tree.Expression e = se.getExpression();
        if (e!=null) {
            ProducedType expressionType = e.getTypeModel();
            if (!isTypeUnknown(expressionType)) {
//                if (expressionType.getDeclaration() instanceof Interface && 
//                        expressionType.getDeclaration().equals(unit.getSequentialDeclaration())) {
//                    expressionType = unit.getEmptyType(unit.getSequenceType(expressionType.getTypeArgumentList().get(0)));
//                }
                ProducedType t;
                if (unit.isPossiblyEmptyType(expressionType)) {
                    t = unit.getNonemptyDefiniteType(expressionType);
                }
                else {
                    t = expressionType;
                }
                local.setTypeModel(t);
                that.getDeclarationModel().setType(t);
            }
        }
    }
    
    private void setTypeFromIterableType(Tree.LocalModifier local, 
            Tree.SpecifierExpression se, Tree.Variable that) {
        if (se.getExpression()!=null) {
            ProducedType expressionType = se.getExpression().getTypeModel();
            if (expressionType!=null) {
                ProducedType t = unit.getIteratedType(expressionType);
                if (t!=null) {
                    local.setTypeModel(t);
                    that.getDeclarationModel().setType(t);
                }
            }
        }
    }
    
    private void setTypeFromKeyType(Tree.LocalModifier local,
            Tree.SpecifierExpression se, Tree.Variable that) {
        Tree.Expression e = se.getExpression();
        if (e!=null) {
            ProducedType expressionType = e.getTypeModel();
            if (expressionType!=null) {
                ProducedType entryType = unit.getIteratedType(expressionType);
                if (entryType!=null) {
                    ProducedType kt = unit.getKeyType(entryType);
                    if (kt!=null) {
                        local.setTypeModel(kt);
                        that.getDeclarationModel().setType(kt);
                    }
                }
            }
        }
    }
    
    private void setTypeFromValueType(Tree.LocalModifier local,
            Tree.SpecifierExpression se, Tree.Variable that) {
        Tree.Expression e = se.getExpression();
        if (e!=null) {
            ProducedType expressionType = e.getTypeModel();
            if (expressionType!=null) {
                ProducedType entryType = unit.getIteratedType(expressionType);
                if (entryType!=null) {
                    ProducedType vt = unit.getValueType(entryType);
                    if (vt!=null) {
                        local.setTypeModel(vt);
                        that.getDeclarationModel().setType(vt);
                    }
                }
            }
        }
    }
    
    private void setType(Tree.LocalModifier local, 
            Tree.SpecifierOrInitializerExpression s, 
            Tree.TypedDeclaration that) {
        Tree.Expression e = s.getExpression();
        if (e!=null) {
            ProducedType type = e.getTypeModel();
            if (type!=null) {
                ProducedType t = unit.denotableType(type).withoutUnderlyingType();
                local.setTypeModel(t);
                that.getDeclarationModel().setType(t);
            }
        }
    }
        
    private void setType(Tree.LocalModifier local, 
            Tree.SpecifierOrInitializerExpression s, 
            Tree.AttributeArgument that) {
        Tree.Expression e = s.getExpression();
        if (e!=null) {
            ProducedType type = e.getTypeModel();
            if (type!=null) {
                ProducedType t = unit.denotableType(type).withoutUnderlyingType();
                local.setTypeModel(t);
                that.getDeclarationModel().setType(t);
            }
        }
    }
        
    private void setFunctionType(Tree.FunctionModifier local, 
            ProducedType et, Tree.TypedDeclaration that) {
        ProducedType t = unit.denotableType(et).withoutUnderlyingType();
        local.setTypeModel(t);
        that.getDeclarationModel().setType(t);
    }
        
    private void setFunctionType(Tree.FunctionModifier local, 
            ProducedType et, Tree.MethodArgument that) {
        ProducedType t = unit.denotableType(et).withoutUnderlyingType();
        local.setTypeModel(t);
        that.getDeclarationModel().setType(t);
    }
        
    @Override public void visit(Tree.Throw that) {
        super.visit(that);
        Tree.Expression e = that.getExpression();
        if (e!=null) {
            ProducedType et = e.getTypeModel();
            if (!isTypeUnknown(et)) {
                checkAssignable(et, unit.getType(unit.getThrowableDeclaration()), 
                        e, "thrown expression must be a throwable");
//                if (et.getDeclaration().isParameterized()) {
//                    e.addUnsupportedError("parameterized types in throw not yet supported");
//                }
            }
        }
    }
    
    @Override public void visit(Tree.Return that) {
        super.visit(that);
        if (returnType==null) {
            //misplaced return statements are already handled by ControlFlowVisitor
            //missing return types declarations already handled by TypeVisitor
            //that.addError("could not determine expected return type");
        } 
        else {
            that.setDeclaration(returnDeclaration);
            Tree.Expression e = that.getExpression();
            String name = returnDeclaration.getName();
            if (name==null) name = "anonymous function";
            if (e==null) {
                if (!(returnType instanceof Tree.VoidModifier)) {
                    that.addError("non-void function or getter must return a value: " +
                            name);
                }
            }
            else {
                ProducedType et = returnType.getTypeModel();
                ProducedType at = e.getTypeModel();
                if (returnType instanceof Tree.VoidModifier) {
                    that.addError("void function, setter, or class initializer may not return a value: " +
                            name);
                }
                else if (returnType instanceof Tree.LocalModifier) {
                    inferReturnType(et, at);
                }
                else {
                    if (!isTypeUnknown(et) && !isTypeUnknown(at)) {
                        checkAssignable(at, et, e, 
                                "returned expression must be assignable to return type of " +
                                        name, 2100);
                    }
                }
            }
        }
    }

    private void inferReturnType(ProducedType et, ProducedType at) {
        if (at!=null) {
            at = unit.denotableType(at);
            if (et==null || et.isSubtypeOf(at)) {
                returnType.setTypeModel(at);
            }
            else {
                if (!at.isSubtypeOf(et)) {
                    UnionType ut = new UnionType(unit);
                    List<ProducedType> list = new ArrayList<ProducedType>(2);
                    addToUnion(list, et);
                    addToUnion(list, at);
                    ut.setCaseTypes(list);
                    returnType.setTypeModel( ut.getType() );
                }
            }
        }
    }
    
    ProducedType unwrap(ProducedType pt, Tree.QualifiedMemberOrTypeExpression mte) {
        ProducedType result;
        Tree.MemberOperator op = mte.getMemberOperator();
        Tree.Primary p = mte.getPrimary();
        if (op instanceof Tree.SafeMemberOp)  {
            checkOptional(pt, p, p);
            result = unit.getDefiniteType(pt);
        }
        else if (op instanceof Tree.SpreadOp) {
            if (unit.isIterableType(pt)) {
                result = unit.getIteratedType(pt);
            }
            else {
                p.addError("expression must be of iterable type: " +
                        pt.getProducedTypeName(unit) + 
                        " is not a subtype of Iterable");
                result = pt;
            }
        }
        else {
            result = pt;
        }
        if (result==null) {
            result = new UnknownType(mte.getUnit()).getType();
        }
        return result;
    }
    
    ProducedType wrap(ProducedType pt, ProducedType receivingType, 
            Tree.QualifiedMemberOrTypeExpression mte) {
        Tree.MemberOperator op = mte.getMemberOperator();
        if (op instanceof Tree.SafeMemberOp)  {
            return unit.getOptionalType(pt);
        }
        else if (op instanceof Tree.SpreadOp) {
            //note: the following is nice, even though
            //      it is not actually blessed by the
            //      language spec!
            return unit.isSequenceType(receivingType) ?
                    unit.getSequenceType(pt) :
                    unit.getSequentialType(pt);
        }
        else {
            return pt;
        }
    }
    
    @Override public void visit(Tree.InvocationExpression that) {
        
        Tree.PositionalArgumentList pal = that.getPositionalArgumentList();
        if (pal!=null) {
            pal.visit(this);
            visitInvocationPositionalArgs(that);
        }
        
        Tree.NamedArgumentList nal = that.getNamedArgumentList();
        if (nal!=null) {
            nal.visit(this);
        }
        
        Tree.Primary p = that.getPrimary();
        if (p==null) {
            //TODO: can this actually occur??
            that.addError("malformed invocation expression");
        }
        else {
            p.visit(this);
            visitInvocationPrimary(that, p);
            if (isIndirectInvocation(that)) {
                visitIndirectInvocation(that);
            }
            else {
                visitDirectInvocation(that);
            }
        }
        
    }

    private void visitInvocationPositionalArgs(Tree.InvocationExpression that) {
        Tree.Primary p = that.getPrimary();
        Tree.PositionalArgumentList pal = that.getPositionalArgumentList();
        if (p instanceof Tree.MemberOrTypeExpression) {
            Tree.MemberOrTypeExpression mte = (Tree.MemberOrTypeExpression) p;
            //set up the "signature" on the primary
            //so that we can resolve the correct 
            //overloaded declaration
            List<Tree.PositionalArgument> args = pal.getPositionalArguments();
            List<ProducedType> sig = new ArrayList<ProducedType>(args.size());
            for (Tree.PositionalArgument pa: args) {
                sig.add(pa.getTypeModel());
            }
            mte.setSignature(sig);
            mte.setEllipsis(hasSpreadArgument(args));
        }
    }
    
    private void checkSuperInvocation(Tree.MemberOrTypeExpression qmte) {
        Declaration member = qmte.getDeclaration();
        if (member!=null) {
            if (member.isFormal() && !inExtendsClause) {
                qmte.addError("supertype member is declared formal: " + member.getName() + 
                        " of " + ((TypeDeclaration) member.getContainer()).getName());
            }
            else {
                ClassOrInterface ci = getContainingClassOrInterface(qmte.getScope());
                Declaration etm = ci.getExtendedTypeDeclaration()
                        .getMember(member.getName(), null, false);
                if (etm!=null && !etm.equals(member) && etm.refines(member)) {
                    qmte.addError("inherited member is refined by intervening superclass: " + 
                            ((TypeDeclaration) etm.getContainer()).getName() + 
                            " refines " + member.getName() + " declared by " + 
                            ((TypeDeclaration) member.getContainer()).getName());
                }
                for (TypeDeclaration td: ci.getSatisfiedTypeDeclarations()) {
                    Declaration stm = td.getMember(member.getName(), null, false);
                    if (stm!=null && !stm.equals(member) && stm.refines(member)) {
                        qmte.addError("inherited member is refined by intervening superinterface: " + 
                                ((TypeDeclaration) stm.getContainer()).getName() + 
                                " refines " + member.getName() + " declared by " + 
                                ((TypeDeclaration) member.getContainer()).getName());
                    }
                }
            }
        }
    }
    
    private void visitInvocationPrimary(Tree.InvocationExpression that,
            Tree.Primary pr) {
        Tree.Term term = Util.unwrapExpressionUntilTerm(pr);
        if (term instanceof Tree.StaticMemberOrTypeExpression) {
            Tree.StaticMemberOrTypeExpression mte = (Tree.StaticMemberOrTypeExpression) term;
            Declaration dec = mte.getDeclaration();
            if ( mte.getTarget()==null && dec instanceof Functional && 
                    mte.getTypeArguments() instanceof Tree.InferredTypeArguments ) {
                List<ProducedType> typeArgs = getInferedTypeArguments(that, (Functional) dec);
                mte.getTypeArguments().setTypeModels(typeArgs);
                if (term instanceof Tree.BaseTypeExpression) {
                    visitBaseTypeExpression((Tree.BaseTypeExpression) term,
                            (TypeDeclaration) dec, typeArgs, mte.getTypeArguments());
                }
                else if (term instanceof Tree.QualifiedTypeExpression) {
                    Tree.QualifiedTypeExpression qte = (Tree.QualifiedTypeExpression) term;
                    if (qte.getPrimary() instanceof Tree.Package) {
                        visitBaseTypeExpression(qte, (TypeDeclaration) dec, 
                                typeArgs, mte.getTypeArguments());
                    }
                    else {
                        visitQualifiedTypeExpression(qte, qte.getPrimary().getTypeModel(),
                                (TypeDeclaration) dec, typeArgs, mte.getTypeArguments());
                    }
                }
                else if (term instanceof Tree.BaseMemberExpression) {
                    visitBaseMemberExpression((Tree.BaseMemberExpression) term,
                            (TypedDeclaration) dec, typeArgs, mte.getTypeArguments());
                }
                else if (term instanceof Tree.QualifiedMemberExpression) {
                    Tree.QualifiedMemberExpression qme = (Tree.QualifiedMemberExpression) term;
                    if (qme.getPrimary() instanceof Tree.Package) {
                        visitBaseMemberExpression(qme, (TypedDeclaration) dec, 
                                typeArgs, mte.getTypeArguments());
                    }
                    else {
                        visitQualifiedMemberExpression(qme, qme.getPrimary().getTypeModel(),
                                (TypedDeclaration) dec, typeArgs, mte.getTypeArguments());
                    }
                }
            }
        }
    }
    
    private List<ProducedType> getInferedTypeArguments(Tree.InvocationExpression that, 
            Functional dec) {
        List<ProducedType> typeArgs = new ArrayList<ProducedType>();
        if (!dec.getParameterLists().isEmpty()) {
            ParameterList parameters = dec.getParameterLists().get(0);
            for (TypeParameter tp: dec.getTypeParameters()) {
                ProducedType it = inferTypeArgument(that, 
                        that.getPrimary().getTypeModel(),
                        tp, parameters);
                if (it.containsUnknowns()) {
                    that.addError("could not infer type argument from given arguments: " + 
                            tp.getName());
                }
                else {
                    it = constrainInferredType(dec, tp, it);
                }
                typeArgs.add(it);
            }
        }
        return typeArgs;
    }

    private ProducedType constrainInferredType(Functional dec,
            TypeParameter tp, ProducedType ta) {
        //Note: according to the language spec we should only 
        //      do this for contravariant  parameters, but in
        //      fact it also helps for special cases like
        //      emptyOrSingleton(null)
        List<ProducedType> list = new ArrayList<ProducedType>();
        addToIntersection(list, ta, unit);
        //Intersect the inferred type with any 
        //upper bound constraints on the type.
        for (ProducedType st: tp.getSatisfiedTypes()) {
            //TODO: substitute in the other inferred type args
            //      of the invocation
            //TODO: st.getProducedType(receiver, dec, typeArgs);
            if (!st.containsTypeParameters()) {
                addToIntersection(list, st, unit);
            }
        }
        IntersectionType it = new IntersectionType(unit);
        it.setSatisfiedTypes(list);
        ProducedType type = it.canonicalize().getType();
        return type;
    }

    private ProducedType inferTypeArgument(Tree.InvocationExpression that,
            ProducedReference pr, TypeParameter tp, ParameterList parameters) {
        List<ProducedType> inferredTypes = new ArrayList<ProducedType>();
        if (that.getPositionalArgumentList()!=null) {
            inferTypeArgumentFromPositionalArgs(tp, parameters, pr, 
                    that.getPositionalArgumentList(), inferredTypes);
        }
        else if (that.getNamedArgumentList()!=null) {
            inferTypeArgumentFromNamedArgs(tp, parameters, pr, 
                    that.getNamedArgumentList(), inferredTypes);
        }
        return formUnionOrIntersection(tp, inferredTypes);
    }

    private void inferTypeArgumentFromNamedArgs(TypeParameter tp, ParameterList parameters,
            ProducedReference pr, Tree.NamedArgumentList args, 
            List<ProducedType> inferredTypes) {
        Set<Parameter> foundParameters = new HashSet<Parameter>();
        for (Tree.NamedArgument arg: args.getNamedArguments()) {
            inferTypeArgFromNamedArg(arg, tp, pr, parameters, inferredTypes, 
                    foundParameters);
        }
        Parameter sp = getUnspecifiedParameter(null, parameters, 
                foundParameters);
        if (sp!=null) {
        	Tree.SequencedArgument sa = args.getSequencedArgument();
        	inferTypeArgFromSequencedArg(sa, tp, sp, inferredTypes);
        }    
    }

    private void inferTypeArgFromSequencedArg(Tree.SequencedArgument sa, TypeParameter tp,
            Parameter sp, List<ProducedType> inferredTypes) {
    	ProducedType att;
    	if (sa==null) {
    		att = unit.getEmptyDeclaration().getType();
    	}
    	else {
    		List<Tree.PositionalArgument> args = sa.getPositionalArguments();
    		att = getTupleType(args, false);
    	}
        ProducedType spt = sp.getType();
        addToUnionOrIntersection(tp, inferredTypes, inferTypeArg(tp, spt, att,
                new ArrayList<TypeParameter>()));
    }

    private void inferTypeArgFromNamedArg(Tree.NamedArgument arg, TypeParameter tp,
            ProducedReference pr, ParameterList parameters, 
            List<ProducedType> inferredTypes, Set<Parameter> foundParameters) {
        ProducedType type = null;
        if (arg instanceof Tree.SpecifiedArgument) {
            Tree.Expression e = ((Tree.SpecifiedArgument) arg).getSpecifierExpression()
                            .getExpression();
            if (e!=null) {
                type = e.getTypeModel();
            }
        }
        else if (arg instanceof Tree.TypedArgument) {
            //copy/pasted from checkNamedArgument()
            Tree.TypedArgument ta = (Tree.TypedArgument) arg;
            type = ta.getDeclarationModel()
            		.getTypedReference() //argument can't have type parameters
            		.getFullType();
        }
        if (type!=null) {
            Parameter parameter = getMatchingParameter(parameters, arg, foundParameters);
            if (parameter!=null) {
                foundParameters.add(parameter);
                ProducedType pt = pr.getTypedParameter(parameter)
                        .getFullType();
//              if (parameter.isSequenced()) pt = unit.getIteratedType(pt);
                addToUnionOrIntersection(tp,inferredTypes, inferTypeArg(tp, pt, type, 
                        new ArrayList<TypeParameter>()));
            }
        }
    }

    private void inferTypeArgumentFromPositionalArgs(TypeParameter tp, ParameterList parameters,
            ProducedReference pr, Tree.PositionalArgumentList pal, 
            List<ProducedType> inferredTypes) {
        List<Parameter> params = parameters.getParameters();
        for (int i=0; i<params.size(); i++) {
            Parameter parameter = params.get(i);
            List<Tree.PositionalArgument> args = pal.getPositionalArguments();
            if (args.size()>i) {
                Tree.PositionalArgument a = args.get(i);
                ProducedType at = a.getTypeModel();
                if (a instanceof Tree.SpreadArgument) {
                    at = spreadType(at, unit, true);
                    ProducedType ptt = unit.getParameterTypesAsTupleType(params.subList(i, params.size()), pr);
                    addToUnionOrIntersection(tp, inferredTypes, inferTypeArg(tp, ptt, at, 
                            new ArrayList<TypeParameter>()));
                }
                else if (a instanceof Tree.Comprehension) {
                    if (parameter.isSequenced()) {
                        inferTypeArgFromComprehension(tp, parameter, ((Tree.Comprehension) a), 
                                inferredTypes);
                    }
                }
                else {
                    if (parameter.isSequenced()) {
                        inferTypeArgFromPositionalArgs(tp, parameter, args.subList(i, args.size()), 
                                inferredTypes);
                        break;
                    }
                    else {
                        ProducedType pt = pr.getTypedParameter(parameter)
                                .getFullType();
                        addToUnionOrIntersection(tp, inferredTypes, inferTypeArg(tp, pt, at, 
                                new ArrayList<TypeParameter>()));
                    }
                }
            }
        }
    }

    private void inferTypeArgFromPositionalArgs(TypeParameter tp, Parameter parameter,
            List<Tree.PositionalArgument> args, List<ProducedType> inferredTypes) {
        for (int k=0; k<args.size(); k++) {
            Tree.PositionalArgument sa = args.get(k);
            ProducedType sat = sa.getTypeModel();
            if (sat!=null) {
                ProducedType pt = parameter.getType();
                if (sa instanceof Tree.SpreadArgument) {
                    sat = spreadType(sat, unit, true);
                    addToUnionOrIntersection(tp, inferredTypes, inferTypeArg(tp, pt, sat,
                            new ArrayList<TypeParameter>()));
                }
                else {
                    ProducedType spt = unit.getIteratedType(pt);
                    addToUnionOrIntersection(tp, inferredTypes, inferTypeArg(tp, spt, sat,
                            new ArrayList<TypeParameter>()));
                }
            }
        }
    }
    
    private void inferTypeArgFromComprehension(TypeParameter tp, Parameter parameter,
            Tree.Comprehension c, List<ProducedType> inferredTypes) {
            ProducedType sat = c.getTypeModel();
            if (sat!=null) {
                ProducedType pt = parameter.getType();
                ProducedType spt = unit.getIteratedType(pt);
                addToUnionOrIntersection(tp, inferredTypes, inferTypeArg(tp, spt, sat,
                        new ArrayList<TypeParameter>()));
            }
    }
    
    private ProducedType formUnionOrIntersection(TypeParameter tp,
            List<ProducedType> inferredTypes) {
        if (tp.isContravariant()) {
            return formIntersection(inferredTypes);
        }
        else {
            return formUnion(inferredTypes);
        }
    }
    
    private ProducedType unionOrIntersection(TypeParameter tp,
            List<ProducedType> inferredTypes) {
        if (inferredTypes.isEmpty()) {
            return null;
        }
        else {
            return formUnionOrIntersection(tp, inferredTypes);
        }
    }
    
    private void addToUnionOrIntersection(TypeParameter tp, List<ProducedType> list, 
            ProducedType pt) {
        if (tp.isContravariant()) {
            addToIntersection(list, pt, unit);
        }
        else {
            addToUnion(list, pt);
        }
    }

    private ProducedType union(List<ProducedType> types) {
        if (types.isEmpty()) {
            return null;
        }
        return formUnion(types);
    }

    private ProducedType intersection(List<ProducedType> types) {
        if (types.isEmpty()) {
            return null;
        }
        return formIntersection(types);
    }

    private ProducedType formUnion(List<ProducedType> types) {
        UnionType ut = new UnionType(unit);
        ut.setCaseTypes(types);
        return ut.getType();
    }
    
    private ProducedType formIntersection(List<ProducedType> types) {
        IntersectionType it = new IntersectionType(unit);
        it.setSatisfiedTypes(types);
        return it.canonicalize().getType();
    }
    
    private ProducedType inferTypeArg(TypeParameter tp, ProducedType paramType,
            ProducedType argType, List<TypeParameter> visited) {
        if (paramType!=null && argType!=null) {
            paramType = paramType.resolveAliases();
            argType = argType.resolveAliases();
            if (paramType.getDeclaration() instanceof TypeParameter &&
                    paramType.getDeclaration().equals(tp)) {
                return unit.denotableType(argType);
            }
            else if (paramType.getDeclaration() instanceof TypeParameter) {
                TypeParameter tp2 = (TypeParameter) paramType.getDeclaration();
                if (!visited.contains(tp2)) {
                    visited.add(tp2);
                    List<ProducedType> list = new ArrayList<ProducedType>();
                    for (ProducedType pt: tp2.getSatisfiedTypes()) {
                        addToUnionOrIntersection(tp, list, inferTypeArg(tp, pt, argType, visited));
                        ProducedType st = argType.getSupertype(pt.getDeclaration());
                        if (st!=null) {
                            for (int j=0; j<pt.getTypeArgumentList().size(); j++) {
                                if (st.getTypeArgumentList().size()>j) {
                                    addToUnionOrIntersection(tp, list, inferTypeArg(tp, 
                                            pt.getTypeArgumentList().get(j), 
                                            st.getTypeArgumentList().get(j), 
                                            visited));
                                }
                            }
                        }
                    }
                    return unionOrIntersection(tp, list);
                }
                else {
                    return null;
                }
            }
            else if (paramType.getDeclaration() instanceof UnionType) {
                List<ProducedType> list = new ArrayList<ProducedType>();
                //If there is more than one type parameter in
                //the union, ignore this union when inferring 
                //types. 
                //TODO: This is all a bit adhoc. The problem is that
                //      when a parameter type involves a union of type
                //      parameters, it in theory imposes a compound 
                //      constraint upon the type parameters, but our
                //      algorithm doesn't know how to deal with compound
                //      constraints
                /*ProducedType typeParamType = null;
                boolean found = false;
                for (ProducedType ct: paramType.getDeclaration().getCaseTypes()) {
                    TypeDeclaration ctd = ct.getDeclaration();
                    if (ctd instanceof TypeParameter) {
                        typeParamType = ct;
                    }
                    if (ct.containsTypeParameters()) { //TODO: check that they are "free" type params                        
                        if (found) {
                            //the parameter type involves two type
                            //parameters which are being inferred
                            return null;
                        }
                        else {
                            found = true;
                        }
                    }
                }*/
                ProducedType pt = paramType;
                ProducedType apt = argType;
                if (argType.getDeclaration() instanceof UnionType) {
                    for (ProducedType act: argType.getDeclaration().getCaseTypes()) {
                        //some element of the argument union is already a subtype
                        //of the parameter union, so throw it away from both unions
                        if (act.substitute(argType.getTypeArguments()).isSubtypeOf(paramType)) {
                            pt = pt.shallowMinus(act);
                            apt = apt.shallowMinus(act);
                        }
                    }
                }
                if (pt.getDeclaration() instanceof UnionType)  {
                    boolean found = false;
                	for (TypeDeclaration td: pt.getDeclaration().getCaseTypeDeclarations()) {
                		if (td instanceof TypeParameter) {
                			if (found) return null;
                			found = true;
                		}
                	}
                	//just one type parameter left in the union
                    for (ProducedType ct: pt.getDeclaration().getCaseTypes()) {
                    	addToUnionOrIntersection(tp, list, inferTypeArg(tp, 
                    			ct.substitute(pt.getTypeArguments()), 
                    			apt, visited));
                    }
                }
                else {
                	addToUnionOrIntersection(tp, list, inferTypeArg(tp, 
                			pt, apt, visited));
                }
                return unionOrIntersection(tp, list);
                /*else {
                    //if the param type is of form T|A1 and the arg type is
                    //of form A2|B then constrain T by B and A1 by A2
                    ProducedType pt = paramType.minus(typeParamType);
                    addToUnionOrIntersection(tp, list, inferTypeArg(tp, 
                            paramType.minus(pt), argType.minus(pt), visited));
                    addToUnionOrIntersection(tp, list, inferTypeArg(tp, 
                            paramType.minus(typeParamType), pt, visited));
                    //return null;
                }*/
            }
            else if (paramType.getDeclaration() instanceof IntersectionType) {
                List<ProducedType> list = new ArrayList<ProducedType>();
                for (ProducedType ct: paramType.getDeclaration().getSatisfiedTypes()) {
                    addToUnionOrIntersection(tp, list, inferTypeArg(tp, 
                            ct.substitute(paramType.getTypeArguments()), 
                            argType, visited));
                }
                return unionOrIntersection(tp,list);
            }
            else if (argType.getDeclaration() instanceof UnionType) {
                List<ProducedType> list = new ArrayList<ProducedType>();
                for (ProducedType ct: argType.getDeclaration().getCaseTypes()) {
                    addToUnion(list, inferTypeArg(tp, paramType, 
                            ct.substitute(paramType.getTypeArguments()), 
                            visited));
                }
                return union(list);
            }
            else if (argType.getDeclaration() instanceof IntersectionType) {
                List<ProducedType> list = new ArrayList<ProducedType>();
                for (ProducedType ct: argType.getDeclaration().getSatisfiedTypes()) {
                    addToIntersection(list, inferTypeArg(tp, paramType, 
                            ct.substitute(paramType.getTypeArguments()), 
                            visited), unit);
                }
                return intersection(list);
            }
            else {
                ProducedType st = argType.getSupertype(paramType.getDeclaration());
                if (st!=null) {
                    List<ProducedType> list = new ArrayList<ProducedType>();
                    if (paramType.getQualifyingType()!=null && 
                            st.getQualifyingType()!=null) {
                        addToUnionOrIntersection(tp, list, inferTypeArg(tp, 
                                    paramType.getQualifyingType(), 
                                    st.getQualifyingType(), 
                                    visited));
                    }
                    for (int j=0; j<paramType.getTypeArgumentList().size(); j++) {
                        if (st.getTypeArgumentList().size()>j) {
                            addToUnionOrIntersection(tp, list, inferTypeArg(tp, 
                                    paramType.getTypeArgumentList().get(j), 
                                    st.getTypeArgumentList().get(j), 
                                    visited));
                        }
                    }
                    return unionOrIntersection(tp, list);
                }
                else {
                    return null;
                }
            }
        }
        else {
            return null;
        }
    }
    
    private void visitDirectInvocation(Tree.InvocationExpression that) {
        Tree.Term p = Util.unwrapExpressionUntilTerm(that.getPrimary());
        Tree.MemberOrTypeExpression mte = (Tree.MemberOrTypeExpression) p;
        ProducedReference prf = mte.getTarget();
        Functional dec = (Functional) mte.getDeclaration();
        if (dec==null) return;
        if (!(p instanceof Tree.ExtendedTypeExpression)) {
            if (dec instanceof Class && ((Class) dec).isAbstract()) {
                that.addError("abstract class may not be instantiated: " + dec.getName(unit));
            }
        }
        if (that.getNamedArgumentList()!=null && 
                dec.isAbstraction()) {
            //TODO: this is not really right - it's the fact 
            //      that we're calling Java and don't have
            //      meaningful parameter names that is the
            //      real problem, not the overload
            that.addError("overloaded declarations may not be called using named arguments: " +
                    dec.getName(unit));
        }
        //that.setTypeModel(prf.getType());
        ProducedType ct = p.getTypeModel();
        if (ct!=null && !ct.getTypeArgumentList().isEmpty()) {
            //pull the return type out of the Callable
            that.setTypeModel(ct.getTypeArgumentList().get(0));
        }
        if (that.getNamedArgumentList() != null) {
            List<ParameterList> parameterLists = dec.getParameterLists();
            if(!parameterLists.isEmpty()
                    && !parameterLists.get(0).isNamedParametersSupported()) {
                that.addError("named invocations of Java methods not supported");
            }
        }
        if (dec.isAbstraction()) {
            //nothing to check the argument types against
            //that.addError("no matching overloaded declaration");
        }
        else {
            //typecheck arguments using the parameter list
            //of the target declaration
            checkInvocationArguments(that, prf, dec);
        }
    }

    private void visitIndirectInvocation(Tree.InvocationExpression that) {
        
        if (that.getNamedArgumentList()!=null) {
            that.addError("named arguments not supported for indirect invocations");
        }
        Tree.PositionalArgumentList pal = that.getPositionalArgumentList();
        if (pal==null) {
            return;
        }
        
        Tree.Primary p = that.getPrimary();
        ProducedType pt = p.getTypeModel();
        if (!isTypeUnknown(pt)) {
            if (checkCallable(pt, p, "invoked expression must be callable")) {
                List<ProducedType> typeArgs = pt.getSupertype(unit.getCallableDeclaration())
                        .getTypeArgumentList();
                if (!typeArgs.isEmpty()) {
                    that.setTypeModel(typeArgs.get(0));
                }
                //typecheck arguments using the type args of Callable
                if (typeArgs.size()>=2) {
                    ProducedType paramTypesAsTuple = typeArgs.get(1);
                    if (paramTypesAsTuple!=null) {
                        TypeDeclaration pttd = paramTypesAsTuple.getDeclaration();
                        if (pttd instanceof ClassOrInterface &&
                                (pttd.equals(unit.getTupleDeclaration()) ||
                                pttd.equals(unit.getSequenceDeclaration()) ||
                                pttd.equals(unit.getSequentialDeclaration()) ||
                                pttd.equals(unit.getEmptyDeclaration()))) {
                            //we have a plain tuple type so we can check the
                            //arguments individually
                            checkIndirectInvocationArguments(that, paramTypesAsTuple,
                                    unit.getTupleElementTypes(paramTypesAsTuple),
                                    unit.isTupleLengthUnbounded(paramTypesAsTuple),
                                    unit.isTupleVariantAtLeastOne(paramTypesAsTuple),
                                    unit.getTupleMinimumLength(paramTypesAsTuple));
                        }
                        else {
                            //we have something exotic, a union of tuple types
                            //or whatever, so just check the whole argument tuple
                            checkAssignable(getTupleType(pal.getPositionalArguments(), false), 
                                    paramTypesAsTuple, pal,
                                    "argument list type must be assignable to parameter list type");
                        }
                    }                
                }
            }
        }
    }
    
    private void checkInvocationArguments(Tree.InvocationExpression that,
            ProducedReference prf, Functional dec) {
        List<ParameterList> pls = dec.getParameterLists();
        if (pls.isEmpty()) {
            if (dec instanceof TypeDeclaration) {
                that.addError("type has no parameter list: " + 
                        dec.getName(unit));
            }
            else {
                that.addError("function has no parameter list: " +
                        dec.getName(unit));
            }
        }
        else /*if (!dec.isOverloaded())*/ {
            ParameterList pl = pls.get(0);            
            Tree.PositionalArgumentList args = that.getPositionalArgumentList();
            if (args!=null) {
                checkPositionalArguments(pl, prf, args, that);
            }
            Tree.NamedArgumentList namedArgs = that.getNamedArgumentList();
            if (namedArgs!=null) {
                if (pl.isNamedParametersSupported()) {
                    namedArgs.getNamedArgumentList().setParameterList(pl);
                    checkNamedArguments(pl, prf, namedArgs);
                }
            }
        }
    }
    
    /*private void checkSpreadArgumentSequential(Tree.SpreadArgument arg,
            ProducedType argTuple) {
        if (!unit.isSequentialType(argTuple)) {
            arg.addError("spread argument expression is not sequential: " +
                    argTuple.getProducedTypeName(unit) + " is not a sequence type");
        }
    }*/
    
    private void checkNamedArguments(ParameterList pl, ProducedReference pr, 
            Tree.NamedArgumentList nal) {
        List<Tree.NamedArgument> na = nal.getNamedArguments();        
        Set<Parameter> foundParameters = new HashSet<Parameter>();
        
        for (Tree.NamedArgument a: na) {
            checkNamedArg(a, pl, pr, foundParameters);
        }
        
        Tree.SequencedArgument sa = nal.getSequencedArgument();
        if (sa!=null) {
            checkSequencedArg(sa, pl, pr, foundParameters);        
        }
        else {
            Parameter sp = getUnspecifiedParameter(pr, pl, foundParameters);
            if (sp!=null && !unit.isNonemptyIterableType(sp.getType())) {
                foundParameters.add(sp);
            }
        }
            
        for (Parameter p: pl.getParameters()) {
            if (!foundParameters.contains(p) && 
                    !p.isDefaulted() && (!p.isSequenced() || p.isAtLeastOne())) {
                nal.addError("missing named argument to parameter " + 
                        p.getName() + " of " + pr.getDeclaration().getName(unit));
            }
        }
    }
    
    private void checkSequencedArg(Tree.SequencedArgument sa, ParameterList pl,
            ProducedReference pr, Set<Parameter> foundParameters) {
        Parameter sp = getUnspecifiedParameter(pr, pl, foundParameters);
        if (sp==null) {
            sa.addError("all iterable parameters specified by named argument list: " + 
                    pr.getDeclaration().getName(unit) +
                    " does not declare any additional parameters of type Iterable");
        }
        else {
            if (!foundParameters.add(sp)) {
                sa.addError("duplicate argument for parameter: " +
                        sp + " of " + pr.getDeclaration().getName(unit));
            }
            else if (!dynamic && isTypeUnknown(sp.getType())) {
                sa.addError("parameter type could not be determined: " + 
                        sp.getName() + " of " + sp.getDeclaration().getName(unit));
            }
            checkSequencedArgument(sa, pr, sp);
        }
    }

    private void checkNamedArg(Tree.NamedArgument a, ParameterList pl,
            ProducedReference pr, Set<Parameter> foundParameters) {
        Parameter p = getMatchingParameter(pl, a, foundParameters);
        if (p==null) {
            if (a.getIdentifier()==null) {
                a.addError("all parameters specified by named argument list: " + 
                        pr.getDeclaration().getName(unit) +
                        " does not declare any additional parameters");
            }
            else {
                a.addError("no matching parameter for named argument " + 
                        name(a.getIdentifier()) + " declared by " + 
                        pr.getDeclaration().getName(unit), 101);
            }
        }
        else {
            if (!foundParameters.add(p)) {
                a.addError("duplicate argument for parameter: " +
                        p + " of " + pr.getDeclaration().getName(unit));
            }
            else if (!dynamic && isTypeUnknown(p.getType())) {
                a.addError("parameter type could not be determined: " + 
                        p.getName() + " of " + p.getDeclaration().getName(unit));
            }
            checkNamedArgument(a, pr, p);
        }
    }

    private void checkNamedArgument(Tree.NamedArgument a, ProducedReference pr, 
            Parameter p) {
        a.setParameter(p);
        ProducedType argType = null;
        if (a instanceof Tree.SpecifiedArgument) {
            Tree.SpecifiedArgument sa = (Tree.SpecifiedArgument) a;
            Tree.Expression e = sa.getSpecifierExpression().getExpression();
            if (e!=null) {
                argType = e.getTypeModel();
            }
        }
        else if (a instanceof Tree.TypedArgument) {
            Tree.TypedArgument ta = (Tree.TypedArgument) a;
            argType = ta.getDeclarationModel()
            		.getTypedReference() //argument can't have type parameters
            		.getFullType();
            checkArgumentToVoidParameter(p, ta);
        }
        ProducedType pt = pr.getTypedParameter(p).getFullType();
//      if (p.isSequenced()) pt = unit.getIteratedType(pt);
        if (!isTypeUnknown(argType) && !isTypeUnknown(pt)) {
            Node node;
            if (a instanceof Tree.SpecifiedArgument) {
                node = ((Tree.SpecifiedArgument) a).getSpecifierExpression();
            }
            else {
                node = a;
            }
            checkAssignable(argType, pt, node,
                    "named argument must be assignable to parameter " + 
                            p.getName() + " of " + pr.getDeclaration().getName(unit) + 
                            (pr.getQualifyingType()==null ? "" : 
                                " in " + pr.getQualifyingType().getProducedTypeName(unit)), 
                            2100);
        }
    }

    private void checkArgumentToVoidParameter(Parameter p, Tree.TypedArgument ta) {
        if (ta instanceof Tree.MethodArgument) {
            Tree.MethodArgument ma = (Tree.MethodArgument) ta;
            Tree.SpecifierExpression se = ma.getSpecifierExpression();
            if (se!=null && se.getExpression()!=null) {
                Tree.Type t = ta.getType();
                // if the argument is explicitly declared 
                // using the function modifier, it should 
                // be allowed, even if the parameter is 
                // declared void
                //TODO: what is a better way to check that 
                //      this is really the "shortcut" form
                if (t instanceof Tree.FunctionModifier && 
                        t.getToken()==null &&
                    p.isDeclaredVoid() &&
                    !isSatementExpression(se.getExpression())) {
                    ta.addError("functional parameter is declared void so argument may not evaluate to a value: " +
                            p.getName());
                }
            }
        }
    }
    
    private void checkSequencedArgument(Tree.SequencedArgument sa, ProducedReference pr, 
            Parameter p) {
        sa.setParameter(p);
        List<Tree.PositionalArgument> args = sa.getPositionalArguments();
        ProducedType paramType = pr.getTypedParameter(p).getFullType();
        ProducedType att = getTupleType(args, false)
                .getSupertype(unit.getIterableDeclaration());
        if (!isTypeUnknown(att) && !isTypeUnknown(paramType)) {
            checkAssignable(att, paramType, sa, 
                    "iterable arguments must be assignable to iterable parameter " + 
                            p.getName() + " of " + pr.getDeclaration().getName(unit) + 
                            (pr.getQualifyingType()==null ? "" : 
                                " in " + pr.getQualifyingType().getProducedTypeName(unit)));
        }
    }
    
    private Parameter getMatchingParameter(ParameterList pl, Tree.NamedArgument na, 
            Set<Parameter> foundParameters) {
        String name = name(na.getIdentifier());
        if (MISSING_NAME.equals(name)) {
            for (Parameter p: pl.getParameters()) {
                if (!foundParameters.contains(p)) {
                    Tree.Identifier node = new Tree.Identifier(null);
                    node.setScope(na.getScope());
                    node.setUnit(na.getUnit());
                    node.setText(p.getName());
                    na.setIdentifier(node);
                    return p;
                }
            }
        }
        else {
            for (Parameter p: pl.getParameters()) {
                if (p.getName()!=null &&
                        p.getName().equals(name)) {
                    return p;
                }
            }
        }
        return null;
    }

    private Parameter getUnspecifiedParameter(ProducedReference pr,
            ParameterList pl, Set<Parameter> foundParameters) {
        for (Parameter p: pl.getParameters()) {
            ProducedType t = pr==null ? 
                    p.getType() : 
                    pr.getTypedParameter(p).getFullType();
            if (t!=null) {
                t = t.resolveAliases();
                if (!foundParameters.contains(p) &&
                		unit.isIterableParameterType(t)) {
                    return p;
                }
            }
        }
        return null;
    }
    
    private void checkPositionalArguments(ParameterList pl, ProducedReference pr, 
            Tree.PositionalArgumentList pal, Tree.InvocationExpression that) {
        List<Tree.PositionalArgument> args = pal.getPositionalArguments();
        List<Parameter> params = pl.getParameters();
        for (int i=0; i<params.size(); i++) {
            Parameter p = params.get(i);
            if (i>=args.size()) {
                if (!p.isDefaulted() && (!p.isSequenced() || p.isAtLeastOne())) {
                    Node n = that instanceof Tree.Annotation && args.isEmpty() ? that : pal;
                    n.addError("missing argument to required parameter " + 
                            p.getName() + " of " + pr.getDeclaration().getName(unit));
                }
            } 
            else {
                Tree.PositionalArgument a = args.get(i);
                if (!dynamic && isTypeUnknown(p.getType())) {
                    a.addError("parameter type could not be determined: " + 
                            p.getName() + " of " + p.getDeclaration().getName(unit));
                }
                if (a instanceof Tree.SpreadArgument) {
                    checkSpreadArgument(pr, p, a, 
                            (Tree.SpreadArgument) a, 
                            params.subList(i, params.size()));
                    break;
                }
                else if (a instanceof Tree.Comprehension) {
                    if (p.isSequenced()) {
                        checkComprehensionPositionalArgument(p, pr, 
                                (Tree.Comprehension) a, p.isAtLeastOne());
                    }
                    else {
                        a.addError("not a variadic parameter: parameter " + 
                                p.getName() + " of " + pr.getDeclaration().getName());
                    }
                    break;
                }
                else {
                    if (p.isSequenced()) {
                        checkSequencedPositionalArgument(p, pr, 
                                args.subList(i, args.size()));
                        return; //Note: early return!
                    }
                    else {
                        checkPositionalArgument(p, pr, (Tree.ListedArgument) a);
                    }
                }
            }
        }
        
        for (int i=params.size(); i<args.size(); i++) {
            Tree.PositionalArgument arg = args.get(i);
            if (arg instanceof Tree.SpreadArgument) {
                if (unit.isEmptyType(arg.getTypeModel())) {
                    continue;
                }
            }
            arg.addError("no matching parameter declared by " +
                    pr.getDeclaration().getName(unit) + ": " + 
                    pr.getDeclaration().getName(unit) + " has " + 
                    params.size() + " parameters", 2000);
        }
    
    }

    private void checkSpreadArgument(ProducedReference pr, Parameter p,
            Tree.PositionalArgument a, Tree.SpreadArgument arg,
            List<Parameter> psl) {
        a.setParameter(p);
        ProducedType rat = arg.getTypeModel();
        if (!isTypeUnknown(rat)) {
            if (!unit.isIterableType(rat)) {
                //note: check already done by visit(SpreadArgument)
                /*arg.addError("spread argument is not iterable: " + 
                        rat.getProducedTypeName(unit) + 
                        " is not a subtype of Iterable");*/
            }
            else {
                ProducedType at = spreadType(rat, unit, true);
                //checkSpreadArgumentSequential(arg, at);
                ProducedType ptt = unit.getParameterTypesAsTupleType(psl, pr);
                if (!isTypeUnknown(at) && !isTypeUnknown(ptt)) {
                    checkAssignable(at, ptt, arg, 
                            "spread argument not assignable to parameter types");
                }
            }
        }
    }
    
    private void checkIndirectInvocationArguments(Tree.InvocationExpression that, 
            ProducedType paramTypesAsTuple, List<ProducedType> paramTypes, 
            boolean sequenced, boolean atLeastOne, int firstDefaulted) {
        
        Tree.PositionalArgumentList pal = that.getPositionalArgumentList();
        List<Tree.PositionalArgument> args = pal.getPositionalArguments();
        
        for (int i=0; i<paramTypes.size(); i++) {
            if (isTypeUnknown(paramTypes.get(i))) {
                that.addError("parameter types cannot be determined from function reference");
                return;
            }
        }
        
        for (int i=0; i<paramTypes.size(); i++) {
            if (i>=args.size()) {
                if (i<firstDefaulted && (!sequenced || atLeastOne || i!=paramTypes.size()-1)) {
                    pal.addError("missing argument for required parameter " + i);
                }
            }
            else {
                Tree.PositionalArgument arg = args.get(i);
                ProducedType at = arg.getTypeModel();
                if (arg instanceof Tree.SpreadArgument) {
                    int fd = firstDefaulted<0?-1:firstDefaulted<i?0:firstDefaulted-i;
                    checkSpreadIndirectArgument((Tree.SpreadArgument) arg, 
                            paramTypes.subList(i, paramTypes.size()), 
                            sequenced, atLeastOne, fd, at);
                    break;
                }
                else if (arg instanceof Tree.Comprehension) {
                    ProducedType paramType = paramTypes.get(i);
                    if (sequenced && i==paramTypes.size()-1) {
                        checkComprehensionIndirectArgument((Tree.Comprehension) arg, 
                            paramType, atLeastOne);
                    }
                    else {
                        arg.addError("not a variadic parameter: parameter " + i);
                    }
                    break;
                }
                else {
                    ProducedType paramType = paramTypes.get(i);
                    if (sequenced && i==paramTypes.size()-1) {
                        checkSequencedIndirectArgument(args.subList(i, args.size()), 
                                paramType);
                        return; //Note: early return!
                    }
                    else if (at!=null && paramType!=null && 
                            !isTypeUnknown(at) && !isTypeUnknown(paramType)) {
                        checkAssignable(at, paramType, arg, 
                                "argument must be assignable to parameter type");
                    }
                }
            }
        }
        
        for (int i=paramTypes.size(); i<args.size(); i++) {
            Tree.PositionalArgument arg = args.get(i);
            if (arg instanceof Tree.SpreadArgument) {
                if (unit.isEmptyType(arg.getTypeModel())) {
                    continue;
                }
            }
            arg.addError("no matching parameter: function reference has " + 
                    paramTypes.size() + " parameters", 2000);
        }
        
    }

    private void checkSpreadIndirectArgument(Tree.SpreadArgument sa,
            List<ProducedType> psl, boolean sequenced, 
            boolean atLeastOne, int firstDefaulted, ProducedType at) {
        //checkSpreadArgumentSequential(sa, at);
        if (!isTypeUnknown(at)) {
            if (!unit.isIterableType(at)) {
              //note: check already done by visit(SpreadArgument)
                /*sa.addError("spread argument is not iterable: " + 
                        at.getProducedTypeName(unit) + 
                        " is not a subtype of Iterable");*/
            }
            else {
                ProducedType sat = spreadType(at, unit, true);
                //TODO: this ultimately repackages the parameter
                //      information as a tuple - it would be
                //      better to just truncate the original
                //      tuple type we started with
                List<ProducedType> pts = new ArrayList<ProducedType>(psl);
                if (sequenced) {
                    pts.set(pts.size()-1, 
                            unit.getIteratedType(pts.get(pts.size()-1)));
                }
                ProducedType ptt = unit.getTupleType(pts, sequenced, atLeastOne, 
                        firstDefaulted);
                if (!isTypeUnknown(sat) && !isTypeUnknown(ptt)) {
                    checkAssignable(sat, ptt, sa, 
                            "spread argument not assignable to parameter types");
                }
            }
        }
    }

    private void checkSequencedIndirectArgument(List<Tree.PositionalArgument> args,
            ProducedType paramType) {
        ProducedType set = paramType==null ? null : unit.getIteratedType(paramType);
        for (int j=0; j<args.size(); j++) {
            Tree.PositionalArgument a = args.get(j);
            ProducedType at = a.getTypeModel();
            if (!isTypeUnknown(at) && !isTypeUnknown(paramType)) {
                if (a instanceof Tree.SpreadArgument) {
                    at = spreadType(at, unit, true);
                    checkAssignable(at, paramType, a, 
                            "spread argument must be assignable to variadic parameter",
                                    2101);
                }
                else {
                    checkAssignable(at, set, a, 
                            "argument must be assignable to variadic parameter", 
                                    2101);
                    //if we already have an arg to a nonempty variadic parameter,
                    //we can treat it like a possibly-empty variadic now
                    paramType = paramType.getSupertype(unit.getSequentialDeclaration());
                }
            }
        }
    }
    
    private void checkComprehensionIndirectArgument(Tree.Comprehension c, 
            ProducedType paramType, boolean atLeastOne) {
        Tree.InitialComprehensionClause icc = ((Tree.Comprehension) c).getInitialComprehensionClause();
        if (icc.getPossiblyEmpty() && atLeastOne) {
            c.addError("variadic parameter is required but comprehension is possibly empty");
        }
        ProducedType at = c.getTypeModel();
        ProducedType set = paramType==null ? null : unit.getIteratedType(paramType);
        if (!isTypeUnknown(at) && !isTypeUnknown(set)) {
            checkAssignable(at, set, c, 
                    "argument must be assignable to variadic parameter");
        }
    }
    
    private void checkSequencedPositionalArgument(Parameter p, ProducedReference pr,
            List<Tree.PositionalArgument> args) {
        ProducedType paramType = pr.getTypedParameter(p).getFullType();
        ProducedType set = paramType==null ? null : unit.getIteratedType(paramType);
        for (int j=0; j<args.size(); j++) {
            Tree.PositionalArgument a = args.get(j);
            a.setParameter(p);
            ProducedType at = a.getTypeModel();
            if (!isTypeUnknown(at) && !isTypeUnknown(paramType)) {
                if (a instanceof Tree.SpreadArgument) {
                    at = spreadType(at, unit, true);
                    checkAssignable(at, paramType, a, 
                            "spread argument must be assignable to variadic parameter " + 
                                    p.getName()+ " of " + pr.getDeclaration().getName(unit) + 
                                    (pr.getQualifyingType()==null ? "" : 
                                        " in " + pr.getQualifyingType().getProducedTypeName(unit)), 
                                    2101);
                }
                else {
                    checkAssignable(at, set, a, 
                            "argument must be assignable to variadic parameter " + 
                                    p.getName()+ " of " + pr.getDeclaration().getName(unit) + 
                                    (pr.getQualifyingType()==null ? "" : 
                                        " in " + pr.getQualifyingType().getProducedTypeName(unit)), 
                                    2101);
                    //if we already have an arg to a nonempty variadic parameter,
                    //we can treat it like a possibly-empty variadic now
                    paramType = paramType.getSupertype(unit.getSequentialDeclaration());
                }
            }
        }
    }
    
    private void checkComprehensionPositionalArgument(Parameter p, ProducedReference pr,
            Tree.Comprehension c, boolean atLeastOne) {
        Tree.InitialComprehensionClause icc = ((Tree.Comprehension) c).getInitialComprehensionClause();
        if (icc.getPossiblyEmpty() && atLeastOne) {
            c.addError("variadic parameter is required but comprehension is possibly empty");
        }
        ProducedType paramType = pr.getTypedParameter(p).getFullType();
        c.setParameter(p);
        ProducedType at = c.getTypeModel();
        if (!isTypeUnknown(at) && !isTypeUnknown(paramType)) {
            ProducedType set = paramType==null ? null : unit.getIteratedType(paramType);
            checkAssignable(at, set, c, 
                    "argument must be assignable to variadic parameter " + 
                            p.getName() + " of " + pr.getDeclaration().getName(unit) + 
                            (pr.getQualifyingType()==null ? "" : 
                                " in " + pr.getQualifyingType().getProducedTypeName(unit)), 
                            2101);
        }
    }
    
    private boolean hasSpreadArgument(List<Tree.PositionalArgument> args) {
        int size = args.size();
        if (size>0) {
            return args.get(size-1) instanceof Tree.SpreadArgument;
        }
        else {
            return false;
        }
    }

    private void checkPositionalArgument(Parameter p, ProducedReference pr,
            Tree.ListedArgument a) {
        ProducedType paramType = pr.getTypedParameter(p).getFullType();
        a.setParameter(p);
        ProducedType at = a.getTypeModel();
        if (!isTypeUnknown(at) && !isTypeUnknown(paramType)) {
            checkAssignable(at, paramType, a, 
                    "argument must be assignable to parameter " + 
                            p.getName() + " of " + pr.getDeclaration().getName(unit) + 
                            (pr.getQualifyingType()==null ? "" : 
                                " in " + pr.getQualifyingType().getProducedTypeName(unit)), 
                            2100);
        }
    }
    
    @Override public void visit(Tree.Comprehension that) {
        super.visit(that);
        that.setTypeModel(that.getInitialComprehensionClause().getTypeModel());
    }

    @Override public void visit(Tree.SpreadArgument that) {
        super.visit(that);
        Tree.Expression e = that.getExpression();
        if (e!=null) {
            ProducedType t = e.getTypeModel();
            if (t!=null) {
                if (!isTypeUnknown(t)) {
                    if (!unit.isIterableType(t)) {
                        e.addError("spread argument is not iterable: " + 
                                t.getProducedTypeName(unit) + 
                                " is not a subtype of Iterable");
                    }
                }
                that.setTypeModel(t);
            }
        }
    }

    @Override public void visit(Tree.ListedArgument that) {
        super.visit(that);
        if (that.getExpression()!=null) {
            that.setTypeModel(that.getExpression().getTypeModel());
        }
    }

    private boolean involvesUnknownTypes(Tree.ElementOrRange eor) {
        if (eor instanceof Tree.Element) {
            return isTypeUnknown(((Tree.Element) eor).getExpression().getTypeModel());
        }
        else {
            Tree.ElementRange er = (Tree.ElementRange) eor;
            return er.getLowerBound()!=null && isTypeUnknown(er.getLowerBound().getTypeModel()) ||
                    er.getUpperBound()!=null && isTypeUnknown(er.getUpperBound().getTypeModel());
        }
    }
    
    @Override public void visit(Tree.IndexExpression that) {
        super.visit(that);
        ProducedType pt = type(that);
        if (pt==null) {
            that.addError("could not determine type of receiver");
        }
        else {
            /*if (that.getIndexOperator() instanceof Tree.SafeIndexOp) {
                if (unit.isOptionalType(pt)) {
                    pt = unit.getDefiniteType(pt);
                }
                else {
                    that.getPrimary().addError("receiving type not of optional type: " +
                            pt.getDeclaration().getName(unit) + " is not a subtype of Optional");
                }
            }*/
            if (that.getElementOrRange()==null) {
                that.addError("malformed index expression");
            }
            else if (!isTypeUnknown(pt) && 
                     !involvesUnknownTypes(that.getElementOrRange())) {
                if (that.getElementOrRange() instanceof Tree.Element) {
                    ProducedType cst = pt.getSupertype(unit.getCorrespondenceDeclaration());
                    if (cst==null) {
                        that.getPrimary().addError("illegal receiving type for index expression: " +
                                pt.getDeclaration().getName(unit) + " is not a subtype of Correspondence");
                    }
                    else {
                        List<ProducedType> args = cst.getTypeArgumentList();
                        ProducedType kt = args.get(0);
                        ProducedType vt = args.get(1);
                        Tree.Element e = (Tree.Element) that.getElementOrRange();
                        checkAssignable(e.getExpression().getTypeModel(), kt, 
                                e.getExpression(), 
                                "index must be assignable to key type");
                        ProducedType rt = unit.getOptionalType(vt);
                        that.setTypeModel(rt);
                        Tree.Term t = e.getExpression().getTerm();
                        //TODO: in theory we could do a whole lot
                        //      more static-execution of the 
                        //      expression, but this seems
                        //      perfectly sufficient
                        refineTypeForTupleElement(that, pt, t);
                    }
                }
                else {
                    ProducedType rst = pt.getSupertype(unit.getRangedDeclaration());
                    if (rst==null) {
                        that.getPrimary().addError("illegal receiving type for index range expression: " +
                                pt.getDeclaration().getName(unit) + " is not a subtype of Ranged");
                    }
                    else {
                        List<ProducedType> args = rst.getTypeArgumentList();
                        ProducedType kt = args.get(0);
                        ProducedType rt = args.get(2);
                        Tree.ElementRange er = (Tree.ElementRange) that.getElementOrRange();
                        if (er.getLowerBound()!=null) {
                            checkAssignable(er.getLowerBound().getTypeModel(), kt,
                                    er.getLowerBound(), 
                                    "lower bound must be assignable to index type");
                        }
                        if (er.getUpperBound()!=null) {
                            checkAssignable(er.getUpperBound().getTypeModel(), kt,
                                    er.getUpperBound(), 
                                    "upper bound must be assignable to index type");
                        }
                        if (er.getLength()!=null) {
                            checkAssignable(er.getLength().getTypeModel(), 
                                    unit.getType(unit.getIntegerDeclaration()),
                                    er.getLength(), 
                                    "length must be an integer");
                        }
                        that.setTypeModel(rt);
//                        if (er.getLowerBound()!=null && er.getUpperBound()!=null) {
//                            refineTypeForTupleRange(that, pt, 
//                                    er.getLowerBound().getTerm(), 
//                                    er.getUpperBound().getTerm());
//                        }
//                        else if (er.getLowerBound()!=null) {
                        if (er.getLowerBound()!=null && 
                                er.getUpperBound()==null &&
                                er.getLength()==null) {
                            refineTypeForTupleOpenRange(that, pt, 
                                    er.getLowerBound().getTerm());
                        }
                        /*if (that.getIndexOperator() instanceof Tree.SafeIndexOp) {
                            that.setTypeModel(unit.getOptionalType(that.getTypeModel()));
                        }*/
                    }
                }
            }
        }
    }

    private void refineTypeForTupleElement(Tree.IndexExpression that,
            ProducedType pt, Tree.Term t) {
        boolean negated = false;
        if (t instanceof Tree.NegativeOp) {
            t = ((Tree.NegativeOp) t).getTerm();
            negated = true;
        }
        else if (t instanceof Tree.PositiveOp) {
            t = ((Tree.PositiveOp) t).getTerm();
        }
        ProducedType tt = pt;
        if (unit.isSequentialType(tt)) {
            if (t instanceof Tree.NaturalLiteral) {
                int index = Integer.parseInt(t.getText());
                if (negated) index = -index;
                List<ProducedType> elementTypes = unit.getTupleElementTypes(tt);
                boolean variadic = unit.isTupleLengthUnbounded(tt);
                int minimumLength = unit.getTupleMinimumLength(tt);
                boolean atLeastOne = unit.isTupleVariantAtLeastOne(tt);
                if (elementTypes!=null) {
                    if (elementTypes.isEmpty()) {
                        that.setTypeModel(unit.getType(unit.getNullDeclaration()));
                    }
                    else if (index<0) {
                        that.setTypeModel(unit.getType(unit.getNullDeclaration()));
                    }
                    else if (index<elementTypes.size()-(variadic?1:0)) {
                        ProducedType iet = elementTypes.get(index);
                        if (iet==null) return;
                        if (index>=minimumLength) {
                            iet = unionType(iet, 
                                    unit.getType(unit.getNullDeclaration()), 
                                    unit);
                        }
                        that.setTypeModel(iet);
                    }
                    else if (variadic) {
                        ProducedType iet = elementTypes.get(elementTypes.size()-1);
                        if (iet==null) return;
                        ProducedType it = unit.getIteratedType(iet);
                        if (it==null) return;
                        if (!atLeastOne || index>=elementTypes.size()) {
                            it = unionType(it, 
                                    unit.getType(unit.getNullDeclaration()), 
                                    unit);
                        }
                        that.setTypeModel(it);
                    }
                    else {
                        that.setTypeModel(unit.getType(unit.getNullDeclaration()));
                    }
                }
            }
        }
    }
    
    private void refineTypeForTupleOpenRange(Tree.IndexExpression that,
            ProducedType pt, Tree.Term l) {
        boolean lnegated = false;
        if (l instanceof Tree.NegativeOp) {
            l = ((Tree.NegativeOp) l).getTerm();
            lnegated = true;
        }
        else if (l instanceof Tree.PositiveOp) {
            l = ((Tree.PositiveOp) l).getTerm();
        }
        ProducedType tt = pt;
        if (unit.isSequentialType(tt)) {
            if (l instanceof Tree.NaturalLiteral) {
                int lindex = Integer.parseInt(l.getText());
                if (lnegated) lindex = -lindex;
                List<ProducedType> elementTypes = unit.getTupleElementTypes(tt);
                boolean variadic = unit.isTupleLengthUnbounded(tt);
                boolean atLeastOne = unit.isTupleVariantAtLeastOne(tt);
                int minimumLength = unit.getTupleMinimumLength(tt);
                List<ProducedType> list = new ArrayList<ProducedType>();
                if (elementTypes!=null) {
                    if (lindex<0) {
                        lindex=0;
                    }
                    for (int index=lindex; 
                            index<elementTypes.size()-(variadic?1:0); 
                            index++) {
                        ProducedType et = elementTypes.get(index);
                        if (et==null) return;
                        list.add(et);
                    }
                    if (variadic) {
                        ProducedType it = elementTypes.get(elementTypes.size()-1);
                        if (it==null) return;
                        ProducedType rt = unit.getIteratedType(it);
                        if (rt==null) return;
                        list.add(rt);
                    }
                    ProducedType rt = unit.getTupleType(list, variadic, 
                            atLeastOne && lindex<elementTypes.size(), 
                            minimumLength-lindex);
                    //intersect with the type determined using
                    //Ranged, which may be narrower, for example,
                    //for String
                    that.setTypeModel(intersectionType(rt, 
                            that.getTypeModel(), unit));
                }
            }
        }
    }

    private ProducedType type(Tree.PostfixExpression that) {
        Tree.Primary p = that.getPrimary();
        return p==null ? null : p.getTypeModel();
    }
    
    private void assign(Tree.Term term) {
        if (term instanceof Tree.MemberOrTypeExpression) {
            Tree.MemberOrTypeExpression m = 
                    (Tree.MemberOrTypeExpression) term;
            m.setAssigned(true);
        }
    }
    
    @Override public void visit(Tree.PostfixOperatorExpression that) {
        assign(that.getTerm());
        super.visit(that);
        ProducedType type = type(that);
        visitIncrementDecrement(that, type, that.getTerm());
        checkAssignability(that.getTerm(), that);
    }

    @Override public void visit(Tree.PrefixOperatorExpression that) {
        assign(that.getTerm());
        super.visit(that);
        ProducedType type = type(that);
        if (that.getTerm()!=null) {
            visitIncrementDecrement(that, type, that.getTerm());
            checkAssignability(that.getTerm(), that);
        }
    }
    
    private void visitIncrementDecrement(Tree.Term that,
            ProducedType pt, Tree.Term term) {
        if (!isTypeUnknown(pt)) {
            ProducedType ot = checkSupertype(pt, unit.getOrdinalDeclaration(), 
                    term, "operand expression must be of enumerable type");
            if (ot!=null) {
                ProducedType ta = ot.getTypeArgumentList().get(0);
                checkAssignable(ta, pt, that, 
                        "result type must be assignable to declared type");
            }
            that.setTypeModel(pt);
        }
    }
    
    /*@Override public void visit(Tree.SumOp that) {
        super.visit( (Tree.BinaryOperatorExpression) that );
        ProducedType lhst = leftType(that);
        if (lhst!=null) {
            //take into account overloading of + operator
            if (lhst.isSubtypeOf(getStringDeclaration().getType())) {
                visitBinaryOperator(that, getStringDeclaration());
            }
            else {
                visitBinaryOperator(that, getNumericDeclaration());
            }
        }
    }*/

    private void visitScaleOperator(Tree.ScaleOp that) {
        ProducedType lhst = leftType(that);
        ProducedType rhst = rightType(that);
        if (!isTypeUnknown(rhst) && !isTypeUnknown(lhst)) {
            TypeDeclaration sd = unit.getScalableDeclaration();
            ProducedType st = checkSupertype(rhst, sd, that, 
                    "right operand must be of scalable type");
            if (st!=null) {
                ProducedType ta = st.getTypeArgumentList().get(0);
                ProducedType rt = st.getTypeArgumentList().get(1);
                //hardcoded implicit type conversion Integer->Float 
                TypeDeclaration fd = unit.getFloatDeclaration();
                TypeDeclaration id = unit.getIntegerDeclaration();
                if (lhst.getDeclaration().inherits(id) &&
                        ta.getDeclaration().inherits(fd)) {
                    lhst = fd.getType();
                }
                checkAssignable(lhst, ta, that, 
                        "scale factor must be assignable to scale type");
                that.setTypeModel(rt);
            }
        }
    }
    
    private void checkComparable(Tree.BinaryOperatorExpression that) {
        ProducedType lhst = leftType(that);
        ProducedType rhst = rightType(that);
        if (!isTypeUnknown(rhst) && !isTypeUnknown(lhst)) {
            checkOperandTypes(lhst, rhst, unit.getComparableDeclaration(), that, 
                    "operand expressions must be comparable");
        }
    }
    
    private void visitComparisonOperator(Tree.BinaryOperatorExpression that) {
        checkComparable(that);
        that.setTypeModel( unit.getType(unit.getBooleanDeclaration()) );            
    }

    private void visitCompareOperator(Tree.CompareOp that) {
        checkComparable(that);
        that.setTypeModel( unit.getType(unit.getComparisonDeclaration()) );            
    }
    
    private void visitWithinOperator(Tree.WithinOp that) {
        Tree.Term lbt = that.getLowerBound().getTerm();
        Tree.Term ubt = that.getUpperBound().getTerm();
        ProducedType lhst = lbt==null ? null : lbt.getTypeModel();
        ProducedType rhst = ubt==null ? null : ubt.getTypeModel();
        ProducedType t = that.getTerm().getTypeModel();
        if (!isTypeUnknown(t) &&
            !isTypeUnknown(lhst) && !isTypeUnknown(rhst)) {
            checkOperandTypes(t, lhst, rhst, unit.getComparableDeclaration(), 
                    that, "operand expressions must be comparable");
        }
        that.setTypeModel( unit.getType(unit.getBooleanDeclaration()) );            
    }

    private void visitSpanOperator(Tree.RangeOp that) {
        ProducedType lhst = leftType(that);
        ProducedType rhst = rightType(that);
        ProducedType ot = checkOperandTypes(lhst, rhst, 
                unit.getEnumerableDeclaration(), that,
                "operand expressions must be of compatible enumerable type");
        if (ot!=null) {
            that.setTypeModel(unit.getSpanType(ot));
        }
    }
    
    private void visitMeasureOperator(Tree.SegmentOp that) {
        ProducedType lhst = leftType(that);
        ProducedType rhst = rightType(that);
        ProducedType ot = checkSupertype(lhst, unit.getEnumerableDeclaration(), 
                that.getLeftTerm(), "left operand must be of enumerable type");
        if (!isTypeUnknown(rhst)) {
            checkAssignable(rhst, unit.getType(unit.getIntegerDeclaration()), 
                    that.getRightTerm(), "right operand must be an integer");
        }
        if (ot!=null) {
            ProducedType ta = ot.getTypeArgumentList().get(0);
            that.setTypeModel(unit.getMeasureType(ta));
        }
    }
    
    private void visitEntryOperator(Tree.EntryOp that) {
        ProducedType lhst = leftType(that);
        ProducedType rhst = rightType(that);
        ProducedType ot = unit.getType(unit.getObjectDeclaration());
        checkAssignable(lhst, ot, that.getLeftTerm(), 
                "operand expression must not be an optional type");
        checkAssignable(rhst, ot, that.getRightTerm(), 
                "operand expression must not be an optional type");
        that.setTypeModel( unit.getEntryType(unit.denotableType(lhst), 
                unit.denotableType(rhst)) );
    }
    
    private void visitIdentityOperator(Tree.BinaryOperatorExpression that) {
        ProducedType lhst = leftType(that);
        ProducedType rhst = rightType(that);
        if (!isTypeUnknown(rhst) && !isTypeUnknown(lhst)) {
            TypeDeclaration id = unit.getIdentifiableDeclaration();
            checkAssignable(lhst, id.getType(), that.getLeftTerm(), 
                    "operand expression must be of type Identifiable");
            checkAssignable(rhst, id.getType(), that.getRightTerm(), 
                    "operand expression must be of type Identifiable");
            if (intersectionType(lhst, rhst, unit).isNothing()) {
                that.addError("values of disjoint types are never identical: " +
                        lhst.getProducedTypeName(unit) + 
                        " has empty intersection with " +
                        rhst.getProducedTypeName(unit));
            }
        }
        that.setTypeModel(unit.getType(unit.getBooleanDeclaration()));
    }
    
    private void visitEqualityOperator(Tree.BinaryOperatorExpression that) {
        ProducedType lhst = leftType(that);
        ProducedType rhst = rightType(that);
        if (!isTypeUnknown(rhst) && !isTypeUnknown(lhst)) {
            TypeDeclaration od = unit.getObjectDeclaration();
            checkAssignable(lhst, od.getType(), that.getLeftTerm(), 
                    "operand expression must be of type Object");
            checkAssignable(rhst, od.getType(), that.getRightTerm(), 
                    "operand expression must be of type Object");
        }
        that.setTypeModel(unit.getType(unit.getBooleanDeclaration()));
    }
    
    private void visitAssignOperator(Tree.AssignOp that) {
        ProducedType rhst = rightType(that);
        ProducedType lhst = leftType(that);
        if (!isTypeUnknown(rhst) && !isTypeUnknown(lhst)) {
            ProducedType leftHandType = lhst;
            // allow assigning null to java properties that could after all be null
            if (hasUncheckedNulls(that.getLeftTerm()))
                leftHandType = unit.getOptionalType(leftHandType);
            checkAssignable(rhst, leftHandType, that.getRightTerm(), 
                    "assigned expression must be assignable to declared type", 
                    2100);
        }
        that.setTypeModel(rhst);
//      that.setTypeModel(lhst); //this version is easier on backend
    }

    private ProducedType checkOperandTypes(ProducedType lhst, ProducedType rhst, 
            TypeDeclaration td, Node node, String message) {
        ProducedType lhsst = checkSupertype(lhst, td, node, message);
        if (lhsst!=null) {
            ProducedType at = lhsst.getTypeArgumentList().get(0);
            checkAssignable(rhst, at, node, message);
            return at;
        }
        else {
            return null;
        }
    }
    
    private ProducedType checkOperandTypes(ProducedType t, 
            ProducedType lhst, ProducedType rhst, 
            TypeDeclaration td, Node node, String message) {
        ProducedType st = checkSupertype(t, td, node, message);
        if (st!=null) {
            ProducedType at = st.getTypeArgumentList().get(0);
            checkAssignable(lhst, at, node, message);
            checkAssignable(rhst, at, node, message);
            return at;
        }
        else {
            return null;
        }
    }
    
    private void visitArithmeticOperator(Tree.BinaryOperatorExpression that, 
            TypeDeclaration type) {
        ProducedType lhst = leftType(that);
        ProducedType rhst = rightType(that);
        if (!isTypeUnknown(rhst) && !isTypeUnknown(lhst)) {
            //hardcoded implicit type conversion Integer->Float
            TypeDeclaration fd = unit.getFloatDeclaration();
            TypeDeclaration id = unit.getIntegerDeclaration();
            if (rhst.getDeclaration().inherits(fd) &&
                lhst.getDeclaration().inherits(id)) {
                lhst = fd.getType();
            }
            else if (rhst.getDeclaration().inherits(id) &&
                     lhst.getDeclaration().inherits(fd)) {
                rhst = fd.getType();
            }
            ProducedType nt = checkSupertype(lhst, type, that.getLeftTerm(), 
                    that instanceof Tree.SumOp ?
                            "left operand must be of summable type" :
                            "left operand must be of numeric type");
            if (nt!=null) {
                List<ProducedType> tal = nt.getTypeArgumentList();
                if (tal.isEmpty()) return;
                ProducedType tt = tal.get(0);
                that.setTypeModel(tt);
                ProducedType ot;
                if (that instanceof Tree.PowerOp) {
                    if (tal.size()<2) return;
                    ot = tal.get(1);
                }
                else {
                    ot = tt;
                }
                checkAssignable(rhst, ot, that, 
                        that instanceof Tree.SumOp ?
                        "right operand must be of compatible summable type" :
                        "right operand must be of compatible numeric type");
            }
        }
    }
    
    private void visitArithmeticAssignOperator(Tree.BinaryOperatorExpression that, 
            TypeDeclaration type) {
        ProducedType lhst = leftType(that);
        ProducedType rhst = rightType(that);
        if (!isTypeUnknown(rhst) && !isTypeUnknown(lhst)) {
            //hardcoded implicit type conversion Integer->Float
            TypeDeclaration fd = unit.getFloatDeclaration();
            TypeDeclaration id = unit.getIntegerDeclaration();
            if (rhst.getDeclaration().inherits(id) &&
                lhst.getDeclaration().inherits(fd)) {
                rhst = fd.getType();
            }
            ProducedType nt = checkSupertype(lhst, type, that.getLeftTerm(),
                    that instanceof Tree.AddAssignOp ?
                            "operand expression must be of summable type" :
                            "operand expression must be of numeric type");
            that.setTypeModel(lhst);
            if (nt!=null) {
                ProducedType t = nt.getTypeArgumentList().get(0);
                //that.setTypeModel(t); //stef requests lhst to make it easier on backend
                checkAssignable(rhst, t, that, 
                        that instanceof Tree.AddAssignOp ?
                                "right operand must be of compatible summable type" :
                                "right operand must be of compatible numeric type");
                checkAssignable(t, lhst, that, 
                        "result type must be assignable to declared type");
            }
        }
    }
    
    private void visitSetOperator(Tree.BitwiseOp that) {
        //TypeDeclaration sd = unit.getSetDeclaration();
        ProducedType lhst = leftType(that);
        ProducedType rhst = rightType(that);
        if (!isTypeUnknown(rhst) && !isTypeUnknown(lhst)) {
            checkAssignable(lhst, unit.getSetType(unit.getType(unit.getObjectDeclaration())), 
                    that.getLeftTerm(), "set operand expression must be a set");
            checkAssignable(lhst, unit.getSetType(unit.getType(unit.getObjectDeclaration())), 
                    that.getRightTerm(), "set operand expression must be a set");
            ProducedType lhset = unit.getSetElementType(lhst);
            ProducedType rhset = unit.getSetElementType(rhst);
            ProducedType et;
            if (that instanceof Tree.IntersectionOp) {
                et = intersectionType(rhset, lhset, unit);
            }
            else if (that instanceof Tree.ComplementOp) {
                et = lhset;
            }
            else {
                et = unionType(rhset, lhset, unit);
            }            
            that.setTypeModel(unit.getSetType(et));
        }
    }

    private void visitSetAssignmentOperator(Tree.BitwiseAssignmentOp that) {
        //TypeDeclaration sd = unit.getSetDeclaration();
        ProducedType lhst = leftType(that);
        ProducedType rhst = rightType(that);
        if (!isTypeUnknown(rhst) && !isTypeUnknown(lhst)) {
            checkAssignable(lhst, unit.getSetType(unit.getType(unit.getObjectDeclaration())), 
                    that.getLeftTerm(), "set operand expression must be a set");
            checkAssignable(lhst, unit.getSetType(unit.getType(unit.getObjectDeclaration())), 
                    that.getRightTerm(), "set operand expression must be a set");
            ProducedType lhset = unit.getSetElementType(lhst);
            ProducedType rhset = unit.getSetElementType(rhst);
            if (that instanceof Tree.UnionAssignOp) {
                checkAssignable(rhset, lhset, that.getRightTerm(), 
                        "resulting set element type must be assignable to to declared set element type");
            }            
            that.setTypeModel(unit.getSetType(lhset)); //in theory, we could make this narrower
        }
    }

    private void visitLogicalOperator(Tree.BinaryOperatorExpression that) {
        ProducedType bt = unit.getType(unit.getBooleanDeclaration());
        ProducedType lt = leftType(that);
        ProducedType rt = rightType(that);
        if (!isTypeUnknown(rt) && !isTypeUnknown(lt)) {
            checkAssignable(lt, bt, that, 
                    "logical operand expression must be a boolean value");
            checkAssignable(rt, bt, that, 
                    "logical operand expression must be a boolean value");
        }
        that.setTypeModel(bt);
    }

    private void visitDefaultOperator(Tree.DefaultOp that) {
        ProducedType lhst = leftType(that);
        ProducedType rhst = rightType(that);
        if (!isTypeUnknown(rhst) && !isTypeUnknown(lhst)) {
            checkOptional(lhst, that.getLeftTerm(), that.getLeftTerm());
            List<ProducedType> list = new ArrayList<ProducedType>(2);
            addToUnion(list, unit.denotableType(rhst));
            addToUnion(list, unit.getDefiniteType(unit.denotableType(lhst)));
            UnionType ut = new UnionType(unit);
            ut.setCaseTypes(list);
            ProducedType rt = ut.getType();
            that.setTypeModel(rt);
            /*that.setTypeModel(rhst);
            ProducedType ot;
            if (isOptionalType(rhst)) {
                ot = rhst;
            }
            else {
                ot = getOptionalType(rhst);
            }
            if (!lhst.isSubtypeOf(ot)) {
                that.getLeftTerm().addError("must be of type: " + 
                        ot.getProducedTypeName(unit));
            }*/
        }
    }

    private void visitThenOperator(Tree.ThenOp that) {
        ProducedType lhst = leftType(that);
        ProducedType rhst = rightType(that);
        if (!isTypeUnknown(lhst)) {
            checkAssignable(lhst, unit.getType(unit.getBooleanDeclaration()), that.getLeftTerm(), 
                    "operand expression must be a boolean value");
        }
        if ( rhst!=null && !isTypeUnknown(rhst)) {
            checkAssignable(rhst, unit.getType(unit.getObjectDeclaration()), that.getRightTerm(),
                    "operand expression may not be an optional type");
            that.setTypeModel(unit.getOptionalType(unit.denotableType(rhst)));
        }
    }

    private void visitInOperator(Tree.InOp that) {
        ProducedType lhst = leftType(that);
        ProducedType rhst = rightType(that);
        if (!isTypeUnknown(rhst) && !isTypeUnknown(lhst)) {
            ProducedType ct = checkSupertype(rhst,unit.getCategoryDeclaration(),
            		that.getRightTerm(), "operand expression must be a category");
            if (ct!=null) {
                ProducedType at = ct.getTypeArguments().isEmpty() ? 
                        null : ct.getTypeArgumentList().get(0);
            	checkAssignable(lhst, at, that.getLeftTerm(), 
            			"operand expression must be assignable to category type");
            }
        }
        that.setTypeModel( unit.getType(unit.getBooleanDeclaration()) );
    }
    
    private void visitUnaryOperator(Tree.UnaryOperatorExpression that, 
            TypeDeclaration type) {
        ProducedType t = type(that);
        if (!isTypeUnknown(t)) {
            ProducedType nt = checkSupertype(t, type, that.getTerm(), 
                    "operand expression must be of correct type");
            if (nt!=null) {
                ProducedType at = nt.getTypeArguments().isEmpty() ? 
                        nt : nt.getTypeArgumentList().get(0);
                that.setTypeModel(at);
            }
        }
    }

    private void visitExistsOperator(Tree.Exists that) {
        checkOptional(type(that), that.getTerm(), that);
        that.setTypeModel(unit.getType(unit.getBooleanDeclaration()));
    }
    
    private void visitNonemptyOperator(Tree.Nonempty that) {
        checkEmpty(type(that), that.getTerm(), that);
        that.setTypeModel(unit.getType(unit.getBooleanDeclaration()));
    }
    
    private void visitOfOperator(Tree.OfOp that) {
        Tree.Type rt = that.getType();
        if (rt!=null) {
            ProducedType t = rt.getTypeModel();
            if (!isTypeUnknown(t)) {
                that.setTypeModel(t);
                Tree.Term tt = that.getTerm();
                if (tt!=null) {
                    if (tt!=null) {
                        ProducedType pt = tt.getTypeModel();
                        if (!isTypeUnknown(pt)) {
                            if (!t.covers(pt)) {
                                that.addError("specified type does not cover the cases of the operand expression: " +
                                        t.getProducedTypeName(unit) + " does not cover " + pt.getProducedTypeName(unit));
                            }
                        }
                    }
                }
            }
            /*else if (dynamic) {
                that.addError("static type not known");
            }*/
        }
    }
    
    private void visitIsOperator(Tree.IsOp that) {
        Tree.Type rt = that.getType();
        if (rt!=null) {
            ProducedType t = rt.getTypeModel();
            if (t!=null) {
                if (that.getTerm()!=null) {
                    ProducedType pt = that.getTerm().getTypeModel();
                    if (pt!=null && pt.isSubtypeOf(t)) {
                        that.addError("expression type is a subtype of the type: " +
                                pt.getProducedTypeName(unit) + " is assignable to " +
                                t.getProducedTypeName(unit));
                    }
                    else {
                        if (intersectionType(t, pt, unit).isNothing()) {
                            that.addError("tests assignability to Nothing type: intersection of " +
                                    pt.getProducedTypeName(unit) + " and " + 
                                    t.getProducedTypeName(unit) +
                                    " is empty");
                        }
                    }
                }
            }
        }
        that.setTypeModel(unit.getType(unit.getBooleanDeclaration()));
    }
    
    private void checkAssignability(Tree.Term that, Node node) {
        if (that instanceof Tree.BaseMemberExpression ||
                that instanceof Tree.QualifiedMemberExpression) {
            ProducedReference pr = ((Tree.MemberOrTypeExpression) that).getTarget();
            if (pr!=null) {
                Declaration dec = pr.getDeclaration();
                if (!(dec instanceof Value)) {
                    that.addError("member cannot be assigned: " 
                            + dec.getName(unit));
                }
                else if (!((MethodOrValue) dec).isVariable() &&
                         !((MethodOrValue) dec).isLate()) {
                    that.addError("value is not variable: " 
                            + dec.getName(unit), 800);
                }
            }
            if (that instanceof Tree.QualifiedMemberOrTypeExpression) {
            	if (!(((Tree.QualifiedMemberOrTypeExpression) that).getMemberOperator() instanceof Tree.MemberOp)) {
            		that.addUnsupportedError("assignment to expression involving ?. and *. not supported");
            	}
            	
            }
        }
        else {
            that.addError("expression cannot be assigned");
        }
    }
    
    private ProducedType rightType(Tree.BinaryOperatorExpression that) {
        Tree.Term rt = that.getRightTerm();
        return rt==null? null : rt.getTypeModel();
    }

    private ProducedType leftType(Tree.BinaryOperatorExpression that) {
        Tree.Term lt = that.getLeftTerm();
        return lt==null ? null : lt.getTypeModel();
    }
    
    private ProducedType type(Tree.UnaryOperatorExpression that) {
        Tree.Term t = that.getTerm();
        return t==null ? null : t.getTypeModel();
    }
    
    private Interface getArithmeticDeclaration(Tree.ArithmeticOp that) {
        if (that instanceof Tree.PowerOp) {
            return unit.getExponentiableDeclaration();
        }
        else if (that instanceof Tree.SumOp) {
            return unit.getSummableDeclaration();
        }
        else if (that instanceof Tree.DifferenceOp) {
            return unit.getInvertableDeclaration();
        }
        else if (that instanceof Tree.RemainderOp) {
            return unit.getIntegralDeclaration();
        }
        else {
            return unit.getNumericDeclaration();
        }
    }

    private Interface getArithmeticDeclaration(Tree.ArithmeticAssignmentOp that) {
        if (that instanceof Tree.AddAssignOp) {
            return unit.getSummableDeclaration();
        }
        else if (that instanceof Tree.SubtractAssignOp) {
            return unit.getInvertableDeclaration();
        }
        else if (that instanceof Tree.RemainderAssignOp) {
            return unit.getIntegralDeclaration();
        }
        else {
            return unit.getNumericDeclaration();
        }
    }

    @Override public void visit(Tree.ArithmeticOp that) {
        super.visit(that);
        visitArithmeticOperator(that, getArithmeticDeclaration(that));
    }

    @Override public void visit(Tree.BitwiseOp that) {
        super.visit(that);
        visitSetOperator(that);
    }

    @Override public void visit(Tree.ScaleOp that) {
        super.visit(that);
        visitScaleOperator(that);
    }

    @Override public void visit(Tree.LogicalOp that) {
        super.visit(that);
        visitLogicalOperator(that);
    }

    @Override public void visit(Tree.EqualityOp that) {
        super.visit(that);
        visitEqualityOperator(that);
    }

    @Override public void visit(Tree.ComparisonOp that) {
        super.visit(that);
        visitComparisonOperator(that);
    }

    @Override public void visit(Tree.WithinOp that) {
        super.visit(that);
        visitWithinOperator(that);
    }

    @Override public void visit(Tree.IdenticalOp that) {
        super.visit(that);
        visitIdentityOperator(that);
    }

    @Override public void visit(Tree.CompareOp that) {
        super.visit(that);
        visitCompareOperator(that);
    }

    @Override public void visit(Tree.DefaultOp that) {
        super.visit(that);
        visitDefaultOperator(that);
    }
        
    @Override public void visit(Tree.ThenOp that) {
        super.visit(that);
        visitThenOperator(that);
    }
        
    @Override public void visit(Tree.NegativeOp that) {
        super.visit(that);
        visitUnaryOperator(that, unit.getInvertableDeclaration());
    }
        
    @Override public void visit(Tree.PositiveOp that) {
        super.visit(that);
        visitUnaryOperator(that, unit.getInvertableDeclaration());
    }
    
    @Override public void visit(Tree.NotOp that) {
        super.visit(that);
        visitUnaryOperator(that, unit.getBooleanDeclaration());
    }
    
    @Override public void visit(Tree.AssignOp that) {
        assign(that.getLeftTerm());
        super.visit(that);
        visitAssignOperator(that);
        checkAssignability(that.getLeftTerm(), that);
    }
    
    @Override public void visit(Tree.ArithmeticAssignmentOp that) {
        assign(that.getLeftTerm());
        super.visit(that);
        visitArithmeticAssignOperator(that, getArithmeticDeclaration(that));
        checkAssignability(that.getLeftTerm(), that);
    }
    
    @Override public void visit(Tree.LogicalAssignmentOp that) {
        assign(that.getLeftTerm());
        super.visit(that);
        visitLogicalOperator(that);
        checkAssignability(that.getLeftTerm(), that);
    }
    
    @Override public void visit(Tree.BitwiseAssignmentOp that) {
        assign(that.getLeftTerm());
        super.visit(that);
        visitSetAssignmentOperator(that);
        checkAssignability(that.getLeftTerm(), that);
    }
    
    @Override public void visit(Tree.RangeOp that) {
        super.visit(that);
        visitSpanOperator(that);
    }
    
    @Override public void visit(Tree.SegmentOp that) {
        super.visit(that);
        visitMeasureOperator(that);
    }
        
    @Override public void visit(Tree.EntryOp that) {
        super.visit(that);
        visitEntryOperator(that);
    }
    
    @Override public void visit(Tree.Exists that) {
        super.visit(that);
        visitExistsOperator(that);
    }
    
    @Override public void visit(Tree.Nonempty that) {
        super.visit(that);
        visitNonemptyOperator(that);
    }
    
    @Override public void visit(Tree.IsOp that) {
        super.visit(that);
        visitIsOperator(that);
    }
    
    @Override public void visit(Tree.OfOp that) {
        super.visit(that);
        visitOfOperator(that);
    }
    
    @Override public void visit(Tree.Extends that) {
        super.visit(that);
        that.addUnsupportedError("extends operator not yet supported");
    }
    
    @Override public void visit(Tree.Satisfies that) {
        super.visit(that);
        that.addUnsupportedError("satisfies operator not yet supported");
    }
    
    @Override public void visit(Tree.InOp that) {
        super.visit(that);
        visitInOperator(that);
    }
    
    @Override
    public void visit(Tree.BaseType that) {
        super.visit(that);
        TypeDeclaration type = that.getDeclarationModel();
        if (type!=null) {
            if (!type.isVisible(that.getScope())) {
                that.addError("type is not visible: " + 
                        baseDescription(that), 400);
            }
            else if (type.isPackageVisibility() && 
                    !declaredInPackage(type, unit)) {
                that.addError("package private type is not visible: " + 
                    baseDescription(that));
            }
            //don't need to consider "protected" because
            //toplevel types can't be declared protected
            //and inherited protected member types are
            //visible to subclasses
        }
    }

    @Override
    public void visit(Tree.QualifiedType that) {
        super.visit(that);
        TypeDeclaration type = that.getDeclarationModel();
        if (type!=null) {
            if (!type.isVisible(that.getScope())) {
                that.addError("member type is not visible: " + 
                        qualifiedDescription(that), 400);
            }
            else if (type.isPackageVisibility() && 
                    !declaredInPackage(type, unit)) {
                that.addError("package private member type is not visible: " + 
                        qualifiedDescription(that));
            }
            //this is actually slightly too restrictive
            //since a qualified type may in fact be an
            //inherited member type, but in that case
            //you can just get rid of the qualifier, so
            //in fact this restriction is OK
            else if (type.isProtectedVisibility() &&
                    !declaredInPackage(type, unit)) {
                that.addError("protected member type is not visible: " + 
                        qualifiedDescription(that));
            }
            //Note: we should remove this check if we ever 
            //      make qualified member types like T.Member
            //      into a sort of virtual type
            Tree.StaticType outerType = that.getOuterType();
			if (outerType instanceof Tree.SimpleType) {
				if (((Tree.SimpleType) outerType).getDeclarationModel() instanceof TypeParameter) {
					outerType.addError("type parameter should not occur as qualifying type: " +
							qualifiedDescription(that));
				}
			}
        }
    }

    private void checkBaseVisibility(Node that, TypedDeclaration member, 
            String name) {
        if (!member.isVisible(that.getScope())) {
            that.addError("function or value is not visible: " +
                    name, 400);
        }
        else if (member.isPackageVisibility() && 
                !declaredInPackage(member, unit)) {
            that.addError("package private function or value is not visible: " +
                    name);
        }
        //don't need to consider "protected" because
        //there are no toplevel members in Java and
        //inherited protected members are visible to 
        //subclasses
    }
    
    private void checkQualifiedVisibility(Node that, TypedDeclaration member, 
            String name, String container, boolean selfReference) {
        if (!member.isVisible(that.getScope())) {
            that.addError("method or attribute is not visible: " +
                    name + " of " + container, 400);
        }
        else if (member.isPackageVisibility() && 
                !declaredInPackage(member, unit)) {
            that.addError("package private method or attribute is not visible: " +
                    name + " of " + container);
        }
        //this is actually too restrictive since
        //it doesn't take into account "other 
        //instance" access (access from a different
        //instance of the same type)
        else if (member.isProtectedVisibility() && 
                !selfReference && 
                !declaredInPackage(member, unit)) {
            that.addError("protected method or attribute is not visible: " +
                    name + " of " + container);
        }
    }

    private void checkBaseTypeAndConstructorVisibility(
            Tree.BaseTypeExpression that, String name, TypeDeclaration type) {
        //Note: the handling of "protected" here looks
        //      wrong because Java has a crazy rule 
        //      that you can't instantiate protected
        //      member classes from a subclass
        if (isOverloadedVersion(type)) {  
            //it is a Java constructor
            //get the actual type that
            //owns the constructor
            //Declaration at = type.getContainer().getDirectMember(type.getName(), null, false);
            Declaration at = type.getExtendedTypeDeclaration();
            if (!at.isVisible(that.getScope())) {
                that.addError("type is not visible: " + name);
            }
            else if (at.isPackageVisibility() &&
                    !declaredInPackage(type, unit)) {
                that.addError("package private type is not visible: " + name);
            }
            else if (at.isProtectedVisibility() &&
                    !declaredInPackage(type, unit)) {
                that.addError("protected type is not visible: " + name);
            }
            else if (!type.isVisible(that.getScope())) {
                that.addError("type constructor is not visible: " + name);
            }
            else if (type.isPackageVisibility() && 
                    !declaredInPackage(type, unit)) {
                that.addError("package private constructor is not visible: " + name);
            }
            else if (type.isProtectedVisibility() &&
                    !declaredInPackage(type, unit)) {
                that.addError("protected constructor is not visible: " + name);
            }
        }
        else {
            if (!type.isVisible(that.getScope())) {
                that.addError("type is not visible: " + name, 400);
            }
            else if (type.isPackageVisibility() && 
                    !declaredInPackage(type, unit)) {
                that.addError("package private type is not visible: " + name);
            }
            else if (type.isProtectedVisibility() && 
                    !declaredInPackage(type, unit)) {
                that.addError("protected type is not visible: " + name);
            }
        }
    }
    
    private void checkQualifiedTypeAndConstructorVisibility(
            Tree.QualifiedTypeExpression that, TypeDeclaration type,
            String name, String container) {
        //Note: the handling of "protected" here looks
        //      wrong because Java has a crazy rule 
        //      that you can't instantiate protected
        //      member classes from a subclass
        if (isOverloadedVersion(type)) {
            //it is a Java constructor
            //get the actual type that
            //owns the constructor
            //Declaration at = type.getContainer().getDirectMember(type.getName(), null, false);
            Declaration at = type.getExtendedTypeDeclaration();
            if (!at.isVisible(that.getScope())) {
                that.addError("member type is not visible: " +
                        name + " of " + container);
            }
            else if (at.isPackageVisibility() && 
                    !declaredInPackage(type, unit)) {
                that.addError("package private member type is not visible: " +
                        name + " of type " + container);
            }
            else if (at.isProtectedVisibility() &&
                    !declaredInPackage(type, unit)) {
                that.addError("protected member type is not visible: " +
                        name + " of type " + container);
            }
            else if (!type.isVisible(that.getScope())) {
                that.addError("member type constructor is not visible: " +
                        name + " of " + container);
            }
            else if (type.isPackageVisibility() && 
                    !declaredInPackage(type, unit)) {
                that.addError("package private member type constructor is not visible: " +
                        name + " of " + container);
            }
            else if (type.isProtectedVisibility() && 
                    !declaredInPackage(type, unit)) {
                that.addError("protected member type constructor is not visible: " +
                        name + " of " + container);
            }
        }
        else {
            if (!type.isVisible(that.getScope())) {
                that.addError("member type is not visible: " +
                        name + " of " + container, 400);
            }
            else if (type.isPackageVisibility() && 
                    !declaredInPackage(type, unit)) {
                that.addError("package private member type is not visible: " +
                        name + " of " + container);
            }
            else if (type.isProtectedVisibility() && 
                    !declaredInPackage(type, unit)) {
                that.addError("protected member type is not visible: " +
                        name + " of " + container);
            }
        }
    }

    private static String baseDescription(Tree.BaseType that) {
        return name(that.getIdentifier());
    }
    
    private static String qualifiedDescription(Tree.QualifiedType that) {
        String name = name(that.getIdentifier());
        Declaration d = that.getOuterType().getTypeModel().getDeclaration();
        return name + " of type " + d.getName();
    }
    
    @Override public void visit(Tree.BaseMemberExpression that) {
        /*if (that.getTypeArgumentList()!=null)
            that.getTypeArgumentList().visit(this);*/
        super.visit(that);
        String name = name(that.getIdentifier());
        TypedDeclaration member = getTypedDeclaration(that.getScope(), 
                name, that.getSignature(), that.getEllipsis(),
                that.getUnit());
        if (member==null) {
            if (!dynamic) {
                that.addError("function or value does not exist: " +
                        name, 100);
                unit.getUnresolvedReferences().add(that.getIdentifier());
            }
        }
        else {
            member = (TypedDeclaration) handleAbstraction(member, that);
            that.setDeclaration(member);
            checkBaseVisibility(that, member, name);
            Tree.TypeArguments tal = that.getTypeArguments();
            if (explicitTypeArguments(member, tal, that)) {
                List<ProducedType> ta = getTypeArguments(tal, 
                        getTypeParameters(member), null);
                tal.setTypeModels(ta);
                visitBaseMemberExpression(that, member, ta, tal);
                //otherwise infer type arguments later
            }
            else  {
                typeArgumentsImplicit(that);
            }
            /*if (defaultArgument) {
                if (member.isClassOrInterfaceMember()) {
                    that.addWarning("references to this from default argument expressions not yet supported");
                }
            }*/
//            checkOverloadedReference(that);
        }
    }

    private List<TypeParameter> getTypeParameters(Declaration member) {
        return member instanceof Generic ? 
                ((Generic) member).getTypeParameters() : 
                Collections.<TypeParameter>emptyList();
    }
    
    @Override public void visit(Tree.QualifiedMemberExpression that) {
        /*that.getPrimary().visit(this);
        if (that.getTypeArgumentList()!=null)
            that.getTypeArgumentList().visit(this);*/
        super.visit(that);
        Tree.Primary p = that.getPrimary();
        ProducedType pt = p.getTypeModel();
        boolean packageQualified = p instanceof Tree.Package;
        boolean check = packageQualified ||
                that.getStaticMethodReference() ||
                pt!=null &&
                //account for dynamic blocks
                (!pt.getType().isUnknown() || 
                        that.getMemberOperator() instanceof Tree.SpreadOp);
        boolean nameNonempty = that.getIdentifier()!=null && 
                        !that.getIdentifier().getText().equals("");
        if (nameNonempty && check) {
            TypedDeclaration member;
            String name = name(that.getIdentifier());
            String container;
            boolean ambiguous;
            List<ProducedType> signature = that.getSignature();
            boolean ellipsis = that.getEllipsis();
            if (packageQualified) {
                container = "package " + unit.getPackage().getNameAsString();
                Declaration pm = unit.getPackage()
                        .getMember(name, signature, ellipsis);
                if (pm instanceof TypedDeclaration) {
                    member = (TypedDeclaration) pm;
                }
                else {
                    member = null;
                }
                ambiguous = false;
            }
            else {
                pt = pt.resolveAliases(); //needed for aliases like "alias Id<T> => T"
                TypeDeclaration d = getDeclaration(that, pt);
                container = "type " + d.getName(unit);
                ClassOrInterface ci = getContainingClassOrInterface(that.getScope());
                if (ci!=null && d.inherits(ci)) {
                    Declaration direct = ci.getDirectMember(name, signature, ellipsis);
                    if (direct instanceof TypedDeclaration) {
                        member = (TypedDeclaration) direct;
                    }
                    else {
                        member = getTypedMember(d, name, signature, ellipsis, unit);
                    }
                }
                else {
                    member = getTypedMember(d, name, signature, ellipsis, unit);
                }
                ambiguous = member==null && d.isMemberAmbiguous(name, unit, 
                        signature, ellipsis);
            }
            if (member==null) {
                if (ambiguous) {
                    that.addError("method or attribute is ambiguous: " +
                            name + " for " + container);
                }
                else {
                    that.addError("method or attribute does not exist: " +
                            name + " in " + container, 100);
                    unit.getUnresolvedReferences().add(that.getIdentifier());
                }
            }
            else {
                member = (TypedDeclaration) handleAbstraction(member, that);
                that.setDeclaration(member);
                boolean selfReference = isSelfReference(p);
                checkQualifiedVisibility(that, member, name, container,
                        selfReference);
                if (!selfReference && !member.isShared()) {
                    member.setOtherInstanceAccess(true);
                }
                Tree.TypeArguments tal = that.getTypeArguments();
                if (explicitTypeArguments(member, tal, that)) {
                    List<ProducedType> ta = getTypeArguments(tal,
                            getTypeParameters(member), pt);
                    tal.setTypeModels(ta);
                    if (packageQualified) {
                        visitBaseMemberExpression(that, member, ta, tal);
                    }
                    else {
                        visitQualifiedMemberExpression(that, pt, member, ta, tal);
                    }
                    //otherwise infer type arguments later
                }
                else {
                    typeArgumentsImplicit(that);
                }
//                checkOverloadedReference(that);
            }
            checkSuperMember(that);
        }
    }

    private boolean isSelfReference(Tree.Primary p) {
        return 
                p instanceof Tree.This ||
                p instanceof Tree.Outer ||
                p instanceof Tree.Super;
    }

    private void checkSuperMember(Tree.QualifiedMemberOrTypeExpression that) {
        Tree.Term t = eliminateParensAndWidening(that.getPrimary());
        if (t instanceof Tree.Super) {
            checkSuperInvocation(that);
        }
    }

    private void typeArgumentsImplicit(Tree.MemberOrTypeExpression that) {
        if (!that.getDirectlyInvoked()) {
            that.addError("missing type arguments to generic declaration: " + 
                    that.getDeclaration().getName(unit) + " declares type parameters");
        }
    }
    
    private void visitQualifiedMemberExpression(Tree.QualifiedMemberExpression that,
            ProducedType receivingType, TypedDeclaration member, 
            List<ProducedType> typeArgs, Tree.TypeArguments tal) {
        ProducedType receiverType = accountForStaticReferenceReceiverType(that, 
                unwrap(receivingType, that));
        if (acceptsTypeArguments(receiverType, member, typeArgs, tal, that, false)) {
            ProducedTypedReference ptr = 
                    receiverType.getTypedMember(member, typeArgs, 
                            that.getAssigned());
            /*if (ptr==null) {
                that.addError("member method or attribute does not exist: " + 
                        member.getName(unit) + " of type " + 
                        receiverType.getDeclaration().getName(unit));
            }
            else {*/
                ProducedType t = 
                        ptr.getFullType(wrap(ptr.getType(), 
                                receivingType, that));
                that.setTarget(ptr); //TODO: how do we wrap ptr???
                if (!dynamic && isTypeUnknown(t)) {
                    //this occurs with an ambiguous reference
                    //to a member of an intersection type
                    that.addError("could not determine type of method or attribute reference: " +
                            member.getName(unit)  + " of " + 
                            receiverType.getDeclaration().getName(unit));
                }
                that.setTypeModel(accountForStaticReferenceType(that, member, t));
            //}
        }
    }

    private ProducedType accountForStaticReferenceReceiverType(Tree.QualifiedMemberOrTypeExpression that, 
            ProducedType receivingType) {
        if (that.getStaticMethodReference()) {
            ProducedReference target = ((Tree.MemberOrTypeExpression) that.getPrimary()).getTarget();
            return target==null ? new UnknownType(unit).getType() : target.getType();
        }
        else {
            return receivingType;
        }
    }
    
    private ProducedType accountForStaticReferenceType(Tree.QualifiedMemberOrTypeExpression that, 
            Declaration member, ProducedType type) {
        if (that.getStaticMethodReference()) {
            Tree.MemberOrTypeExpression qmte = (Tree.MemberOrTypeExpression) that.getPrimary();
            if (member.isStaticallyImportable()) {
                return type;
            }
            else {
                ProducedReference target = qmte.getTarget();
                if (target==null) {
                    return new UnknownType(unit).getType();
                }
                else {
                    return getStaticReferenceType(type, target.getType());
                }
            }
        }
        else {
            return type;
        }
    }
    
    private ProducedType getStaticReferenceType(ProducedType type, ProducedType rt) {
        return producedType(unit.getCallableDeclaration(), type,
                producedType(unit.getTupleDeclaration(), 
                        rt, rt, unit.getType(unit.getEmptyDeclaration())));
    }
    
    private void visitBaseMemberExpression(Tree.StaticMemberOrTypeExpression that, 
            TypedDeclaration member, List<ProducedType> typeArgs, Tree.TypeArguments tal) {
        if (acceptsTypeArguments(member, typeArgs, tal, that, false)) {
            ProducedType outerType = 
                    that.getScope().getDeclaringType(member);
            ProducedTypedReference pr = 
                    member.getProducedTypedReference(outerType, typeArgs, 
                            that.getAssigned());
            that.setTarget(pr);
            ProducedType t = pr.getFullType();
            if (isTypeUnknown(t)) {
                if (!dynamic) {
                    that.addError("could not determine type of function or value reference: " +
                            member.getName(unit));
                }
            }
            else {
                that.setTypeModel(t);
            }
        }
    }

    @Override public void visit(Tree.BaseTypeExpression that) {
        super.visit(that);
        /*if (that.getTypeArgumentList()!=null)
            that.getTypeArgumentList().visit(this);*/
        String name = name(that.getIdentifier());
        TypeDeclaration type = getTypeDeclaration(that.getScope(), 
                name, that.getSignature(), that.getEllipsis(), 
                that.getUnit());
        if (type==null) {
            if (!dynamic) {
                that.addError("type does not exist: " + name, 102);
                unit.getUnresolvedReferences().add(that.getIdentifier());
            }
        }
        else {
            type = (TypeDeclaration) handleAbstraction(type, that);
            that.setDeclaration(type);
            checkBaseTypeAndConstructorVisibility(that, name, type);
            checkConcreteClass(type, that);
            Tree.TypeArguments tal = that.getTypeArguments();
            if (explicitTypeArguments(type, tal, that)) {
                List<ProducedType> ta = getTypeArguments(tal, 
                        type.getTypeParameters(), null);
                tal.setTypeModels(ta);
                visitBaseTypeExpression(that, type, ta, tal);
                //otherwise infer type arguments later
            }
            else {
                typeArgumentsImplicit(that);
            }
//            checkOverloadedReference(that);
        	checkSealedReference(type, that);
        }
    }

    private void checkConcreteClass(TypeDeclaration type,
            Tree.MemberOrTypeExpression that) {
        if (that.getStaticMethodReferencePrimary()) {
            if (!(type instanceof ClassOrInterface)) {
                that.addError("type cannot be instantiated: " +
                        type.getName(unit) + " is not a class or interface");
            }
        }
        else {
            if (type instanceof Class) {
                if (((Class) type).isAbstract()) {
                    that.addError("class cannot be instantiated: " +
                            type.getName(unit) + " is abstract");
                }
            }
            else if (type instanceof TypeParameter) {
                if (((TypeParameter) type).getParameterList()==null) {
                    that.addError("type parameter cannot be instantiated: " +
                            type.getName(unit));
                }
            }
            else {
                that.addError("type cannot be instantiated: " +
                        type.getName(unit) + " is not a class");
            }
        }
    }

	private void checkSealedReference(TypeDeclaration type,
            Tree.MemberOrTypeExpression that) {
	    if (type.isSealed() && !inSameModule(type) &&
	    		(!that.getStaticMethodReferencePrimary())) {
	    	that.addError("invokes or references a sealed class in a different module: " +
	    			type.getName(unit) + " in " + 
	    			type.getUnit().getPackage().getModule().getNameAsString());
	    }
    }
    
    @Override public void visit(Tree.ExtendedTypeExpression that) {
        super.visit(that);
        Declaration dec = that.getDeclaration();
        if (dec instanceof Class) {
            Class c = (Class) dec;
            if (c.isAbstraction()) { 
                //if the constructor is overloaded
                //resolve the right overloaded version
                Declaration result = findMatchingOverloadedClass(c, that.getSignature(), 
                        that.getEllipsis());
                if (result!=null && result!=dec) {
                    //patch the reference, which was already
                    //initialized to the abstraction
                    that.setDeclaration((TypeDeclaration) result);
                    if (isOverloadedVersion(result)) {  
                        //it is a Java constructor
                        if (result.isPackageVisibility() && 
                                !declaredInPackage(result, unit)) {
                            that.addError("package private constructor is not visible: " + 
                                    result.getName());
                        }
                    }
                }
                //else report to user that we could not
                //find a matching overloaded constructor
            }
//            checkOverloadedReference(that);
        }
    }
    
    @Override public void visit(Tree.QualifiedMemberOrTypeExpression that) {
        super.visit(that);
        Tree.Primary p = that.getPrimary();
        if (p instanceof Tree.MemberOrTypeExpression) {
            Declaration pd = ((Tree.MemberOrTypeExpression) p).getDeclaration();
            if (!(pd instanceof TypeDeclaration) && 
                    pd instanceof Functional) {
                //this is a direct function ref
                //its not a type, it can't have members
                that.addError("direct function references do not have members");
            }
        }
    }
    
    @Override public void visit(Tree.QualifiedTypeExpression that) {
        super.visit(that);
        /*that.getPrimary().visit(this);
        if (that.getTypeArgumentList()!=null)
            that.getTypeArgumentList().visit(this);*/
        Tree.Primary p = that.getPrimary();
        ProducedType pt = p.getTypeModel();
        boolean packageQualified = p instanceof Tree.Package;
        boolean check = packageQualified || 
                that.getStaticMethodReference() || 
                pt!=null && 
                //account for dynamic blocks
                (!pt.isUnknown() || 
                        that.getMemberOperator() instanceof Tree.SpreadOp);
        if (check) {
            TypeDeclaration type;
            String name = name(that.getIdentifier());
            String container;
            boolean ambiguous;
            List<ProducedType> signature = that.getSignature();
            boolean ellipsis = that.getEllipsis();
            if (packageQualified) {
                container = "package " + unit.getPackage().getNameAsString();
                Declaration pm = unit.getPackage()
                        .getMember(name, signature, ellipsis);
                if (pm instanceof TypeDeclaration) {
                    type = (TypeDeclaration) pm;
                }
                else {
                    type = null;
                }
                ambiguous = false;
            }
            else {
                pt = pt.resolveAliases(); //needed for aliases like "alias Id<T> => T"
                TypeDeclaration d = getDeclaration(that, pt);
                container = "type " + d.getName(unit);
                ClassOrInterface ci = getContainingClassOrInterface(that.getScope());
                if (ci!=null && d.inherits(ci)) {
                    Declaration direct = ci.getDirectMember(name, signature, ellipsis);
                    if (direct instanceof TypeDeclaration) {
                        type = (TypeDeclaration) direct;
                    }
                    else {
                        type = getTypeMember(d, name, signature, ellipsis, unit);
                    }
                }
                else {
                    type = getTypeMember(d, name, signature, ellipsis, unit);
                }
                ambiguous = type==null && d.isMemberAmbiguous(name, unit, 
                        signature, ellipsis);
            }
            if (type==null) {
                if (ambiguous) {
                    that.addError("member type is ambiguous: " +
                            name + " for " + container);
                }
                else {
                    that.addError("member type does not exist: " +
                            name + " in " + container, 100);
                    unit.getUnresolvedReferences().add(that.getIdentifier());
                }
            }
            else {
                type = (TypeDeclaration) handleAbstraction(type, that);
                that.setDeclaration(type);
                checkQualifiedTypeAndConstructorVisibility(that, type, name, container);
                if (!isSelfReference(p) && !type.isShared()) {
                    type.setOtherInstanceAccess(true);
                }
                checkConcreteClass(type, that);
                Tree.TypeArguments tal = that.getTypeArguments();
                if (explicitTypeArguments(type, tal, that)) {
                    List<ProducedType> ta = getTypeArguments(tal,
                            type.getTypeParameters(), pt);
                    tal.setTypeModels(ta);
                    if (packageQualified) {
                        visitBaseTypeExpression(that, type, ta, tal);
                    }
                    else {
                        visitQualifiedTypeExpression(that, pt, type, ta, tal);
                    }
                    //otherwise infer type arguments later
                }
                else {
                    typeArgumentsImplicit(that);
                }
//                checkOverloadedReference(that);
            	checkSealedReference(type, that);
            }
            //TODO: this is temporary until we get metamodel reference expressions!
            /*if (p instanceof Tree.BaseTypeExpression ||
                    p instanceof Tree.QualifiedTypeExpression) {
                ProducedReference target = that.getTarget();
                if (target!=null) {
                    checkTypeBelongsToContainingScope(target.getType(), 
                            that.getScope(), that);
                }
            }*/
            if (!inExtendsClause) {
                checkSuperMember(that);
            }
        }
    }
    
    private TypeDeclaration getDeclaration(Tree.QualifiedMemberOrTypeExpression that,
            ProducedType pt) {
        if (that.getStaticMethodReference()) {
            TypeDeclaration td = (TypeDeclaration) ((Tree.MemberOrTypeExpression) that.getPrimary()).getDeclaration();
            return td==null ? new UnknownType(unit) : td;
        }
        else {
            return unwrap(pt, that).getDeclaration();
        }
    }

    private boolean explicitTypeArguments(Declaration dec, Tree.TypeArguments tal, 
            Tree.MemberOrTypeExpression that) {
        return !dec.isParameterized() || 
                tal instanceof Tree.TypeArgumentList;
                //TODO: enable this line to enable
                //      typechecking of references
                //      without type arguments
                //|| !that.getDirectlyInvoked();
    }
    
    @Override public void visit(Tree.SimpleType that) {
        //this one is a declaration, not an expression!
        //we are only validating type arguments here
        super.visit(that);
        ProducedType pt = that.getTypeModel();
        if (pt!=null) {
            TypeDeclaration type = that.getDeclarationModel();//pt.getDeclaration()
            Tree.TypeArgumentList tal = that.getTypeArgumentList();
            //No type inference for declarations
            if (type!=null) {
                List<TypeParameter> params = type.getTypeParameters();
                List<ProducedType> ta = getTypeArguments(tal, 
                        params, pt.getQualifyingType());
                acceptsTypeArguments(type, ta, tal, that, that.getMetamodel());
                //the type has already been set by TypeVisitor
                if (tal!=null) {
                    List<Tree.Type> args = tal.getTypes();
                    for (int i = 0; i<args.size(); i++) {
                        Tree.Type t = args.get(i);
                        if (t instanceof Tree.StaticType) {
                            TypeVariance variance = 
                                    ((Tree.StaticType) t).getTypeVariance();
                            if (variance!=null) {
                                TypeParameter p = params.get(i);
                                if (p.isInvariant()) {
                                    if (variance.getText().equals("out")) {
                                        pt.setVariance(p, OUT);
                                    }
                                    else if (variance.getText().equals("in")) {
                                        pt.setVariance(p, IN);
                                    }
                                }
                                else {
                                    variance.addError("type parameter is not declared invariant: " + 
                                            p.getName() + " of " + type.getName(unit));
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    @Override public void visit(Tree.EntryType that) {
        super.visit(that);
        checkAssignable(that.getKeyType().getTypeModel(), unit.getType(unit.getObjectDeclaration()), 
                that.getKeyType(), "entry key type must not be an optional type");
        checkAssignable(that.getValueType().getTypeModel(), unit.getType(unit.getObjectDeclaration()), 
                that.getValueType(), "entry item type must not be an optional type");
    }

    private void visitQualifiedTypeExpression(Tree.QualifiedTypeExpression that,
            ProducedType receivingType, TypeDeclaration type, 
            List<ProducedType> typeArgs, Tree.TypeArguments tal) {
        ProducedType receiverType =  accountForStaticReferenceReceiverType(that, 
                unwrap(receivingType, that));
        if (acceptsTypeArguments(receiverType, type, typeArgs, tal, that, false)) {
            ProducedType t = receiverType.getTypeMember(type, typeArgs);
            boolean abs = isAbstractType(t) || isAbstraction(type);
            ProducedType ft = abs ?
                    producedType(unit.getCallableDeclaration(), t, new UnknownType(unit).getType()) :
                    t.getFullType(wrap(t, receivingType, that));
            that.setTarget(t);
            if (!dynamic && !abs && !that.getStaticMethodReference() && isTypeUnknown(ft)) {
                //this occurs with an ambiguous reference
                //to a member of an intersection type
                that.addError("could not determine type of member class reference: " +
                        type.getName(unit)  + " of " + 
                        receiverType.getDeclaration().getName(unit));
            }
            that.setTypeModel(accountForStaticReferenceType(that, type, ft));
        }
    }

    private void visitBaseTypeExpression(Tree.StaticMemberOrTypeExpression that, 
            TypeDeclaration type, List<ProducedType> typeArgs, Tree.TypeArguments tal) {
        ProducedType outerType = that.getScope().getDeclaringType(type);
        ProducedType t = type.getProducedType(outerType, typeArgs);
//        if (!type.isAlias()) {
            //TODO: remove this awful hack which means
            //      we can't define aliases for types
            //      with sequenced type parameters
            type = t.getDeclaration();
//        }
        if (acceptsTypeArguments(type, typeArgs, tal, that, false)) {
            ProducedType ft = isAbstractType(t) || isAbstraction(type) ?
                    producedType(unit.getCallableDeclaration(), t, new UnknownType(unit).getType()) :
                    t.getFullType();
            that.setTypeModel(ft);
            that.setTarget(t);
        }
    }

    private boolean isAbstractType(ProducedType t) {
        if (t.getDeclaration() instanceof Class) {
            return ((Class) t.getDeclaration()).isAbstract();
        }
        else if (t.getDeclaration() instanceof TypeParameter) {
            return ((TypeParameter) t.getDeclaration()).getParameterList()==null;
        }
        else {
            return true;
        }
    }

    @Override public void visit(Tree.Expression that) {
        //i.e. this is a parenthesized expression
        super.visit(that);
        Tree.Term term = that.getTerm();
        if (term==null) {
            that.addError("expression not well formed");
        }
        else {
            ProducedType t = term.getTypeModel();
            if (t==null) {
                //that.addError("could not determine type of expression");
            }
            else {
                that.setTypeModel(t);
            }
        }
    }
    
    @Override public void visit(Tree.Outer that) {
        ProducedType ci = getOuterClassOrInterface(that.getScope());
        if (ci==null) {
            that.addError("outer appears outside a nested class or interface definition");
        }
        else {
            that.setTypeModel(ci);
        }
        /*if (defaultArgument) {
            that.addError("reference to outer from default argument expression");
        }*/
    }

    @Override public void visit(Tree.Super that) {
        ClassOrInterface ci = getContainingClassOrInterface(that.getScope());
        if (inExtendsClause) {
            if (ci!=null) {
                if (ci.isClassOrInterfaceMember()) {
                    ClassOrInterface oci = (ClassOrInterface) ci.getContainer();
                    that.setTypeModel(intersectionOfSupertypes(oci));
                }
            }
        }
        else {
            //TODO: for consistency, move these errors to SelfReferenceVisitor
            if (ci==null) {
                that.addError("super occurs outside any type definition");
            }
            else {
                that.setTypeModel(intersectionOfSupertypes(ci));
            }
        }
    }

    @Override public void visit(Tree.This that) {
        ClassOrInterface ci = getContainingClassOrInterface(that.getScope());
        if (inExtendsClause) {
            if (ci!=null) {
                if (ci.isClassOrInterfaceMember()) {
                    ClassOrInterface s = (ClassOrInterface) ci.getContainer();
                    that.setTypeModel(s.getType());
                }
            }
        }
        else {
            if (ci==null) {
                that.addError("this appears outside a class or interface definition");
            }
            else {
                that.setTypeModel(ci.getType());
            }
        }
    }
    
    @Override public void visit(Tree.Package that) {
        if (!that.getQualifier()) {
            that.addError("package must qualify a reference to a toplevel declaration");
        }
        super.visit(that);
    }
    
    private ProducedType getTupleType(List<Tree.PositionalArgument> es, 
            boolean requireSequential) {
        ProducedType result = unit.getType(unit.getEmptyDeclaration());
        ProducedType ut = unit.getNothingDeclaration().getType();
        for (int i=es.size()-1; i>=0; i--) {
            Tree.PositionalArgument a = es.get(i);
            ProducedType t = a.getTypeModel();
            if (t!=null) {
                ProducedType et = unit.denotableType(t);
                if (a instanceof Tree.SpreadArgument) {
                    /*if (requireSequential) { 
                        checkSpreadArgumentSequential((Tree.SpreadArgument) a, et);
                    }*/
                    ut = unit.getIteratedType(et);
                    result = spreadType(et, unit, requireSequential);
                }
                else if (a instanceof Tree.Comprehension) {
                    ut = et;
                    Tree.InitialComprehensionClause icc = ((Tree.Comprehension) a).getInitialComprehensionClause();
                    result = icc.getPossiblyEmpty() ? 
                            unit.getSequentialType(et) : 
                            unit.getSequenceType(et);
                    if (!requireSequential) {
                        ProducedType it = producedType(unit.getIterableDeclaration(), 
                                et, icc.getFirstTypeModel());
                        result = intersectionType(result, it, unit);
                    }
                }
                else {
                    ut = unionType(ut, et, unit);
                    result = producedType(unit.getTupleDeclaration(), ut, et, result);
                }
            }
        }
        return result;
    }
    
    private ProducedType spreadType(ProducedType et, Unit unit,
            boolean requireSequential) {
        if (et==null) return null;
        if (requireSequential) {
            if (unit.isSequentialType(et)) {
                //if (et.getDeclaration() instanceof TypeParameter) {
                    return et;
                /*}
                else {
                    // if it's already a subtype of Sequential, erase 
                    // out extraneous information, like that it is a
                    // String, just keeping information about what
                    // kind of tuple it is
                    List<ProducedType> elementTypes = unit.getTupleElementTypes(et);
                    boolean variadic = unit.isTupleLengthUnbounded(et);
                    boolean atLeastOne = unit.isTupleVariantAtLeastOne(et);
                    int minimumLength = unit.getTupleMinimumLength(et);
                    if (variadic) {
                        ProducedType spt = elementTypes.get(elementTypes.size()-1);
                        elementTypes.set(elementTypes.size()-1, unit.getIteratedType(spt));
                    }
                    return unit.getTupleType(elementTypes, variadic, 
                            atLeastOne, minimumLength);
                }*/
            }
            else {
                // transform any Iterable into a Sequence without
                // losing the information that it is nonempty, in
                // the case that we know that for sure
                ProducedType st = unit.isNonemptyIterableType(et) ?
                        unit.getSequenceType(unit.getIteratedType(et)) :
                        unit.getSequentialType(unit.getIteratedType(et));
                // unless this is a tuple constructor, remember
                // the original Iterable type arguments, to
                // account for the possibility that the argument
                // to Absent is a type parameter
                //return intersectionType(et.getSupertype(unit.getIterableDeclaration()), st, unit);
                // for now, just return the sequential type:
                return st;
            }
        }
        else {
            return et;
        }
    }
    
    @Override public void visit(Tree.Dynamic that) {
        super.visit(that);
        if (!dynamic) {
            that.addError("dynamic instantiation expression occurs outside dynamic block");
        }
    }
    
    @Override public void visit(Tree.Tuple that) {
        super.visit(that);
        ProducedType tt = null;
        Tree.SequencedArgument sa = that.getSequencedArgument();
        if (sa!=null) {
            tt = getTupleType(sa.getPositionalArguments(), true);
        }
        else {
            tt = unit.getType(unit.getEmptyDeclaration());
        }
        if (tt!=null) {
            that.setTypeModel(tt);
            if (tt.containsUnknowns()) {
                that.addError("tuple element type could not be inferred");
            }
        }
    }

    @Override public void visit(Tree.SequenceEnumeration that) {
        super.visit(that);
        ProducedType st = null;
        Tree.SequencedArgument sa = that.getSequencedArgument();
        if (sa!=null) {
            ProducedType tt = getTupleType(sa.getPositionalArguments(), false);
            if (tt!=null) {
                st = tt.getSupertype(unit.getIterableDeclaration());
                if (st==null) {
                    st = unit.getIterableType(new UnknownType(unit).getType());
                }
            }
        }
        else {
            st = unit.getIterableType(unit.getNothingDeclaration().getType());
        }
        if (st!=null) {
            that.setTypeModel(st);
            if (st.containsUnknowns()) {
                that.addError("iterable element type could not be inferred");
            }
        }
    }

    /*private ProducedType getGenericElementType(List<Tree.Expression> es,
            Tree.Ellipsis ell) {
        List<ProducedType> list = new ArrayList<ProducedType>();
        for (int i=0; i<es.size(); i++) {
            Tree.Expression e = es.get(i);
            if (e.getTypeModel()!=null) {
                ProducedType et = e.getTypeModel();
                if (i==es.size()-1 && ell!=null) {
                    ProducedType it = unit.getIteratedType(et);
                    if (it==null) {
                        ell.addError("last element expression is not iterable: " +
                                et.getProducedTypeName(unit) + " is not an iterable type");
                    }
                    else {
                        addToUnion(list, it);
                    }
                }
                else {
                    addToUnion(list, unit.denotableType(e.getTypeModel()));
                }
            }
        }
        if (list.isEmpty()) {
            return unit.getType(unit.getBooleanDeclaration());
        }
        else if (list.size()==1) {
            return list.get(0);
        }
        else {
            UnionType ut = new UnionType(unit);
            ut.setExtendedType( unit.getType(unit.getObjectDeclaration()) );
            ut.setCaseTypes(list);
            return ut.getType(); 
        }
    }*/

    @Override public void visit(Tree.CatchVariable that) {
        super.visit(that);
        Tree.Variable var = that.getVariable();
        if (var!=null) {
            Tree.Type vt = var.getType();
            if (vt instanceof Tree.LocalModifier) {
                ProducedType et = unit.getType(unit.getExceptionDeclaration());
                vt.setTypeModel(et);
                var.getDeclarationModel().setType(et);
            }
            else {
                ProducedType tt = unit.getType(unit.getThrowableDeclaration());
                checkAssignable(vt.getTypeModel(), tt, vt, 
                        "catch type must be a throwable type");
//                TypeDeclaration d = vt.getTypeModel().getDeclaration();
//                if (d instanceof IntersectionType) {
//                    vt.addUnsupportedError("intersection types in catch not yet supported");
//                }
//                else if (d.isParameterized()) {
//                    vt.addUnsupportedError("parameterized types in catch not yet supported");
//                }
            }
        }
    }
    
    @Override public void visit(Tree.StringTemplate that) {
        super.visit(that);
        for (Tree.Expression e: that.getExpressions()) {
            ProducedType et = e.getTypeModel();
            if (!isTypeUnknown(et)) {
                checkAssignable(et, unit.getType(unit.getObjectDeclaration()), e, 
                        "interpolated expression must not be an optional type");
            }
        }
        setLiteralType(that, unit.getStringDeclaration());
    }
    
    @Override public void visit(Tree.StringLiteral that) {
        setLiteralType(that, unit.getStringDeclaration());
    }
    
    @Override public void visit(Tree.NaturalLiteral that) {
        setLiteralType(that, unit.getIntegerDeclaration());
    }
    
    @Override public void visit(Tree.FloatLiteral that) {
        setLiteralType(that, unit.getFloatDeclaration());
    }
    
    @Override public void visit(Tree.CharLiteral that) {
        String result = that.getText();
        if (result.codePointCount(1, result.length()-1)!=1) {
            that.addError("character literal must contain exactly one character");
        }
        setLiteralType(that, unit.getCharacterDeclaration());
    }
    
    @Override public void visit(Tree.QuotedLiteral that) {
        setLiteralType(that, unit.getStringDeclaration());
    }
    
    private void setLiteralType(Tree.Atom that, TypeDeclaration languageType) {
        that.setTypeModel(unit.getType(languageType));
    }
    
    @Override
    public void visit(Tree.CompilerAnnotation that) {
        //don't visit the argument       
    }
    
    @Override
    public void visit(Tree.MatchCase that) {
        super.visit(that);
        for (Tree.Expression e: that.getExpressionList().getExpressions()) {
            if (e!=null) {
                ProducedType t = e.getTypeModel();
                if (!isTypeUnknown(t)) {
                    if (switchExpression!=null) {
                        ProducedType st = switchExpression.getTypeModel();
                        if (!isTypeUnknown(st)) {
                            if (!hasUncheckedNulls(switchExpression.getTerm()) || !isNullCase(t)) {
                                checkAssignable(t, st, e, 
                                        "case must be assignable to switch expression type");
                            }
                        }
                    }
                    Tree.Term term = e.getTerm();
                    if (term instanceof Tree.NegativeOp) {
                        term = ((Tree.NegativeOp) term).getTerm();
                    }
                    if (term instanceof Tree.Literal) {
                        if (term instanceof Tree.FloatLiteral) {
                            e.addError("literal case may not be a Float literal");
                        }
                    }
                    else if (term instanceof Tree.MemberOrTypeExpression) {
                        ProducedType ut = unionType(unit.getType(unit.getNullDeclaration()), 
                                unit.getType(unit.getIdentifiableDeclaration()), unit);
                        TypeDeclaration dec = t.getDeclaration();
                        if ((!dec.isToplevel() && !dec.isStaticallyImportable()) || !dec.isAnonymous()) {
                            e.addError("case must refer to a toplevel object declaration or literal value");
                        }
                        else {
                            checkAssignable(t, ut, e, "case must be identifiable or null");
                        }
                    }
                    else if (term!=null) {
                        e.addError("case must be a literal value or refer to a toplevel object declaration");
                    }
                }
            }
        }
    }
    
    @Override
    public void visit(Tree.SatisfiesCase that) {
        super.visit(that);
        that.addUnsupportedError("satisfies cases are not yet supported");
    }
    
    @Override
    public void visit(Tree.IsCase that) {
        Tree.Type t = that.getType();
        if (t!=null) {
            t.visit(this);
        }
        Tree.Variable v = that.getVariable();
        if (v!=null) {
            v.visit(this);
            initOriginalDeclaration(v);
        }
        if (switchExpression!=null) {
            ProducedType st = switchExpression.getTypeModel();
            if (t!=null) {
                ProducedType pt = t.getTypeModel();
                ProducedType it = intersectionType(pt, st, unit);
                if (!hasUncheckedNulls(switchExpression.getTerm()) || !isNullCase(pt)) {
                    if (it.isExactly(unit.getNothingDeclaration().getType())) {
                        that.addError("narrows to Nothing type: " + 
                                pt.getProducedTypeName(unit) + " has empty intersection with " + 
                                st.getProducedTypeName(unit));
                    }
                    /*checkAssignable(ct, switchType, cc.getCaseItem(), 
                        "case type must be a case of the switch type");*/
                }
                if (v!=null) {
                    v.getType().setTypeModel(it);
                    v.getDeclarationModel().setType(it);
                }
            }
        }
    }
    
    @Override
    public void visit(Tree.SwitchStatement that) {
        Tree.Expression ose = switchExpression;
        switchExpression = that.getSwitchClause().getExpression();
        super.visit(that);
        Tree.SwitchCaseList switchCaseList = that.getSwitchCaseList();
        if (switchCaseList!=null && switchExpression!=null) {
            checkCases(switchCaseList);
            if (switchCaseList.getElseClause()==null) {
                checkCasesExhaustive(switchCaseList, that.getSwitchClause());
            }
        }
        switchExpression = ose;
    }
    
    private void checkCases(Tree.SwitchCaseList switchCaseList) {
        List<Tree.CaseClause> cases = switchCaseList.getCaseClauses();
        boolean hasIsCase = false;
        for (Tree.CaseClause cc: cases) {
            if (cc.getCaseItem() instanceof Tree.IsCase) {
                hasIsCase = true;
            }
            for (Tree.CaseClause occ: cases) {
                if (occ==cc) break;
                checkCasesDisjoint(cc, occ);
            }
        }
        if (hasIsCase) {
            Tree.Term st = switchExpression.getTerm();
            if (st instanceof Tree.BaseMemberExpression) {
                checkReferenceIsNonVariable((Tree.BaseMemberExpression) st, true);
            }
            else if (st!=null) {
                st.addError("switch expression must be a value reference in switch with type cases", 3102);
            }
        }   
    }
    
    private void checkCasesExhaustive(Tree.SwitchCaseList switchCaseList,
            Tree.SwitchClause switchClause) {
        ProducedType st = switchExpression.getTypeModel();
        if (!isTypeUnknown(st)) {
            //form the union of all the case types
            List<Tree.CaseClause> caseClauses = switchCaseList.getCaseClauses();
            List<ProducedType> list = new ArrayList<ProducedType>(caseClauses.size());
            for (Tree.CaseClause cc: caseClauses) {
                ProducedType ct = getTypeIgnoringLiterals(cc);
                if (isTypeUnknown(ct)) {
                    return; //Note: early exit!
                }
                else {
                    addToUnion(list, ct);
                }
            }
            UnionType ut = new UnionType(unit);
            ut.setCaseTypes(list);
            //if the union of the case types covers 
            //the switch expression type then the 
            //switch is exhaustive
            if (!ut.getType().covers(st)) {
                switchClause.addError("case types must cover all cases of the switch type or an else clause must appear: " +
                                ut.getType().getProducedTypeName(unit) + " does not cover " + 
                                st.getProducedTypeName(unit));
            }
        }
        /*else if (dynamic) {
            that.addError("else clause must appear: static type not known");
        }*/
    }
    
    private void checkCasesDisjoint(Tree.CaseClause cc, Tree.CaseClause occ) {
        Tree.CaseItem cci = cc.getCaseItem();
        Tree.CaseItem occi = occ.getCaseItem();
        if (cci instanceof Tree.IsCase || occi instanceof Tree.IsCase) {
            checkCasesDisjoint(getType(cc), getType(occ), cci);
        }
        else {
            checkCasesDisjoint(getTypeIgnoringLiterals(cc),
                    getTypeIgnoringLiterals(occ), cci);
        }
        if (cci instanceof Tree.MatchCase && occi instanceof Tree.MatchCase) {
            checkLiteralsDisjoint((Tree.MatchCase) cci, (Tree.MatchCase) occi);
        }
    }
    
    private void checkLiteralsDisjoint(Tree.MatchCase cci, Tree.MatchCase occi) {
        for (Tree.Expression e: cci.getExpressionList().getExpressions()) {
            for (Tree.Expression f: occi.getExpressionList().getExpressions()) {
                Tree.Term et = e.getTerm();
                Tree.Term ft = f.getTerm();
                boolean eneg = et instanceof Tree.NegativeOp;
                boolean fneg = ft instanceof Tree.NegativeOp;
                if (eneg) {
                    et = ((Tree.NegativeOp) et).getTerm();
                }
                if (fneg) {
                    ft = ((Tree.NegativeOp) ft).getTerm();
                }
                if (et instanceof Tree.Literal && ft instanceof Tree.Literal) {
                    String ftv = getLiteralText(ft);
                    String etv = getLiteralText(et);
                    if (et instanceof Tree.NaturalLiteral && 
                        ft instanceof Tree.NaturalLiteral &&
                        ((ftv.startsWith("#") && !etv.startsWith("#")) ||
                        (!ftv.startsWith("#") && etv.startsWith("#")) ||
                        (ftv.startsWith("$") && !etv.startsWith("$")) ||
                        (!ftv.startsWith("$") && etv.startsWith("$")))) {
                        cci.addUnsupportedError("literal cases with mixed bases not yet supported");
                    }
                    else if (etv.equals(ftv) && eneg==fneg) {
                        cci.addError("literal cases must be disjoint: " +
                                (eneg?"-":"") +
                                etv.replaceAll("\\p{Cntrl}","?") + 
                                " occurs in multiple cases");
                    }
                }
            }
        }
    }

    private static String getLiteralText(Tree.Term et) {
        String etv = et.getText();
        if (et instanceof Tree.CharLiteral) {
            return "'" + etv + "'"; 
        }
        else if (et instanceof Tree.StringLiteral) {
            return "\"" + etv + "\"";
        }
        else {
            return etv;
        }
    }

    private boolean isNullCase(ProducedType ct) {
        TypeDeclaration d = ct.getDeclaration();
        return d!=null && d instanceof Class &&
                d.equals(unit.getNullDeclaration());
    }

    private ProducedType getType(Tree.CaseItem ci) {
        Tree.Type t = ((Tree.IsCase) ci).getType();
        if (t!=null) {
            return t.getTypeModel().getUnionOfCases();
        }
        else {
            return null;
        }
    }
    
    private ProducedType getType(Tree.CaseClause cc) {
        Tree.CaseItem ci = cc.getCaseItem();
        if (ci instanceof Tree.IsCase) {
            return getType(ci);
        }
        else if (ci instanceof Tree.MatchCase) {
            List<Tree.Expression> es = ((Tree.MatchCase) ci).getExpressionList().getExpressions();
            List<ProducedType> list = new ArrayList<ProducedType>(es.size());
            for (Tree.Expression e: es) {
                if (e.getTypeModel()!=null) {
                    addToUnion(list, e.getTypeModel());
                }
            }
            return formUnion(list);
        }
        else {
            return null;
        }
    }

    private ProducedType getTypeIgnoringLiterals(Tree.CaseClause cc) {
        Tree.CaseItem ci = cc.getCaseItem();
        if (ci instanceof Tree.IsCase) {
            return getType(ci);
        }
        else if (ci instanceof Tree.MatchCase) {
            List<Tree.Expression> es = ((Tree.MatchCase) ci).getExpressionList().getExpressions();
            List<ProducedType> list = new ArrayList<ProducedType>(es.size());
            for (Tree.Expression e: es) {
                if (e.getTypeModel()!=null && 
                        !(e.getTerm() instanceof Tree.Literal) && 
                        !(e.getTerm() instanceof Tree.NegativeOp)) {
                    addToUnion(list, e.getTypeModel());
                }
            }
            return formUnion(list);
        }
        else {
            return null;
        }
    }
    
    @Override
    public void visit(Tree.TryCatchStatement that) {
        super.visit(that);
        for (Tree.CatchClause cc: that.getCatchClauses()) {
            if (cc.getCatchVariable()!=null && 
                    cc.getCatchVariable().getVariable()!=null) {
                ProducedType ct = cc.getCatchVariable()
                        .getVariable().getType().getTypeModel();
                if (ct!=null) {
                    for (Tree.CatchClause ecc: that.getCatchClauses()) {
                        if (ecc.getCatchVariable()!=null &&
                                ecc.getCatchVariable().getVariable()!=null) {
                            if (cc==ecc) break;
                            ProducedType ect = ecc.getCatchVariable()
                                    .getVariable().getType().getTypeModel();
                            if (ect!=null) {
                                if (ct.isSubtypeOf(ect)) {
                                    cc.getCatchVariable().getVariable().getType()
                                            .addError("exception type is already handled by earlier catch clause:" 
                                                    + ct.getProducedTypeName(unit));
                                }
                                if (ct.getDeclaration() instanceof UnionType) {
                                    for (ProducedType ut: ct.getDeclaration().getCaseTypes()) {
                                        if ( ut.substitute(ct.getTypeArguments()).isSubtypeOf(ect) ) {
                                            cc.getCatchVariable().getVariable().getType()
                                                    .addError("exception type is already handled by earlier catch clause: "
                                                            + ut.getProducedTypeName(unit));
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    @Override
    public void visit(Tree.DynamicStatement that) {
        boolean od = dynamic;
        dynamic = true;
        super.visit(that);
        dynamic = od;
    }
    
    private boolean acceptsTypeArguments(Declaration member, List<ProducedType> typeArguments, 
            Tree.TypeArguments tal, Node parent, boolean metamodel) {
        return acceptsTypeArguments(null, member, typeArguments, tal, parent, metamodel);
    }
    
    private static boolean isGeneric(Declaration member) {
        return member instanceof Generic && 
            !((Generic) member).getTypeParameters().isEmpty();
    }
    
    private boolean acceptsTypeArguments(ProducedType receiver, Declaration dec, 
            List<ProducedType> typeArguments, Tree.TypeArguments tal, Node parent,
            boolean metamodel) {
        if (dec==null) return false;
        if (isGeneric(dec)) {
            List<TypeParameter> params = ((Generic) dec).getTypeParameters();
            int min = 0;
            for (TypeParameter tp: params) { 
                if (!tp.isDefaulted()) min++;
            }
            int max = params.size();
            int args = typeArguments.size();
            if (args<=max && args>=min) {
                for (int i=0; i<args; i++) {
                    TypeParameter param = params.get(i);
                    ProducedType argType = typeArguments.get(i);
                    //Map<TypeParameter, ProducedType> self = Collections.singletonMap(param, arg);
                    boolean argTypeMeaningful = argType!=null && 
                            !(argType.getDeclaration() instanceof UnknownType);
                    for (ProducedType st: param.getSatisfiedTypes()) {
                        //sts = sts.substitute(self);
                        ProducedType sts = st.getProducedType(receiver, dec, typeArguments);
                        if (argType!=null) {
                            if (!isCondition && !argType.isSubtypeOf(sts)) {
                                if (argTypeMeaningful) {
                                    if (tal instanceof Tree.InferredTypeArguments) {
                                        parent.addError("inferred type argument " + argType.getProducedTypeName(unit)
                                                + " to type parameter " + param.getName()
                                                + " of declaration " + dec.getName(unit)
                                                + " not assignable to upper bound " + sts.getProducedTypeName(unit)
                                                + " of " + param.getName());
                                    }
                                    else {
                                        ((Tree.TypeArgumentList) tal).getTypes()
                                                .get(i).addError("type parameter " + param.getName() 
                                                        + " of declaration " + dec.getName(unit)
                                                        + " has argument " + argType.getProducedTypeName(unit) 
                                                        + " not assignable to upper bound " + sts.getProducedTypeName(unit)
                                                        + " of " + param.getName(), 2102);
                                    }
                                }
                                return false;
                            }
                        }
                    }
                    if (!isCondition && 
                            !argumentSatisfiesEnumeratedConstraint(receiver, dec, 
                                    typeArguments, argType, param)) {
                        if (argTypeMeaningful) {
                            if (tal instanceof Tree.InferredTypeArguments) {
                                parent.addError("inferred type argument " + argType.getProducedTypeName(unit)
                                        + " to type parameter " + param.getName()
                                        + " of declaration " + dec.getName(unit)
                                        + " not one of the enumerated cases of " + param.getName());
                            }
                            else {
                                ((Tree.TypeArgumentList) tal).getTypes()
                                        .get(i).addError("type parameter " + param.getName() 
                                                + " of declaration " + dec.getName(unit)
                                                + " has argument " + argType.getProducedTypeName(unit) 
                                                + " not one of the enumerated cases of " + param.getName());
                            }
                        }
                        return false;
                    }
                }
                return true;
            }
            else {
                if (tal==null || tal instanceof Tree.InferredTypeArguments) {
                    if (!metamodel) {
                        parent.addError("missing type arguments to generic type: " + 
                                dec.getName(unit) + " declares type parameters");
                    }
                }
                else {
                    String help="";
                    if (args<min) {
                        help = " requires at least " + min + " type arguments";
                    }
                    else if (args>max) {
                        help = " allows at most " + max + " type arguments";
                    }
                    tal.addError("wrong number of type arguments: " + 
                            dec.getName(unit) + help);
                }
                return false;
            }
        }
        else {
            boolean empty = typeArguments.isEmpty();
            if (!empty) {
                tal.addError("does not accept type arguments: " + 
                        dec.getName(unit));
            }
            return empty;
        }
    }

    public static boolean argumentSatisfiesEnumeratedConstraint(ProducedType receiver, 
            Declaration member, List<ProducedType> typeArguments, ProducedType argType,
            TypeParameter param) {
        
        List<ProducedType> caseTypes = param.getCaseTypes();
        if (caseTypes==null || caseTypes.isEmpty()) {
            //no enumerated constraint
            return true;
        }
        
        //if the type argument is a subtype of one of the cases
        //of the type parameter then the constraint is satisfied
        for (ProducedType ct: caseTypes) {
            ProducedType cts = ct.getProducedType(receiver, member, typeArguments);
            if (argType.isSubtypeOf(cts)) {
                return true;
            }
        }

        //if the type argument is itself a type parameter with
        //an enumerated constraint, and every enumerated case
        //is a subtype of one of the cases of the type parameter,
        //then the constraint is satisfied
        if (argType.getDeclaration() instanceof TypeParameter) {
            List<ProducedType> argCaseTypes = argType.getDeclaration().getCaseTypes();
            if (argCaseTypes!=null && !argCaseTypes.isEmpty()) {
                for (ProducedType act: argCaseTypes) {
                    boolean foundCase = false;
                    for (ProducedType ct: caseTypes) {
                        ProducedType cts = ct.getProducedType(receiver, member, typeArguments);
                        if (act.isSubtypeOf(cts)) {
                            foundCase = true;
                            break;
                        }
                    }
                    if (!foundCase) {
                        return false;
                    }
                }
                return true;
            }
        }

        return false;
    }

    private void visitExtendedOrAliasedType(Tree.SimpleType et,
            Tree.InvocationExpression ie) {
        if (et!=null && ie!=null) {
            ProducedType type = et.getTypeModel();
            if (type!=null) {
                Tree.Primary pr = ie.getPrimary();
                if (pr instanceof Tree.InvocationExpression) {
                    Tree.InvocationExpression iie = (Tree.InvocationExpression) pr;
                    pr = iie.getPrimary();
                }
                if (pr instanceof Tree.ExtendedTypeExpression) {
                    Tree.ExtendedTypeExpression ete = (Tree.ExtendedTypeExpression) pr;
                    ete.setDeclaration(et.getDeclarationModel());
                    ete.setTarget(type);
                    ProducedType qt = type.getQualifyingType();
                    ProducedType ft = type.getFullType();
                    if (ete.getStaticMethodReference()) {
                        ft = producedType(unit.getCallableDeclaration(), ft, 
                                producedType(unit.getTupleDeclaration(), qt, qt, 
                                        unit.getType(unit.getEmptyDeclaration())));
                    }
                    pr.setTypeModel(ft);
                }
            }
        }
    }
    
    @Override 
    public void visit(Tree.ClassSpecifier that) {
        Tree.InvocationExpression ie = that.getInvocationExpression();
        visitExtendedOrAliasedType(that.getType(), ie);
        
        inExtendsClause = true;
        super.visit(that);
        inExtendsClause = false;
        
        //Dupe check:
        /*if (ie!=null && 
                ie.getPrimary() instanceof Tree.MemberOrTypeExpression) {
            checkOverloadedReference((Tree.MemberOrTypeExpression) ie.getPrimary());
        }*/
    }

    @Override 
    public void visit(Tree.ExtendedType that) {
        visitExtendedOrAliasedType(that.getType(), 
                that.getInvocationExpression());
        
        inExtendsClause = true;
        super.visit(that);
        inExtendsClause = false;
                
        TypeDeclaration td = (TypeDeclaration) that.getScope();
        Tree.SimpleType et = that.getType();
        if (et!=null) {
            ProducedType type = et.getTypeModel();
            if (type!=null) {
                checkSelfTypes(et, td, type);
                checkExtensionOfMemberType(et, td, type);
                //checkCaseOfSupertype(et, td, type);
                if (!td.isAlias()) {
                    TypeDeclaration etd = td.getExtendedTypeDeclaration();
                    while (etd!=null && etd.isAlias()) {
                        etd = etd.getExtendedTypeDeclaration();
                    }
                    if (etd!=null) {
                        if (etd.isFinal()) {
                            et.addError("extends a final class: " + 
                                    etd.getName(unit));
                        }
                    	if (etd.isSealed() && !inSameModule(etd)) {
                    		et.addError("extends a sealed class in a different module: " +
                    				etd.getName(unit) + " in " + 
                    				etd.getUnit().getPackage().getModule().getNameAsString());
                    	}
                    }
                }
//                if (td.isParameterized() &&
//                        type.getDeclaration().inherits(unit.getExceptionDeclaration())) {
//                    et.addUnsupportedError("generic exception types not yet supported");
//                }
            }
            checkSupertypeVarianceAnnotations(et);
        }
    }

    private void checkSupertypeVarianceAnnotations(Tree.SimpleType et) {
        Tree.TypeArgumentList tal = et.getTypeArgumentList();
        if (tal!=null) {
            for (Tree.Type t: tal.getTypes()) {
                if (t instanceof Tree.StaticType) {
                    TypeVariance variance = ((Tree.StaticType) t).getTypeVariance();
                    if (variance!=null) {
                        variance.addError("supertype expression may not specify variance");
                    }
                }
            }
        }
    }

	private boolean inSameModule(TypeDeclaration etd) {
	    return etd.getUnit().getPackage().getModule()
	    		.equals(unit.getPackage().getModule());
    }

    @Override 
    public void visit(Tree.SatisfiedTypes that) {
        super.visit(that);
        TypeDeclaration td = (TypeDeclaration) that.getScope();
        Set<TypeDeclaration> set = new HashSet<TypeDeclaration>();
        if (td.getSatisfiedTypes().isEmpty()) return; //handle undecidability case
        for (Tree.StaticType t: that.getTypes()) {
            ProducedType type = t.getTypeModel();
            if (type!=null && type.getDeclaration()!=null) {
                type = type.resolveAliases();
                TypeDeclaration std = type.getDeclaration();
                if (td instanceof ClassOrInterface &&
                        !inLanguageModule(that.getUnit())) {
                    if (unit.isCallableType(type)) {
                        t.addError("satisfies Callable");
                    }
                    if (type.getDeclaration().equals(unit.getConstrainedAnnotationDeclaration())) {
                        t.addError("directly satisfies ConstrainedAnnotation");
                    }
                }
                if (!set.add(type.getDeclaration())) {
                    //this error is not really truly necessary
                    //but the spec says it is an error, and
                    //the backend doesn't like it
                    t.addError("duplicate satisfied type: " + 
                            type.getDeclaration().getName(unit) +
                            " of " + td.getName());
                }
            	if (td instanceof ClassOrInterface && 
            			std.isSealed() && !inSameModule(std)) {
        			t.addError("satisfies a sealed interface in a different module: " +
        					std.getName(unit) + " in " + 
        					std.getUnit().getPackage().getModule().getNameAsString());
            	}
                checkSelfTypes(t, td, type);
                checkExtensionOfMemberType(t, td, type);
                /*if (!(td instanceof TypeParameter)) {
                    checkCaseOfSupertype(t, td, type);
                }*/
            }
            if (t instanceof Tree.SimpleType) {
                checkSupertypeVarianceAnnotations((Tree.SimpleType) t);
            }
        }
        //Moved to RefinementVisitor, which 
        //handles this kind of stuff:
        /*if (td instanceof TypeParameter) {
            List<ProducedType> list = new ArrayList<ProducedType>();
            for (ProducedType st: td.getSatisfiedTypes()) {
                addToIntersection(list, st, unit);
            }
            IntersectionType it = new IntersectionType(unit);
            it.setSatisfiedTypes(list);
            if (it.getType().getDeclaration() instanceof NothingType) {
                that.addError("upper bound constraints cannot be satisfied by any type except Nothing");
            }
        }*/
    }

    /*void checkCaseOfSupertype(Tree.StaticType t, TypeDeclaration td,
            ProducedType type) {
        //TODO: I think this check is a bit too restrictive, since 
        //      it doesn't allow intermediate types between the 
        //      enumerated type and the case type, but since the
        //      similar check below doesn't work, we need it
        if (type.getDeclaration().getCaseTypes()!=null) {
            for (ProducedType ct: type.getDeclaration().getCaseTypes()) {
                if (ct.substitute(type.getTypeArguments())
                        .isExactly(td.getType())) {
                    return;
                }
            }
            t.addError("not a case of supertype: " + 
                    type.getDeclaration().getName(unit));
        }
    }*/

    @Override 
    public void visit(Tree.CaseTypes that) {
        super.visit(that);
        //this forces every case to be a subtype of the
        //enumerated type, so that we can make use of the
        //enumerated type is equivalent to its cases
        TypeDeclaration td = (TypeDeclaration) that.getScope();
        
        //TODO: get rid of this awful hack:
        List<ProducedType> cases = td.getCaseTypes();
        td.setCaseTypes(null);
        
        if (td instanceof TypeParameter) {
            for (Tree.StaticType t: that.getTypes()) {
                for (Tree.StaticType ot: that.getTypes()) {
                    if (t==ot) break;
                    checkCasesDisjoint(t.getTypeModel(), ot.getTypeModel(), ot);
                }
            }
        }
        else {
            Set<TypeDeclaration> set = new HashSet<TypeDeclaration>();
            for (Tree.StaticType st: that.getTypes()) {
                ProducedType type = st.getTypeModel();
                TypeDeclaration ctd = type.getDeclaration();
                if (type!=null && ctd!=null) {
                    type = type.resolveAliases();
                    if (!set.add(ctd)) {
                        //this error is not really truly necessary
                        st.addError("duplicate case type: " + 
                                ctd.getName(unit) + 
                                " of " + td.getName());
                    }
                    if (!(ctd instanceof TypeParameter)) {
                        //it's not a self type
                        if (type!=null) {
                            checkAssignable(type, td.getType(), st,
                                    getCaseTypeExplanation(td, type));
                            //note: this is a better, faster way to call 
                            //      validateEnumeratedSupertypeArguments()
                            //      but unfortunately it winds up displaying
                            //      the error on the wrong node, confusing
                            //      the user
                            /*ProducedType supertype = type.getDeclaration().getType().getSupertype(td);
                            validateEnumeratedSupertypeArguments(t, type.getDeclaration(), supertype);*/
                        }
                    }
                    if (ctd instanceof ClassOrInterface && st instanceof Tree.SimpleType) {
                        Tree.TypeArgumentList tal = ((Tree.SimpleType) st).getTypeArgumentList();
                        if (tal!=null) {
                            List<Tree.Type> args = tal.getTypes();
                            List<TypeParameter> typeParameters = ctd.getTypeParameters();
                            for (int i=0; i<args.size() && i<typeParameters.size(); i++) {
                                Tree.Type arg = args.get(i);
                                TypeParameter typeParameter = ctd.getTypeParameters().get(i);
                                ProducedType argType = arg.getTypeModel();
                                if (argType!=null) {
                                    TypeDeclaration argTypeDec = argType.getDeclaration();
                                    if (argTypeDec instanceof TypeParameter) {
                                        if (!((TypeParameter) argTypeDec).getDeclaration().equals(td)) {
                                            arg.addError("type argument is not a type parameter of the enumerated type: " +
                                                    argTypeDec.getName() + " is not a type parameter of " + td.getName());
                                        }
                                    }
                                    else if (typeParameter.isCovariant()) {
                                        Util.checkAssignable(typeParameter.getType(), argType, arg, 
                                                "type argument not an upper bound of the type parameter");
                                    }
                                    else if (typeParameter.isContravariant()) {
                                        Util.checkAssignable(argType, typeParameter.getType(), arg, 
                                                "type argument not a lower bound of the type parameter");
                                    }
                                    else {
                                        arg.addError("type argument is not a type parameter of the enumerated type: " +
                                                argTypeDec.getName());
                                    }
                                }
                            }
                        }
                    }
                }
            }
            for (Tree.BaseMemberExpression bme: that.getBaseMemberExpressions()) {
                ProducedType type = bme.getTypeModel();
                Declaration d = bme.getDeclaration();
                if (d!=null && type!=null && 
                        !type.getDeclaration().isAnonymous()) {
                    bme.addError("case must be a toplevel anonymous class: " + 
                            d.getName(unit) + " is not an anonymous class");
                }
                else if (d!=null && !d.isToplevel()) {
                    bme.addError("case must be a toplevel anonymous class: " + 
                            d.getName(unit) + " is not toplevel");
                }
                if (type!=null) {
                    checkAssignable(type, td.getType(), bme, 
                            getCaseTypeExplanation(td, type));
                }
            }
        }
        
        //TODO: get rid of this awful hack:
        td.setCaseTypes(cases);
    }

    private String getCaseTypeExplanation(TypeDeclaration td, 
            ProducedType type) {
        String message = "case type must be a subtype of enumerated type";
        if (!td.getTypeParameters().isEmpty() &&
                type.getDeclaration().inherits(td)) {
            message += " for every type argument of the generic enumerated type";
        }
        return message;
    }

    private void checkCasesDisjoint(ProducedType type, ProducedType other,
            Node ot) {
        if (!isTypeUnknown(type) && !isTypeUnknown(other)) {
            if (!intersectionType(type.resolveAliases(), other.resolveAliases(), unit).isNothing()) {
                ot.addError("cases are not disjoint: " + 
                        type.getProducedTypeName(unit) + " and " + 
                        other.getProducedTypeName(unit));
            }
        }
    }

    private void checkExtensionOfMemberType(Node that, TypeDeclaration td,
            ProducedType type) {
        ProducedType qt = type.getQualifyingType();
        if (qt!=null && td instanceof ClassOrInterface &&
        		!type.getDeclaration().isStaticallyImportable()) {
            Scope s = td;
            while (s!=null) {
                s = s.getContainer();
                if (s instanceof TypeDeclaration) {
                	TypeDeclaration otd = (TypeDeclaration) s;
                    if (otd.getType().isSubtypeOf(qt)) {
                        return;
                    }
                }
            }
            that.addError("qualifying type " + qt.getProducedTypeName(unit) + 
                    " of supertype " + type.getProducedTypeName(unit) + 
                    " is not an outer type or supertype of any outer type of " +
                    td.getName(unit));
        }
    }
    
    private void checkSelfTypes(Tree.StaticType that, TypeDeclaration td, ProducedType type) {
        if (!(td instanceof TypeParameter)) { //TODO: is this really ok?!
            List<TypeParameter> params = type.getDeclaration().getTypeParameters();
            List<ProducedType> args = type.getTypeArgumentList();
            for (int i=0; i<params.size(); i++) {
                TypeParameter param = params.get(i);
                if ( param.isSelfType() && args.size()>i ) {
                    ProducedType arg = args.get(i);
                    if (arg==null) arg = new UnknownType(unit).getType();
                    TypeDeclaration std = param.getSelfTypedDeclaration();
                    ProducedType at;
                    TypeDeclaration mtd;
                    if (param.getContainer().equals(std)) {
                        at = td.getType();
                        mtd = td;
                    }
                    else {
                        //TODO: lots wrong here?
                        mtd = (TypeDeclaration) td.getMember(std.getName(), null, false);
                        at = mtd==null ? null : mtd.getType();
                    }
                    if (at!=null && !at.isSubtypeOf(arg) && 
                            !(mtd.getSelfType()!=null && 
                                mtd.getSelfType().isExactly(arg))) {
                        String help;
                        TypeDeclaration ad = arg.getDeclaration();
                        if (ad instanceof TypeParameter &&
                                ((TypeParameter) ad).getDeclaration().equals(td)) {
                            help = " (try making " + ad.getName() + " a self type of " + td.getName() + ")";
                        }
                        else if (ad instanceof Interface) {
                            help = " (try making " + td.getName() + " satisfy " + ad.getName() + ")";
                        }
                        else if (ad instanceof Class && td instanceof Class) {
                            help = " (try making " + td.getName() + " extend " + ad.getName() + ")";
                        }
                        else {
                            help = "";
                        }
                        that.addError("type argument does not satisfy self type constraint on type parameter " +
                                param.getName() + " of " + type.getDeclaration().getName(unit) + ": " +
                                arg.getProducedTypeName(unit) + " is not a supertype or self type of " + 
                                td.getName(unit) + help);
                    }
                }
            }
        }
    }

    private void validateEnumeratedSupertypes(Node that, ClassOrInterface d) {
        ProducedType type = d.getType();
        for (ProducedType supertype: type.getSupertypes()) {
            if (!type.isExactly(supertype)) {
                TypeDeclaration std = supertype.getDeclaration();
                if (std.getCaseTypes()!=null && !std.getCaseTypes().isEmpty()) {
                    if (std.getCaseTypes().size()==1 && 
                            std.getCaseTypeDeclarations().get(0).isSelfType()) {
                        continue;
                    }
                    List<ProducedType> types=new ArrayList<ProducedType>(std.getCaseTypes().size());
                    for (ProducedType ct: std.getCaseTypes()) {
                        ProducedType cst = type.getSupertype(ct.getDeclaration());
                        if (cst!=null) {
                            types.add(cst);
                        }
                    }
                    if (types.isEmpty()) {
                        that.addError("not a subtype of any case of enumerated supertype: " + 
                                d.getName(unit) + " is a subtype of " + std.getName(unit));
                    }
                    else if (types.size()>1) {
                        StringBuilder sb = new StringBuilder();
                        for (ProducedType pt: types) {
                            sb.append(pt.getProducedTypeName(unit)).append(" and ");
                        }
                        sb.setLength(sb.length()-5);
                        that.addError("concrete type is a subtype of multiple cases of enumerated supertype: " + 
                                d.getName(unit) + " is a subtype of " + sb);
                    }
                }
            }
        }
    }

    private void validateEnumeratedSupertypeArguments(Node that, ClassOrInterface d) {
        //note: I hate doing the whole traversal here, but it is the
        //      only way to get the error in the right place (see
        //      the note in visit(CaseTypes) for more)
        ProducedType type = d.getType();
        for (ProducedType supertype: type.getSupertypes()) { //traverse the entire supertype hierarchy of the declaration
            if (!type.isExactly(supertype)) {
                List<TypeDeclaration> ctds = supertype.getDeclaration().getCaseTypeDeclarations();
                if (ctds!=null) {
                    for (TypeDeclaration ct: ctds) {
                        if (ct.equals(d)) { //the declaration is a case of the current enumerated supertype
                            validateEnumeratedSupertypeArguments(that, d, supertype);
                            break;
                        }
                    }
                }
            }
        }
    }

    private void validateEnumeratedSupertypeArguments(Node that, TypeDeclaration d, 
            ProducedType supertype) {
        for (TypeParameter p: supertype.getDeclaration().getTypeParameters()) {
            ProducedType arg = supertype.getTypeArguments().get(p); //the type argument that the declaration (indirectly) passes to the enumerated supertype
            if (arg!=null) {
                validateEnumeratedSupertypeArgument(that, d, supertype, p, arg);
            }
        }
    }

    private void validateEnumeratedSupertypeArgument(Node that, TypeDeclaration d, 
            ProducedType supertype, TypeParameter p, ProducedType arg) {
        TypeDeclaration td = arg.getDeclaration();
        if (td instanceof TypeParameter) {
            TypeParameter tp = (TypeParameter) td;
            if (tp.getDeclaration().equals(d)) { //the argument is a type parameter of the declaration
                //check that the variance of the argument type parameter is
                //the same as the type parameter of the enumerated supertype
                if (p.isCovariant() && !tp.isCovariant()) {
                    that.addError("argument to covariant type parameter of enumerated supertype must be covariant: " + 
                            typeDescription(p, unit));
                }
                if (p.isContravariant() && !tp.isContravariant()) {
                    that.addError("argument to contravariant type parameter of enumerated supertype must be contravariant: " + 
                            typeDescription(p, unit));
                }
            }
            else {
                that.addError("argument to type parameter of enumerated supertype must be a type parameter of " +
                        d.getName() + ": " + typeDescription(p, unit));
            }
        }
        else if (p.isCovariant()) {
            if (!(td instanceof NothingType)) {
                //TODO: let it be the union of the lower bounds on p
                that.addError("argument to covariant type parameter of enumerated supertype must be a type parameter or Nothing: " + 
                        typeDescription(p, unit));
            }
        }
        else if (p.isContravariant()) {
            List<ProducedType> sts = p.getSatisfiedTypes();
            //TODO: do I need to do type arg substitution here??
            ProducedType ub = formIntersection(sts);
            if (!(arg.isExactly(ub))) {
                that.addError("argument to contravariant type parameter of enumerated supertype must be a type parameter or " + 
                        typeNamesAsIntersection(sts, unit) + ": " + 
                        typeDescription(p, unit));
            }
        }
        else {
            that.addError("argument to type parameter of enumerated supertype must be a type parameter: " + 
                    typeDescription(p, unit));
        }
    }
    
    @Override public void visit(Tree.Term that) {
        super.visit(that);
        if (that.getTypeModel()==null) {
            that.setTypeModel( defaultType() );
        }
    }

    @Override public void visit(Tree.Type that) {
        super.visit(that);
        if (that.getTypeModel()==null) {
            that.setTypeModel( defaultType() );
        }
    }

    private ProducedType defaultType() {
        TypeDeclaration ut = new UnknownType(unit);
        Class ad = unit.getAnythingDeclaration();
        if (ad!=null) {
            ut.setExtendedType(ad.getType());
        }
        return ut.getType();
    }
    
    @Override
    public void visit(Tree.PackageLiteral that) {
        super.visit(that);
        Package p = TypeVisitor.getPackage(that.getImportPath());
        that.getImportPath().setModel(p);
        that.setTypeModel(unit.getPackageDeclarationType());
    }
    
    @Override
    public void visit(Tree.ModuleLiteral that) {
        super.visit(that);
        Module m = TypeVisitor.getModule(that.getImportPath());
        that.getImportPath().setModel(m);
        that.setTypeModel(unit.getModuleDeclarationType());
    }
    
    @Override
    public void visit(Tree.TypeLiteral that) {
        super.visit(that);
        ProducedType t = null;
        TypeDeclaration d = null;
        Tree.StaticType type = that.getType();
        Tree.BaseMemberExpression oe = that.getObjectExpression();
		if (type != null) {
        	t = type.getTypeModel();
        	d = t.getDeclaration();
        }
		else if (oe != null ) {
        	t = oe.getTypeModel();
        	d = t.getDeclaration();
        	if (!d.isAnonymous()) {
        		oe.addError("must be a reference to an anonymous class");
        	}
        }
        // FIXME: should we disallow type parameters in there?
        if (t != null) {
            that.setDeclaration(d);
            that.setWantsDeclaration(true);
            if (that instanceof Tree.ClassLiteral) {
                if (!(d instanceof Class)) {
                    if(type != null)
                        type.addError("referenced declaration is not a class" +
                                getDeclarationReferenceSuggestion(d));
                }
                else {
//                        checkNonlocal(that, d);
                }
                that.setTypeModel(unit.getClassDeclarationType());
            }
            else if (that instanceof Tree.InterfaceLiteral) {
                if (!(d instanceof Interface)) {
                    type.addError("referenced declaration is not an interface" +
                            getDeclarationReferenceSuggestion(d));
                }
                that.setTypeModel(unit.getInterfaceDeclarationType());
            }
            else if (that instanceof Tree.AliasLiteral) {
                if (!(d instanceof TypeAlias)) {
                    type.addError("referenced declaration is not a type alias" +
                            getDeclarationReferenceSuggestion(d));
                }
                that.setTypeModel(unit.getAliasDeclarationType());
            }
            else if (that instanceof Tree.TypeParameterLiteral) {
                if (!(d instanceof TypeParameter)) {
                    type.addError("referenced declaration is not a type parameter" +
                            getDeclarationReferenceSuggestion(d));
                }
                that.setTypeModel(unit.getTypeParameterDeclarationType());
            }
            else if (d != null) {
                that.setWantsDeclaration(false);
                t = t.resolveAliases();
                //checkNonlocalType(that.getType(), t.getDeclaration());
                if (d instanceof Class) {
//                        checkNonlocal(that, t.getDeclaration());
                    if (((Class) d).isAbstraction()) {
                        that.addError("class constructor is overloaded");
                    }
                    else {
                        that.setTypeModel(unit.getClassMetatype(t));
                    }
                }
                else if (d instanceof Interface) {
                    that.setTypeModel(unit.getInterfaceMetatype(t));
                }
                else {
                    that.setTypeModel(unit.getTypeMetaType(t));
                }
            }
        }
    }
    
    @Override
    public void visit(Tree.MemberLiteral that) {
        super.visit(that);
        if (that.getIdentifier() != null) {
            String name = name(that.getIdentifier());
            ProducedType qt = null;
            TypeDeclaration qtd = null;
            Tree.StaticType type = that.getType();
            Tree.BaseMemberExpression oe = that.getObjectExpression();
			if (type != null) {
            	qt = type.getTypeModel();
            	qtd = qt.getDeclaration();
            }
			else if (oe != null) {
            	qt = oe.getTypeModel();
            	qtd = qt.getDeclaration();
            	if (!qtd.isAnonymous()) {
            		oe.addError("must be a reference to an anonymous class");
            	}
            }
            if (qt != null) {
            	qt = qt.resolveAliases();
            	if (qtd instanceof UnknownType) {
            		// let it go, we already logged an error for the missing type
            		return;
            	}
            	//checkNonlocalType(that.getType(), qtd);
            	String container = "type " + qtd.getName(unit);
            	TypedDeclaration member = getTypedMember(qtd, name, null, false, unit);
            	if (member==null) {
            		if (qtd.isMemberAmbiguous(name, unit, null, false)) {
            			that.addError("method or attribute is ambiguous: " +
            					name + " for " + container);
            		}
            		else {
            			that.addError("method or attribute does not exist: " +
            					name + " in " + container);
            		}
            	}
            	else {
            		checkQualifiedVisibility(that, member, name, container, false);
            		setMemberMetatype(that, member);
            	}
            }
            else {
                TypedDeclaration result = getTypedDeclaration(that.getScope(), 
                        name, null, false, unit);
                if (result!=null) {
                    checkBaseVisibility(that, result, name);
                    setMemberMetatype(that, result);
                }
                else {
                    that.addError("function or value does not exist: " +
                            name(that.getIdentifier()), 100);
                    unit.getUnresolvedReferences().add(that.getIdentifier());
                }
            }
        }
    }

    private void setMemberMetatype(Tree.MemberLiteral that, TypedDeclaration result) {
        that.setDeclaration(result);
        if (that instanceof Tree.ValueLiteral) {
            if (result instanceof Value) {
                checkNonlocal(that, result);
            }
            else {
                that.getIdentifier().addError("referenced declaration is not a value" + 
                        getDeclarationReferenceSuggestion(result));
            }
            if (that.getBroken()) {
                that.addError("keyword object may not appear here: " +
                              "use the value keyword to refer to anonymous class declarations");
            }
            that.setWantsDeclaration(true);
            that.setTypeModel(unit.getValueDeclarationType(result));
        }
        else if (that instanceof Tree.FunctionLiteral) {
            if (result instanceof Method) {
                checkNonlocal(that, result);
            }
            else {
                that.getIdentifier().addError("referenced declaration is not a function" + 
                        getDeclarationReferenceSuggestion(result));
            }
            that.setWantsDeclaration(true);
            that.setTypeModel(unit.getFunctionDeclarationType());
        }
        else {
            checkNonlocal(that, result);
            setMetamodelType(that, result);
        }
    }

    private String getDeclarationReferenceSuggestion(Declaration result) {
        String name = ": " + result.getName(unit);
        if (result instanceof Method) {
            return name + " is a function";
        }
        else if (result instanceof Value) {
            return name + " is a value";
        }
        else if (result instanceof Class) {
            return name + " is a class";
        }
        else if (result instanceof Interface) {
            return name + " is an interface";
        }
        else if (result instanceof TypeAlias) {
            return name + " is a type alias";
        }
        else if (result instanceof TypeParameter) {
            return name + " is a type parameter";
        }
        return "";
    }

    private void setMetamodelType(Tree.MemberLiteral that, Declaration result) {
        ProducedType outerType;
        if (result.isClassOrInterfaceMember()) {
            outerType = that.getType()==null ? 
                    that.getScope().getDeclaringType(result) : 
                    that.getType().getTypeModel();            
        }
        else {
            outerType = null;
        }
        if (result instanceof Method) {
            Method method = (Method) result;
            if (method.isAbstraction()) {
                that.addError("method is overloaded");
            }
            else {
                Tree.TypeArgumentList tal = that.getTypeArgumentList();
                if (explicitTypeArguments(method, tal, null)) {
                    List<ProducedType> ta = getTypeArguments(tal, 
                    		getTypeParameters(method), outerType);
                    if (tal != null) {
                        tal.setTypeModels(ta);
                    }
                    if (acceptsTypeArguments(outerType, method, ta, tal, that, false)) {
                        ProducedTypedReference pr = outerType==null ? 
                                method.getProducedTypedReference(null, ta) : 
                                    outerType.getTypedMember(method, ta);
                                that.setTarget(pr);
                                that.setTypeModel(unit.getFunctionMetatype(pr));
                    }
                }
                else {
                    that.addError("missing type arguments to: " + method.getName(unit));
                }
            }
        }
        else if (result instanceof Value) {
            Value value = (Value) result;
            if (that.getTypeArgumentList() != null) {
                that.addError("does not accept type arguments: " + result.getName(unit));
            }
            else {
                ProducedTypedReference pr = value.getProducedTypedReference(outerType, 
                        Collections.<ProducedType>emptyList());
                that.setTarget(pr);
                that.setTypeModel(unit.getValueMetatype(pr));
            }
        }
    }

    private void checkNonlocal(Node that, Declaration declaration) {
        if ((!declaration.isClassOrInterfaceMember() || !declaration.isShared())
                    && !declaration.isToplevel()) {
            that.addError("metamodel reference to local declaration");
        }
    }
    
    /*private void checkNonlocalType(Node that, TypeDeclaration declaration) {
        if (declaration instanceof UnionType) {
            for (TypeDeclaration ctd: declaration.getCaseTypeDeclarations()) {
                checkNonlocalType(that, ctd);
            }
        }
        if (declaration instanceof IntersectionType) {
            for (TypeDeclaration std: declaration.getSatisfiedTypeDeclarations()) {
                checkNonlocalType(that, std);
            }
        }
        else if (declaration instanceof ClassOrInterface &&
                (!declaration.isClassOrInterfaceMember()||!declaration.isShared())
                        && !declaration.isToplevel()) {
            that.addWarning("metamodel reference to local type not yet supported");
        }
        else if (declaration.getContainer() instanceof TypeDeclaration) {
            checkNonlocalType(that, (TypeDeclaration) declaration.getContainer());
        }
    }*/
    
    private Declaration handleAbstraction(Declaration dec, Tree.MemberOrTypeExpression that) {
        //NOTE: if this is the qualifying type of a static method
        //      reference, don't do anything special here, since
        //      we're not actually calling the constructor
        if (!that.getStaticMethodReferencePrimary() &&
                isAbstraction(dec)) {
            //first handle the case where it's not _really_ overloaded,
            //it's just a constructor with a different visibility
        	List<Declaration> overloads = ((Functional) dec).getOverloads();
        	if (overloads.size()==1) {
        		return overloads.get(0);
        	}
        }
        return dec;
    }
    
}
