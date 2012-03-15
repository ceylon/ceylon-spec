package com.redhat.ceylon.compiler.typechecker.analyzer;

import static com.redhat.ceylon.compiler.typechecker.analyzer.Util.checkAssignable;
import static com.redhat.ceylon.compiler.typechecker.analyzer.Util.checkIsExactly;
import static com.redhat.ceylon.compiler.typechecker.analyzer.Util.checkTypeBelongsToContainingScope;
import static com.redhat.ceylon.compiler.typechecker.analyzer.Util.getBaseDeclaration;
import static com.redhat.ceylon.compiler.typechecker.analyzer.Util.getTypeArguments;
import static com.redhat.ceylon.compiler.typechecker.model.Util.addToIntersection;
import static com.redhat.ceylon.compiler.typechecker.model.Util.addToUnion;
import static com.redhat.ceylon.compiler.typechecker.model.Util.getContainingClassOrInterface;
import static com.redhat.ceylon.compiler.typechecker.model.Util.getOuterClassOrInterface;
import static com.redhat.ceylon.compiler.typechecker.model.Util.intersectionType;
import static com.redhat.ceylon.compiler.typechecker.model.Util.producedType;
import static com.redhat.ceylon.compiler.typechecker.model.Util.unionType;
import static com.redhat.ceylon.compiler.typechecker.tree.Util.name;
import static java.lang.Character.isUpperCase;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.redhat.ceylon.compiler.typechecker.model.BottomType;
import com.redhat.ceylon.compiler.typechecker.model.Class;
import com.redhat.ceylon.compiler.typechecker.model.ClassOrInterface;
import com.redhat.ceylon.compiler.typechecker.model.Declaration;
import com.redhat.ceylon.compiler.typechecker.model.Functional;
import com.redhat.ceylon.compiler.typechecker.model.Generic;
import com.redhat.ceylon.compiler.typechecker.model.Getter;
import com.redhat.ceylon.compiler.typechecker.model.Interface;
import com.redhat.ceylon.compiler.typechecker.model.IntersectionType;
import com.redhat.ceylon.compiler.typechecker.model.Parameter;
import com.redhat.ceylon.compiler.typechecker.model.ParameterList;
import com.redhat.ceylon.compiler.typechecker.model.ProducedReference;
import com.redhat.ceylon.compiler.typechecker.model.ProducedType;
import com.redhat.ceylon.compiler.typechecker.model.ProducedTypedReference;
import com.redhat.ceylon.compiler.typechecker.model.Scope;
import com.redhat.ceylon.compiler.typechecker.model.Setter;
import com.redhat.ceylon.compiler.typechecker.model.TypeDeclaration;
import com.redhat.ceylon.compiler.typechecker.model.TypeParameter;
import com.redhat.ceylon.compiler.typechecker.model.TypedDeclaration;
import com.redhat.ceylon.compiler.typechecker.model.UnionType;
import com.redhat.ceylon.compiler.typechecker.model.Unit;
import com.redhat.ceylon.compiler.typechecker.model.UnknownType;
import com.redhat.ceylon.compiler.typechecker.model.Value;
import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.CaseClause;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.CaseItem;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.Expression;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.SpecifiedArgument;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.SpecifierExpression;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.Type;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.TypedArgument;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.Variable;
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
    private Declaration returnDeclaration;
    private boolean defaultArgument;
    private boolean withinAnnotation = false;

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
            returnType.setTypeModel( unit.getBottomDeclaration().getType() );
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
    
    @Override public void visit(Tree.DefaultArgument that) {
        defaultArgument=true;
        super.visit(that);
        defaultArgument=false;
    }
    
    @Override public void visit(Tree.TypeDeclaration that) {
        super.visit(that);
        TypeDeclaration td = that.getDeclarationModel();
        if (td!=null) {
            List<ProducedType> supertypes = td.getType().getSupertypes();
            for (int i=0; i<supertypes.size(); i++) {
                ProducedType st1 = supertypes.get(i);
                for (int j=i+1; j<supertypes.size(); j++) {
                    ProducedType st2 = supertypes.get(j);
                    if (st1.getDeclaration().equals(st2.getDeclaration()) /*&& !st1.isExactly(st2)*/) {
                        if (!st1.isSubtypeOf(st2) && !st2.isSubtypeOf(st1)) {
                            that.addError("type " + td.getName() +
                                    " has the same supertype twice with incompatible type arguments: " +
                                    st1.getProducedTypeName() + " and " + st2.getProducedTypeName());
                        }
                    }
                }
                if (!isCompletelyVisible(td, st1)) {
                    that.addError("supertype of type is not visible everywhere type is visible: "
                            + st1.getProducedTypeName());
                }
            }
        }
    }
    
    @Override public void visit(Tree.TypedDeclaration that) {
        super.visit(that);
        TypedDeclaration td = that.getDeclarationModel();
        if ( td.getType()!=null && !isCompletelyVisible(td,td.getType()) ) {
            that.getType().addError("type of declaration is not visible everywhere declaration is visible: " 
                        + td.getName());
        }
    }
    
    @Override public void visit(Tree.FunctionArgument that) {
        super.visit(that);
        ProducedType t = that.getExpression().getTypeModel();
        that.getDeclarationModel().setType(t);
        that.getType().setTypeModel(t);
    }
    
    @Override
    public void visit(Tree.AnyMethod that) {
        super.visit(that);
        TypedDeclaration td = that.getDeclarationModel();
        for (Tree.ParameterList list: that.getParameterLists()) {
            for (Tree.Parameter tp: list.getParameters()) {
                Parameter p = tp.getDeclarationModel();
                if (p.getType()!=null && !isCompletelyVisible(td, p.getType())) {
                    tp.getType().addError("type of parameter is not visible everywhere declaration is visible: " 
                            + p.getName());
                }
            }
        }
    }
    
    @Override
    public void visit(Tree.AnyClass that) {
        super.visit(that);
        Class td = that.getDeclarationModel();
        if (that.getParameterList()!=null) {
            for (Tree.Parameter tp: that.getParameterList().getParameters()) {
                Parameter p = tp.getDeclarationModel();
                if (p.getType()!=null && !isCompletelyVisible(td, p.getType())) {
                    tp.getType().addError("type of parameter is not visible everywhere declaration is visible: " 
                            + p.getName());
                }
            }
        }
    }
    
    private boolean isCompletelyVisible(Declaration member, ProducedType pt) {
        if (pt.getDeclaration() instanceof UnionType) {
            for (ProducedType ct: pt.getDeclaration().getCaseTypes()) {
                if ( !isCompletelyVisible(member, ct.substitute(pt.getTypeArguments())) ) {
                    return false;
                }
            }
            return true;
        }
        else if (pt.getDeclaration() instanceof IntersectionType) {
            for (ProducedType ct: pt.getDeclaration().getSatisfiedTypes()) {
                if ( !isCompletelyVisible(member, ct.substitute(pt.getTypeArguments())) ) {
                    return false;
                }
            }
            return true;
        }
        else {
            if (!isVisible(member, pt.getDeclaration())) {
                return false;
            }
            for (ProducedType at: pt.getTypeArgumentList()) {
                if ( at!=null && !isCompletelyVisible(member, at) ) {
                    return false;
                }
            }
            return true;
        }
    }

    private boolean isVisible(Declaration member, TypeDeclaration type) {
        return type instanceof TypeParameter || 
                type.isVisible(member.getVisibleScope());
    }

    @Override public void visit(Tree.Variable that) {
        super.visit(that);
        if (that.getSpecifierExpression()!=null) {
            inferType(that, that.getSpecifierExpression());
            if (that.getType()!=null) {
                checkType(that.getType().getTypeModel(), that.getSpecifierExpression());
            }
        }
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
        that.getType().visit(this);
        Tree.Variable v = that.getVariable();
        ProducedType type = that.getType().getTypeModel();
        if (v!=null) {
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
                knownType = se.getExpression().getTypeModel();
                if (knownType!=null && knownType.isSubtypeOf(type)) {
                    that.addError("does not narrow type: " + knownType.getProducedTypeName() + 
                            " is a subtype of " + type.getProducedTypeName());
                }
            }
            defaultTypeToVoid(v);
            ProducedType it = knownType==null ? type : 
                    intersectionType(type, knownType, that.getUnit());
            if (it.getDeclaration() instanceof BottomType) {
                that.addError("tests assignability to Bottom type: intersection of " +
                        knownType.getProducedTypeName() + " and " + 
                        type.getProducedTypeName() +
                        " is empty");
            }
            if (v.getType() instanceof Tree.SyntheticVariable) {
                //when we're reusing the original name, we narrow to the
                //intersection of the outer type and the type specified
                //in the condition
                v.getType().setTypeModel(it);
                v.getDeclarationModel().setType(it);
            }
        }
        else if (that.getExpression()!=null) {
            that.getExpression().visit(this);
        }
        /*if (that.getExpression()!=null) {
            that.getExpression().visit(this);
        }*/
        checkReified(that, type);
    }

    private void checkReified(Node that, ProducedType type) {
        if (type!=null) {
            TypeDeclaration dec = type.getDeclaration();
            if (dec instanceof UnionType) {
                for (ProducedType pt: dec.getCaseTypes()) {
                    checkReified(that, pt);
                }
            }
            else if (dec instanceof IntersectionType) {
                for (ProducedType pt: dec.getSatisfiedTypes()) {
                    checkReified(that, pt);
                }
            }
            else if (dec instanceof TypeParameter) {
                that.addWarning("type parameter in assignability condition not yet supported (until we implement reified generics)");
            }
            else if (isGeneric(dec)) {
                List<TypeParameter> params = dec.getTypeParameters();
                List<ProducedType> args = type.getTypeArgumentList();
                if (params.size()==args.size()) {
                    for (int i=0; i<params.size(); i++) {
                        TypeParameter tp = params.get(i);
                        ProducedType ta = args.get(i);
                        if (ta!=null) {
                            if (tp.isCovariant()) {
                                List<ProducedType> list = new ArrayList<ProducedType>();
                                addToIntersection(list, unit.getVoidDeclaration().getType(), unit);
                                for (ProducedType st: tp.getSatisfiedTypes()) {
                                    addToIntersection(list, st, unit);
                                }
                                IntersectionType ut = new IntersectionType(unit);
                                ut.setSatisfiedTypes(list);
                                if (!ta.isExactly(ut.getType())) {
                                    that.addWarning("type argument to covariant type parameter in assignability condition must be " +
                                            ut.getType().getProducedTypeName() + " (until we implement reified generics)");
                                }
                            }
                            else if (tp.isContravariant()) {
                                if (!(ta.getDeclaration() instanceof BottomType)) {
                                    that.addWarning("type argument to contravariant type parameter in assignability condition must be Bottom (until we implement reified generics)");
                                }
                            }
                            else {
                                that.addWarning("type argument to invariant type parameter in assignability condition not yet supported (until we implement reified generics)");
                            }
                        }
                    }
                }
                //else there is another error, so don't bother
                //with this check at all
            }
        }
    }

    @Override public void visit(Tree.SatisfiesCondition that) {
        super.visit(that);
        that.addWarning("satisfies conditions not yet supported");
    }
    
    @Override public void visit(Tree.ExistsOrNonemptyCondition that) {
        //don't recurse to the Variable, since we don't
        //want to check that the specifier expression is
        //assignable to the declared variable type
        //(nor is it possible to infer the variable type)
        ProducedType t = null;
        Node n = that;
        Tree.Variable v = that.getVariable();
        if (v!=null) {
            //v.getType().visit(this);
            defaultTypeToVoid(v);
            Tree.SpecifierExpression se = v.getSpecifierExpression();
            if (se!=null) {
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
            }
        }
        else if (that.getExpression()!=null) {
            //note: this is only here to handle
            //      erroneous syntax elegantly
            that.getExpression().visit(this);
            t = that.getExpression().getTypeModel();
        }
        /*Tree.Expression e = that.getExpression();
        if (e!=null) {
            e.visit(this);
            t = e.getTypeModel();
            n = e;
        }*/
        if (that instanceof Tree.ExistsCondition) {
            checkOptional(t, n);
        }
        else if (that instanceof Tree.NonemptyCondition) {
            checkEmpty(t, n);
        }
    }

    private void defaultTypeToVoid(Tree.Variable v) {
        /*if (v.getType().getTypeModel()==null) {
            v.getType().setTypeModel( getVoidDeclaration().getType() );
        }*/
        v.getType().visit(this);
        if (v.getDeclarationModel().getType()==null) {
            v.getDeclarationModel().setType( defaultType() );
        }
    }

    private void checkReferenceIsNonVariable(Tree.Variable v,
            Tree.SpecifierExpression se) {
        if (v.getType() instanceof Tree.SyntheticVariable) {
            Tree.BaseMemberExpression ref = (Tree.BaseMemberExpression) se.getExpression().getTerm();
            if (ref.getDeclaration()!=null) {
                if ( ( (TypedDeclaration) ref.getDeclaration() ).isVariable() ) {
                    ref.addError("referenced value is variable");
                }
            }
        }
    }
    
    private void checkEmpty(ProducedType t, Node n) {
        if (t==null) {
            n.addError("expression must be a type with fixed size: type not known");
        }
        else if (!unit.isEmptyType(t)) {
            n.addError("expression must be a type with fixed size: " + 
                    t.getProducedTypeName() + " is not a supertype of FixedSized");
        }
    }

    private void checkOptional(ProducedType t, Node n) {
        if (t==null) {
            n.addError("expression must be of optional type: type not known");
        }
        else if (!unit.isOptionalType(t)) {
            n.addError("expression must be of optional type: " +
                    t.getProducedTypeName() + " is not a supertype of Nothing");
        }
    }

    @Override public void visit(Tree.BooleanCondition that) {
        super.visit(that);
        if (that.getExpression()!=null) {
            checkAssignable(that.getExpression().getTypeModel(), 
                    unit.getBooleanDeclaration().getType(), that, 
                    "expression must be of boolean type");
        }
    }

    @Override public void visit(Tree.Resource that) {
        super.visit(that);
        ProducedType t = null;
        Node typedNode = null;
        if (that.getExpression()!=null) {
            Tree.Expression e = that.getExpression();
            t = e.getTypeModel();
            typedNode = e;
            if (e.getTerm() instanceof Tree.InvocationExpression) {
                Tree.InvocationExpression ie = (Tree.InvocationExpression) e.getTerm();
                if (!(ie.getPrimary() instanceof Tree.BaseTypeExpression 
                        || ie.getPrimary() instanceof Tree.QualifiedTypeExpression)) {
                    e.addError("resource expression is not an unqualified value reference or instantiation");
                }
            }
            else if (!(e.getTerm() instanceof Tree.BaseMemberExpression)){
                e.addError("resource expression is not an unqualified value reference or instantiation");
            }
        }
        else if (that.getVariable()!=null) {
            t = that.getVariable().getType().getTypeModel();
            typedNode = that.getVariable().getType();
            Tree.SpecifierExpression se = that.getVariable().getSpecifierExpression();
            if (se==null) {
                that.getVariable().addError("missing resource specifier");
            }
            else if (typedNode instanceof Tree.ValueModifier){
                typedNode = se.getExpression();
            }
        }
        if (typedNode!=null) {
            checkAssignable(t, unit.getCloseableDeclaration().getType(), typedNode, 
                    "resource must be closeable");
        }
    }

    @Override public void visit(Tree.ValueIterator that) {
        super.visit(that);
        if (that.getVariable()!=null) {
            inferContainedType(that.getVariable(), that.getSpecifierExpression());
            checkContainedType(that.getVariable(), that.getSpecifierExpression());
        }
    }

    @Override public void visit(Tree.KeyValueIterator that) {
        super.visit(that);
        if (that.getKeyVariable()!=null && that.getValueVariable()!=null) {
            inferKeyType(that.getKeyVariable(), that.getSpecifierExpression());
            inferValueType(that.getValueVariable(), that.getSpecifierExpression());
            checkKeyValueType(that.getKeyVariable(), that.getValueVariable(), 
                    that.getSpecifierExpression());
        }
    }
    
    @Override public void visit(Tree.AttributeDeclaration that) {
        super.visit(that);
        inferType(that, that.getSpecifierOrInitializerExpression());
        if (that.getType()!=null) {
            checkType(that.getType().getTypeModel(), 
                    that.getSpecifierOrInitializerExpression());
        }
        validateHiddenAttribute(that);
    }

    private void validateHiddenAttribute(Tree.AnyAttribute that) {
        TypedDeclaration dec = that.getDeclarationModel();
        if (dec!=null && dec.isClassMember()) {
            Class c = (Class) dec.getContainer();
            Parameter param = (Parameter) c.getParameter( dec.getName() );
            if ( param!=null ) {
                //if it duplicates a parameter, then it must be is non-variable
                if ( dec.isVariable()) {
                    that.addError("member hidden by parameter may not be variable: " + 
                            dec.getName());
                }
                //if it duplicates a parameter, then it must have the same type
                //as the parameter
                checkIsExactly(dec.getType(), param.getType(), that, 
                        "member hidden by parameter must have same type as parameter " +
                        param.getName());
            }
        }
    }

    @Override public void visit(Tree.SpecifierStatement that) {
        super.visit(that);
        if (!(that.getBaseMemberExpression() instanceof Tree.BaseMemberExpression)) {
            that.getBaseMemberExpression().addError("illegal specification statement");
        }
        checkType(that.getBaseMemberExpression().getTypeModel(), 
                that.getSpecifierExpression());
    }

    @Override public void visit(Tree.Parameter that) {
        super.visit(that);
        SpecifierExpression se = that.getDefaultArgument()==null ?
                null :
                that.getDefaultArgument().getSpecifierExpression();
        Type tt = that.getType();
        if (tt!=null) {
            checkType(tt.getTypeModel(), se);
            //TODO: do we really need this check?!
            //      should we just remove it?
            if (se!=null && tt.getTypeModel()!=null) {
                if (unit.isOptionalType(tt.getTypeModel())) {
                    Tree.Term t = se.getExpression().getTerm();
                    if (t instanceof Tree.BaseMemberExpression) {
                        ProducedReference pr = ((Tree.BaseMemberExpression) t).getTarget();
                        if (pr==null || 
                                !pr.getDeclaration().equals(unit.getNullDeclaration())) {
                            se.getExpression()
                                    .addError("defaulted parameters of optional type must have the default value null");
                        }
                    }
                    else {
                        se.getExpression()
                                .addError("defaulted parameters of optional type must have the default value null");
                    }
                }
            }
        }
    }

    private void checkType(ProducedType declaredType, 
            Tree.SpecifierOrInitializerExpression sie) {
        if (sie!=null && sie.getExpression()!=null) {
            checkAssignable(sie.getExpression().getTypeModel(), declaredType, sie, 
                    "specified expression must be assignable to declared type");
        }
    }

    private void checkFunctionType(ProducedType declaredType, 
            Tree.SpecifierExpression sie, Tree.Type that) {
        if (sie!=null && sie.getExpression()!=null) {
            //TODO: validate that expression type really is Callable
            ProducedType rt = sie.getExpression().getTypeModel()
                    .getTypeArgumentList().get(0);
            checkAssignable(rt, declaredType, that, 
                    "specified reference return type must be assignable to declared return type");
        }
    }

    private void checkParameterType(int i, Parameter p, 
            Tree.SpecifierExpression sie, Tree.Type that) {
        if (sie!=null && sie.getExpression()!=null) {
            //TODO: validate that expression type really is Callable
            ProducedType rt = sie.getExpression().getTypeModel()
                    .getTypeArgumentList().get(i+1);
            checkIsExactly(rt, p.getType(), that, 
                    "specified reference parameter type must be exactly the same as declared type of parameter " + 
                    p.getName());
        }
    }

    private void checkOptionalType(Tree.Variable var, 
            Tree.SpecifierExpression se) {
        if (var.getType()!=null) {
            ProducedType vt = var.getType().getTypeModel();
            if (se!=null && se.getExpression()!=null) {
                ProducedType set = se.getExpression().getTypeModel();
                if (set!=null) {
                    checkAssignable(unit.getDefiniteType(set), vt, se, 
                            "specified expression must be assignable to declared type");
                }
            }
        }
    }

    private void checkEmptyOptionalType(Tree.Variable var, 
            Tree.SpecifierExpression se) {
        if (var.getType()!=null) {
            ProducedType vt = var.getType().getTypeModel();
            checkType(unit.getOptionalType(unit.getPossiblyNoneType(vt)), se);
        }
    }

    private void checkContainedType(Tree.Variable var, 
            Tree.SpecifierExpression se) {
        if (var.getType()!=null) {
            ProducedType vt = var.getType().getTypeModel();
            checkType(unit.getIterableType(vt), se);
        }
    }

    private void checkKeyValueType(Tree.Variable key, Tree.Variable value, 
            Tree.SpecifierExpression se) {
        if (key.getType()!=null && value.getType()!=null) {
            ProducedType kt = key.getType().getTypeModel();
            ProducedType vt = value.getType().getTypeModel();
            checkType(unit.getIterableType(unit.getEntryType(kt, vt)), se);
        }
    }

    @Override public void visit(Tree.AttributeGetterDefinition that) {
        Tree.Type rt = beginReturnScope(that.getType());
        Declaration od = beginReturnDeclaration(that.getDeclarationModel());
        super.visit(that);
        endReturnScope(rt, that.getDeclarationModel());
        endReturnDeclaration(od);
        validateHiddenAttribute(that);
        Setter setter = that.getDeclarationModel().getSetter();
        if (setter!=null) {
            setter.getParameter().setType(that.getDeclarationModel().getType());
        }
    }

    @Override public void visit(Tree.AttributeArgument that) {
        Tree.Type rt = beginReturnScope(that.getType());
        Declaration od = beginReturnDeclaration(that.getDeclarationModel());
        super.visit(that);
        endReturnDeclaration(od);
        endReturnScope(rt, that.getDeclarationModel());
    }

    @Override public void visit(Tree.AttributeSetterDefinition that) {
        Tree.Type rt = beginReturnScope(that.getType());
        Declaration od = beginReturnDeclaration(that.getDeclarationModel());
        super.visit(that);
        endReturnDeclaration(od);
        endReturnScope(rt, that.getDeclarationModel());
    }

    @Override public void visit(Tree.MethodDeclaration that) {
        super.visit(that);
        SpecifierExpression se = that.getSpecifierExpression();
        if (se!=null) {
            ProducedType et = se.getExpression().getTypeModel();
            //TODO: yew, fix this:
            if (et==null || !et.getDeclaration().getQualifiedNameString()
                    .equals("ceylon.language.Callable")) {
                se.addError("specified value must be a reference to a function or class: " + 
                        et.getDeclaration().getName() + " is not a subtype of Callable");
            }
            else {                    
                inferFunctionType(that, se);
                if (that.getType()!=null) {
                    checkFunctionType(that.getType().getTypeModel(), se, that.getType());
                }
                for (Tree.ParameterList pl: that.getParameterLists()) {
                    //TODO: support multiple parameter lists!
                    if (et!=null) {
                        if (pl.getParameters().size()+1==et.getTypeArgumentList().size()) {
                            int i=0;
                            for (Tree.Parameter p: pl.getParameters()) {
                                checkParameterType(i++, p.getDeclarationModel(), se, p.getType());
                            }
                        }
                        else {
                            se.addError("specified reference must have the same number of parameters");
                        }
                    }
                }
            }
        }
    }

    @Override public void visit(Tree.MethodDefinition that) {
        Tree.Type rt = beginReturnScope(that.getType());           
        Declaration od = beginReturnDeclaration(that.getDeclarationModel());
        super.visit(that);
        endReturnDeclaration(od);
        endReturnScope(rt, that.getDeclarationModel());
    }

    @Override public void visit(Tree.MethodArgument that) {
        Tree.Type rt = beginReturnScope(that.getType());           
        Declaration od = beginReturnDeclaration(that.getDeclarationModel());
        super.visit(that);
        endReturnDeclaration(od);
        endReturnScope(rt, that.getDeclarationModel());
    }

    @Override public void visit(Tree.ClassDefinition that) {
        Tree.Type rt = beginReturnScope(new Tree.VoidModifier(that.getToken()));
        Declaration od = beginReturnDeclaration(that.getDeclarationModel());
        super.visit(that);
        endReturnDeclaration(od);
        endReturnScope(rt, null);
        Class c = that.getDeclarationModel();
        if (!c.isAbstract()) {
            validateEnumeratedSupertypes(that, c);
        }
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
    }

    @Override public void visit(Tree.ObjectDefinition that) {
        Tree.Type rt = beginReturnScope(new Tree.VoidModifier(that.getToken()));
        Declaration od = beginReturnDeclaration(that.getDeclarationModel());
        super.visit(that);
        endReturnDeclaration(od);
        endReturnScope(rt, null);
        Class c = (Class) that.getDeclarationModel().getTypeDeclaration();
        validateEnumeratedSupertypes(that, c);
    }

    @Override public void visit(Tree.ObjectArgument that) {
        Tree.Type rt = beginReturnScope(new Tree.VoidModifier(that.getToken()));
        Declaration od = beginReturnDeclaration(that.getDeclarationModel());
        super.visit(that);
        endReturnDeclaration(od);
        endReturnScope(rt, null);
        Class c = (Class) that.getDeclarationModel().getTypeDeclaration();
        validateEnumeratedSupertypes(that, c);
    }

    @Override public void visit(Tree.ClassDeclaration that) {
        super.visit(that);
        Class alias = that.getDeclarationModel();
        Class c = alias.getExtendedTypeDeclaration();
        if (c!=null) {
            //that.getTypeSpecifier().getType().get
            ProducedType at = alias.getExtendedType();
            int cps = c.getParameterList().getParameters().size();
            int aps = alias.getParameterList().getParameters().size();
            if (cps!=aps) {
                that.addError("wrong number of initializer parameters declared by class alias: " + 
                        alias.getName());
            }
            for (int i=0; i<(cps<=aps ? cps : aps); i++) {
                Parameter ap = alias.getParameterList().getParameters().get(i);
                Parameter cp = c.getParameterList().getParameters().get(i);
                ProducedType pt = at.getTypedParameter(cp).getType();
                //TODO: properly check type of functional parameters!!
                checkAssignable(ap.getType(), pt, that, "alias parameter " + 
                        ap.getName() + " must be assignable to corresponding class parameter " +
                        cp.getName());
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
            else {
//                local.addError("could not infer type of: " + 
//                        name(that.getIdentifier()));
            }
        }
    }

    private void inferFunctionType(Tree.TypedDeclaration that, 
            Tree.SpecifierExpression spec) {
        if (that.getType() instanceof Tree.FunctionModifier) {
            Tree.FunctionModifier local = (Tree.FunctionModifier) that.getType();
            if (spec!=null) {
                setFunctionType(local, spec, that);
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
            else {
//                local.addError("could not infer type of: " + 
//                        name(that.getIdentifier()));
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
            else {
//                local.addError("could not infer type of: " + 
//                        name(that.getIdentifier()));
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
            else {
//                local.addError("could not infer type of: " + 
//                        name(that.getIdentifier()));
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
            else {
//                local.addError("could not infer type of key: " + 
//                        name(key.getIdentifier()));
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
            else {
//                local.addError("could not infer type of value: " + 
//                        name(value.getIdentifier()));
            }
        }
    }
    
    private void setTypeFromOptionalType(Tree.LocalModifier local, 
            Tree.SpecifierExpression se, Tree.Variable that) {
        ProducedType expressionType = se.getExpression().getTypeModel();
        if (expressionType!=null) {
            if (unit.isOptionalType(expressionType)) {
                ProducedType t = unit.getDefiniteType(expressionType);
                local.setTypeModel(t);
                that.getDeclarationModel().setType(t);
                return;
            }
        }
//        local.addError("could not infer type of: " + 
//                name(that.getIdentifier()));
    }
    
    private void setTypeFromEmptyType(Tree.LocalModifier local, 
            Tree.SpecifierExpression se, Tree.Variable that) {
        ProducedType expressionType = se.getExpression().getTypeModel();
        if (expressionType!=null) {
            if (unit.isEmptyType(expressionType)) {
                ProducedType t = unit.getNonemptyDefiniteType(expressionType);
                local.setTypeModel(t);
                that.getDeclarationModel().setType(t);
                return;
            }
        }
//        local.addError("could not infer type of: " + 
//                name(that.getIdentifier()));
    }
    
    private void setTypeFromIterableType(Tree.LocalModifier local, 
            Tree.SpecifierExpression se, Tree.Variable that) {
        if (se.getExpression()!=null) {
            ProducedType expressionType = se.getExpression().getTypeModel();
            if (expressionType!=null) {
                if (unit.isIterableType(expressionType)) {
                    ProducedType t = unit.getIteratedType(expressionType);
                    local.setTypeModel(t);
                    that.getDeclarationModel().setType(t);
                    return;
                }
            }
        }
//        local.addError("could not infer type of: " + 
//                name(that.getIdentifier()));
    }
    
    private void setTypeFromKeyType(Tree.LocalModifier local,
            Tree.SpecifierExpression se, Tree.Variable that) {
        if (se.getExpression()!=null) {
            ProducedType expressionType = se.getExpression().getTypeModel();
            if (expressionType!=null) {
                if (unit.isIterableType(expressionType)) {
                    ProducedType entryType = unit.getIteratedType(expressionType);
                    if (entryType!=null) {
                        if (unit.isEntryType(entryType)) {
                            ProducedType et = unit.getKeyType(entryType);
                            local.setTypeModel(et);
                            that.getDeclarationModel().setType(et);
                            return;
                        }
                    }
                }
            }
        }
//        local.addError("could not infer type of: " + 
//                name(that.getIdentifier()));
    }
    
    private void setTypeFromValueType(Tree.LocalModifier local,
            Tree.SpecifierExpression se, Tree.Variable that) {
        if (se.getExpression()!=null) {
            ProducedType expressionType = se.getExpression().getTypeModel();
            if (expressionType!=null) {
                if (unit.isIterableType(expressionType)) {
                    ProducedType entryType = unit.getIteratedType(expressionType);
                    if (entryType!=null) {
                        if (unit.isEntryType(entryType)) {
                            ProducedType et = unit.getValueType(entryType);
                            local.setTypeModel(et);
                            that.getDeclarationModel().setType(et);
                            return;
                        }
                    }
                }
            }
        }
//        local.addError("could not infer type of: " + 
//                name(that.getIdentifier()));
    }
    
    private void setType(Tree.LocalModifier local, 
            Tree.SpecifierOrInitializerExpression s, 
            Tree.TypedDeclaration that) {
        ProducedType t = s.getExpression().getTypeModel();
        local.setTypeModel(t);
        that.getDeclarationModel().setType(t);
    }
        
    private void setFunctionType(Tree.FunctionModifier local, 
            Tree.SpecifierExpression s, Tree.TypedDeclaration that) {
        ProducedType t = s.getExpression().getTypeModel();
        //TODO: validate that t is really Callable
        t = t.getTypeArgumentList().get(0);
        local.setTypeModel(t);
        that.getDeclarationModel().setType(t);
    }
        
    @Override public void visit(Tree.Throw that) {
        super.visit(that);
        if (that.getExpression()!=null) {
            checkAssignable(that.getExpression().getTypeModel(),
                    unit.getExceptionDeclaration().getType(), 
                    that.getExpression(),
                    "thrown expression must be an exception");
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
            if (e==null) {
                if (!(returnType instanceof Tree.VoidModifier)) {
                    that.addError("a non-void method or getter must return a value: " +
                            returnDeclaration.getName());
                }
            }
            else {
                ProducedType et = returnType.getTypeModel();
                ProducedType at = e.getTypeModel();
                if (returnType instanceof Tree.VoidModifier) {
                    that.addError("a void method, setter, or class initializer may not return a value: " +
                            returnDeclaration.getName());
                }
                else if (returnType instanceof Tree.LocalModifier) {
                    if (at!=null) {
                        if (et==null || et.isSubtypeOf(at)) {
                            returnType.setTypeModel(at);
                        }
                        else {
                            if (!at.isSubtypeOf(et)) {
                                UnionType ut = new UnionType(unit);
                                List<ProducedType> list = new ArrayList<ProducedType>();
                                addToUnion(list, et);
                                addToUnion(list, at);
                                ut.setCaseTypes(list);
                                returnType.setTypeModel( ut.getType() );
                            }
                        }
                    }
                }
                else {
                    checkAssignable(at, et, that.getExpression(), 
                            "returned expression must be assignable to return type of " +
                            returnDeclaration.getName());
                }
            }
        }
    }
    
    /*@Override public void visit(Tree.OuterExpression that) {
        that.getPrimary().visit(this);
        ProducedType pt = that.getPrimary().getTypeModel();
        if (pt!=null) {
            if (pt.getDeclaration() instanceof ClassOrInterface) {
                that.setTypeModel(getOuterType(that, (ClassOrInterface) pt.getDeclaration()));
                //TODO: some kind of MemberReference
            }
            else {
                that.addError("can't use outer on a type parameter");
            }
        }
    }*/

    ProducedType unwrap(ProducedType pt, Tree.QualifiedMemberOrTypeExpression mte) {
        ProducedType result;
        Tree.MemberOperator op = mte.getMemberOperator();
        if (op instanceof Tree.SafeMemberOp)  {
            if (unit.isOptionalType(pt)) {
                result = unit.getDefiniteType(pt);
            }
            else {
                mte.getPrimary().addError("receiver not of optional type");
                result = pt;
            }
        }
        else if (op instanceof Tree.SpreadOp) {
            ProducedType st = unit.getNonemptySequenceType(pt);
            if (st==null) {
                mte.getPrimary().addError("receiver not of type: Sequence");
                result = pt;
            }
            else {
                result = unit.getElementType(pt);
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
            ProducedType st = unit.getSequenceType(pt);            
            return unit.isEmptyType(receivingType) ?
                    unit.getEmptyType(st) : st;
        }
        else {
            return pt;
        }
    }
    
    @Override public void visit(Tree.InvocationExpression that) {
        
        //super.visit(that);
        List<ProducedType> sig = null;
        if (that.getPositionalArgumentList()!=null) {
            that.getPositionalArgumentList().visit(this);
            sig = new ArrayList<ProducedType>();
            for (Tree.PositionalArgument pa: that.getPositionalArgumentList().getPositionalArguments()) {
                sig.add(pa.getExpression().getTypeModel());
            }
            if (that.getPrimary() instanceof Tree.MemberOrTypeExpression) {
                ((Tree.MemberOrTypeExpression) that.getPrimary()).setSignature(sig);
            }
        }
        if (that.getNamedArgumentList()!=null) {
            that.getNamedArgumentList().visit(this);
        }
        that.getPrimary().visit(this);
        
        Tree.Primary pr = that.getPrimary();
        if (pr==null) {
            that.addError("malformed invocation expression");
        }
        else if (pr instanceof Tree.StaticMemberOrTypeExpression) {
            Tree.StaticMemberOrTypeExpression mte = (Tree.StaticMemberOrTypeExpression) pr;
            Declaration dec = mte.getDeclaration();
            if ( mte.getTarget()==null && dec instanceof Functional && 
                    mte.getTypeArguments() instanceof Tree.InferredTypeArguments ) {
                List<ProducedType> typeArgs = getInferedTypeArguments(that, (Functional) dec);
                mte.getTypeArguments().setTypeModels(typeArgs);
                if (pr instanceof Tree.BaseTypeExpression) {
                    visitBaseTypeExpression((Tree.BaseTypeExpression) pr, 
                            (TypeDeclaration) dec, typeArgs, mte.getTypeArguments());
                }
                else if (pr instanceof Tree.QualifiedTypeExpression) {
                    visitQualifiedTypeExpression((Tree.QualifiedTypeExpression) pr, 
                            ((Tree.QualifiedTypeExpression) pr).getPrimary().getTypeModel(),
                            (TypeDeclaration) dec, typeArgs, mte.getTypeArguments());
                }
                else if (pr instanceof Tree.BaseMemberExpression) {
                    visitBaseMemberExpression((Tree.BaseMemberExpression) pr, 
                            (TypedDeclaration) dec, typeArgs, mte.getTypeArguments());
                }
                else if (pr instanceof Tree.QualifiedMemberExpression) {
                    visitQualifiedMemberExpression((Tree.QualifiedMemberExpression) pr, 
                            (TypedDeclaration) dec, typeArgs, mte.getTypeArguments());
                }
            }
            visitInvocation(that, mte.getTarget());
        }
        else if (pr instanceof Tree.ExtendedTypeExpression) {
            visitInvocation(that, ((Tree.ExtendedTypeExpression) pr).getTarget());
        }
        else {
            //TODO: fix this
            that.addWarning("direct invocation of Callable objects not yet supported");
        }
    }

    private List<ProducedType> getInferedTypeArguments(Tree.InvocationExpression that, 
            Functional dec) {
        List<ProducedType> typeArgs = new ArrayList<ProducedType>();
        ParameterList parameters = dec.getParameterLists().get(0);
        for (TypeParameter tp: dec.getTypeParameters()) {
            if (!tp.isSequenced()) {
                ProducedType ta = inferTypeArgument(that, tp, parameters);
                List<ProducedType> list = new ArrayList<ProducedType>();
                addToIntersection(list, ta, unit);
                for (ProducedType st: tp.getSatisfiedTypes()) {
                    //TODO: st.getProducedType(receiver, dec, typeArgs);
                    if (//if the upper bound is a type parameter, ignore it
                        !dec.getTypeParameters().contains(st.getDeclaration()) &&
                        (st.getQualifyingType()==null ||
                        !dec.getTypeParameters().contains(st.getQualifyingType().getDeclaration())) &&
                        //TODO: remove this awful hack that 
                        //tries to work around the possibility 
                        //that a type parameter appears in the 
                        //upper bound!
                        !st.getDeclaration().isParameterized() &&
                        (st.getQualifyingType()==null || 
                        !st.getQualifyingType().getDeclaration().isParameterized())) {
                        addToIntersection(list, st, unit);
                    }
                }
                IntersectionType it = new IntersectionType(unit);
                it.setSatisfiedTypes(list);
                typeArgs.add(it.canonicalize().getType());
            }
        }
        return typeArgs;
    }

    private ProducedType inferTypeArgument(Tree.InvocationExpression that,
            TypeParameter tp, ParameterList parameters) {
        List<ProducedType> inferredTypes = new ArrayList<ProducedType>();
        if (that.getPositionalArgumentList()!=null) {
            inferTypeArgument(tp, parameters, that.getPositionalArgumentList(), inferredTypes);
        }
        else if (that.getNamedArgumentList()!=null) {
            inferTypeArgument(tp, parameters, that.getNamedArgumentList(), inferredTypes);
        }
        UnionType ut = new UnionType(unit);
        ut.setCaseTypes(inferredTypes);
        return ut.getType();
    }

    private void inferTypeArgument(TypeParameter tp, ParameterList parameters,
            Tree.NamedArgumentList args, List<ProducedType> inferredTypes) {
        for (Tree.NamedArgument arg: args.getNamedArguments()) {
            ProducedType type = null;
            if (arg instanceof Tree.SpecifiedArgument) {
                type = ((Tree.SpecifiedArgument) arg).getSpecifierExpression()
                                .getExpression().getTypeModel();
            }
            else if (arg instanceof Tree.TypedArgument) {
                //TODO: broken for method args
                type = ((Tree.TypedArgument) arg).getType().getTypeModel();
            }
            if (type!=null) {
                Parameter parameter = getMatchingParameter(parameters, arg);
                if (parameter!=null) {
                    addToUnion(inferredTypes, inferTypeArg(tp, parameter.getType(), 
                            type, new ArrayList<TypeParameter>()));
                }
            }
        }
        Tree.SequencedArgument sa = args.getSequencedArgument();
        if (sa!=null) {
            Parameter sp = getSequencedParameter(parameters);
            if (sp!=null) {
                ProducedType spt = unit.getElementType(sp.getType());
                for (Tree.Expression e: args.getSequencedArgument()
                                .getExpressionList().getExpressions()) {
                    ProducedType sat = e.getTypeModel();
                    if (sat!=null) {
                        addToUnion(inferredTypes, inferTypeArg(tp, spt, sat,
                                new ArrayList<TypeParameter>()));
                    }
                }
            }
        }            
    }

    private void inferTypeArgument(TypeParameter tp, ParameterList parameters,
            Tree.PositionalArgumentList args, List<ProducedType> inferredTypes) {
        for (int i=0; i<parameters.getParameters().size(); i++) {
            Parameter parameter = parameters.getParameters().get(i);
            if (args.getPositionalArguments().size()>i) {
                if (parameter.isSequenced() && args.getEllipsis()==null) {
                    ProducedType spt = unit.getElementType(parameter.getType());
                    for (int k=i; k<args.getPositionalArguments().size(); k++) {
                        ProducedType sat = args.getPositionalArguments().get(k)
                                .getExpression().getTypeModel();
                        if (sat!=null) {
                            addToUnion(inferredTypes, inferTypeArg(tp, spt, sat,
                                    new ArrayList<TypeParameter>()));
                        }
                    }
                    break;
                }
                else {
                    ProducedType argType = args.getPositionalArguments().get(i)
                            .getExpression().getTypeModel();
                    if (argType!=null) {
                        addToUnion(inferredTypes, inferTypeArg(tp, parameter.getType(), 
                                argType, new ArrayList<TypeParameter>()));
                    }
                }
            }
        }
    }
    
    private ProducedType union(List<ProducedType> types) {
        if (types.isEmpty()) {
            return null;
        }
        UnionType ut = new UnionType(unit);
        ut.setCaseTypes(types);
        return ut.getType();
    }
    
    private ProducedType intersection(List<ProducedType> types) {
        if (types.isEmpty()) {
            return null;
        }
        IntersectionType it = new IntersectionType(unit);
        it.setSatisfiedTypes(types);
        return it.canonicalize().getType();
    }
    
    private ProducedType inferTypeArg(TypeParameter tp, ProducedType paramType,
            ProducedType argType, List<TypeParameter> visited) {
        if (paramType!=null) {
            if (paramType.getDeclaration().equals(tp)) {
                return argType;
            }
            else if (paramType.getDeclaration() instanceof UnionType) {
                List<ProducedType> list = new ArrayList<ProducedType>();
                for (ProducedType ct: paramType.getDeclaration().getCaseTypes()) {
                    addToIntersection(list, inferTypeArg(tp, 
                            ct.substitute(paramType.getTypeArguments()), 
                            argType, visited), unit);
                }
                return intersection(list);
            }
            else if (paramType.getDeclaration() instanceof IntersectionType) {
                List<ProducedType> list = new ArrayList<ProducedType>();
                for (ProducedType ct: paramType.getDeclaration().getSatisfiedTypes()) {
                    addToUnion(list, inferTypeArg(tp, 
                            ct.substitute(paramType.getTypeArguments()), 
                            argType, visited));
                }
                return union(list);
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
            else if (paramType.getDeclaration() instanceof TypeParameter) {
                TypeParameter tp2 = (TypeParameter) paramType.getDeclaration();
                if (!visited.contains(tp2)) {
                    visited.add(tp2);
                    List<ProducedType> list = new ArrayList<ProducedType>();
                    for (ProducedType pt: tp2.getSatisfiedTypes()) {
                        addToUnion(list, inferTypeArg(tp, pt, argType, visited) );
                        ProducedType st = argType.getSupertype(pt.getDeclaration());
                        if (st!=null) {
                            for (int j=0; j<pt.getTypeArgumentList().size(); j++) {
                                if (st.getTypeArgumentList().size()>j) {
                                    addToUnion(list, inferTypeArg(tp, 
                                            pt.getTypeArgumentList().get(j), 
                                            st.getTypeArgumentList().get(j), 
                                            visited));
                                }
                            }
                        }
                    }
                    return union(list);
                }
                else {
                    return null;
                }
            }
            else {
                ProducedType st = argType.getSupertype(paramType.getDeclaration());
                if (st!=null) {
                    List<ProducedType> list = new ArrayList<ProducedType>();
                    for (int j=0; j<paramType.getTypeArgumentList().size(); j++) {
                        if (st.getTypeArgumentList().size()>j) {
                            addToUnion(list, inferTypeArg(tp, 
                                    paramType.getTypeArgumentList().get(j), 
                                    st.getTypeArgumentList().get(j), 
                                    visited));
                        }
                    }
                    return union(list);
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

    /*@Override public void visit(Tree.ExtendedType that) {
        super.visit(that);
        Tree.Primary pr = that.getTypeExpression();
        Tree.PositionalArgumentList pal = that.getPositionalArgumentList();
        if (pr==null || pal==null) {
            that.addError("malformed expression");
        }
        else {
            visitInvocation(pal, null, that, pr);
        }
    }*/

    private void visitInvocation(Tree.InvocationExpression that, ProducedReference prf) {
        if (prf==null) {
            //that.addError("could not determine if receiving expression can be invoked");
        }
        else if (!prf.isFunctional()) {
            //TODO: this is temporary and should be removed, once we
            //      can typecheck parameters using the type args of
            //      Callable
            that.addError("receiving expression cannot be invoked: " + 
                    prf.getDeclaration().getName() + " is not a method or class");
        }
        else {
            Tree.MemberOrTypeExpression mte = (Tree.MemberOrTypeExpression) that.getPrimary();
            Functional dec = (Functional) mte.getDeclaration();
            if (!(that.getPrimary() instanceof Tree.ExtendedTypeExpression)) {
                if (dec instanceof Class && ((Class) dec).isAbstract()) {
                    that.addError("abstract classes may not be instantiated");
                }
            }
            //that.setTypeModel(prf.getType());
            ProducedType ct = that.getPrimary().getTypeModel();
            if (ct!=null && !ct.getTypeArgumentList().isEmpty()) {
                //pull the return type out of the Callable
                that.setTypeModel(ct.getTypeArgumentList().get(0));
            }
            if(that.getNamedArgumentList() != null){
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
                checkInvocationArguments(that, prf, dec);
            }
        }
    }

    private void checkInvocationArguments(Tree.InvocationExpression that,
            ProducedReference prf, Functional dec) {
        //TODO: typecheck parameters using the type args of Callable
        List<ParameterList> pls = dec.getParameterLists();
        if (pls.isEmpty()) {
            if (dec instanceof TypeDeclaration) {
                that.addError("type cannot be instantiated: " + 
                        dec.getName() + " (or return statement is missing)");
            }
            else {
                that.addError("member cannot be invoked: " +
                        dec.getName());
            }
        }
        else /*if (!dec.isOverloaded())*/ {
            ParameterList pl = pls.get(0);            
            if ( that.getPositionalArgumentList()!=null ) {
                checkPositionalArguments(pl, prf, that.getPositionalArgumentList());
            }
            if ( that.getNamedArgumentList()!=null ) {
                if(pl.isNamedParametersSupported()) {
                    that.getNamedArgumentList().getNamedArgumentList().setParameterList(pl);
                    checkNamedArguments(pl, prf, that.getNamedArgumentList());
                }
            }
        }
    }

    private void checkNamedArguments(ParameterList pl, ProducedReference pr, 
            Tree.NamedArgumentList nal) {
        List<Tree.NamedArgument> na = nal.getNamedArguments();        
        Set<Parameter> foundParameters = new HashSet<Parameter>();
        
        for (Tree.NamedArgument a: na) {
            Parameter p = getMatchingParameter(pl, a);
            if (p==null) {
                a.addError("no matching parameter for named argument " + 
                        name(a.getIdentifier()) + " declared by " + 
                        pr.getDeclaration().getName(), 101);
            }
            else {
                if (!foundParameters.add(p)) {
                    a.addError("duplicate argument for parameter: " +
                            p.getName());
                }
                checkNamedArgument(a, pr, p);
            }
        }
        
        Tree.SequencedArgument sa = nal.getSequencedArgument();
        if (sa!=null) {
            Parameter sp = getSequencedParameter(pl);
            if (sp==null) {
                sa.addError("no matching sequenced parameter declared by "
                         + pr.getDeclaration().getName());
            }
            else {
                if (!foundParameters.add(sp)) {
                    sa.addError("duplicate argument for parameter: " +
                            sp.getName());
                }
                checkSequencedArgument(sa, pr, sp);
            }
        }
            
        for (Parameter p: pl.getParameters()) {
            if (!foundParameters.contains(p) && 
                    !p.isDefaulted() && !p.isSequenced()) {
                nal.addError("missing named argument to parameter " + 
                        p.getName() + " of " + pr.getDeclaration().getName());
            }
        }
    }

    private void checkNamedArgument(Tree.NamedArgument a, ProducedReference pr, 
            Parameter p) {
        a.setParameter(p);
        ProducedType argType = null;
        if (a instanceof Tree.SpecifiedArgument) {
            SpecifiedArgument sa = (Tree.SpecifiedArgument) a;
            argType = sa.getSpecifierExpression().getExpression().getTypeModel();
        }
        else if (a instanceof Tree.TypedArgument) {
            TypedArgument ta = (Tree.TypedArgument) a;
            argType = ta.getDeclarationModel().getProducedReference(null,
                     //assuming an argument can't have type params 
                    Collections.<ProducedType>emptyList()).getFullType();
            //argType = ta.getType().getTypeModel();
        }
        checkAssignable(argType, pr.getTypedParameter(p).getFullType(), a,
                "named argument must be assignable to parameter " + 
                p.getName() + " of " + pr.getDeclaration().getName());
    }
    
    private void checkSequencedArgument(Tree.SequencedArgument a, ProducedReference pr, 
            Parameter p) {
        a.setParameter(p);
        for (Tree.Expression e: a.getExpressionList().getExpressions()) {
            ProducedType paramType = pr.getTypedParameter(p).getFullType();
            if (paramType==null) {
                paramType = new UnknownType(a.getUnit()).getType();
            }
            checkAssignable(e.getTypeModel(), unit.getElementType(paramType), a, 
                    "sequenced argument must be assignable to sequenced parameter " + 
                    p.getName() + " of " + pr.getDeclaration().getName());
        }
    }
    
    private Parameter getMatchingParameter(ParameterList pl, Tree.NamedArgument na) {
        String name = name(na.getIdentifier());
        for (Parameter p: pl.getParameters()) {
            if (p.getName().equals(name)) {
                return p;
            }
        }
        return null;
    }

    private Parameter getSequencedParameter(ParameterList pl) {
        int s = pl.getParameters().size();
        if (s==0) return null;
        Parameter p = pl.getParameters().get(s-1);
        if (p.isSequenced()) {
            return p;
        }
        else {
            return null;
        }
    }

    private void checkPositionalArguments(ParameterList pl, ProducedReference pr, 
            Tree.PositionalArgumentList pal) {
        List<Tree.PositionalArgument> args = pal.getPositionalArguments();
        List<Parameter> params = pl.getParameters();
        for (int i=0; i<params.size(); i++) {
            Parameter p = params.get(i);
            if (i>=args.size()) {
                if (!p.isDefaulted() && !p.isSequenced()) {
                    pal.addError("missing argument to parameter " + 
                            p.getName() + " of " + pr.getDeclaration().getName());
                }
                if (p.isSequenced() && pal.getEllipsis()!=null) {
                    pal.addError("missing argument to sequenced parameter " + 
                            p.getName() + " of " + pr.getDeclaration().getName());
                }
            } 
            else {
                ProducedType paramType = pr.getTypedParameter(p).getFullType();
                if (p.isSequenced() && pal.getEllipsis()==null) {
                    checkSequencedPositionalArgument(p, pr, pal, i, paramType);
                    return;
                }
                else {
                    checkPositionalArgument(p, pr, args.get(i), paramType);
                }
            }
        }
        for (int i=params.size(); i<args.size(); i++) {
            args.get(i).addError("no matching parameter for argument declared by " +
                     pr.getDeclaration().getName());
        }
        if (pal.getEllipsis()!=null && 
                (params.isEmpty() || !params.get(params.size()-1).isSequenced())) {
            pal.getEllipsis().addError("no matching sequenced parameter declared by " +
                     pr.getDeclaration().getName());
        }
    }

    private void checkSequencedPositionalArgument(Parameter p, ProducedReference pr,
            Tree.PositionalArgumentList pal, int i, ProducedType paramType) {
        List<Tree.PositionalArgument> args = pal.getPositionalArguments();
        ProducedType at = paramType==null ? null : 
                unit.getElementType(paramType);
        for (int j=i; j<args.size(); j++) {
            Tree.PositionalArgument a = args.get(j);
            a.setParameter(p);
            Tree.Expression e = a.getExpression();
            if (e==null) {
                //TODO: this case is temporary until we get support for SPECIAL_ARGUMENTs
            }
            else {
                /*if (pal.getEllipsis()!=null) {
                    if (i<args.size()-1) {
                        a.addError("too many arguments to sequenced parameter: " + 
                                p.getName());
                    }
                    if (!paramType.isSupertypeOf(argType)) {
                        a.addError("argument not assignable to parameter type: " + 
                                p.getName() + " since " +
                                argType.getProducedTypeName() + " is not " +
                                paramType.getProducedTypeName());
                    }
                }
                else {*/
                    checkAssignable(e.getTypeModel(), at, a, 
                            "argument must be assignable to sequenced parameter " + 
                            p.getName()+ " of " + pr.getDeclaration().getName());
                //}
            }
        }
    }

    private void checkPositionalArgument(Parameter p, ProducedReference pr,
            Tree.PositionalArgument a, ProducedType paramType) {
        a.setParameter(p);
        Tree.Expression e = a.getExpression();
        if (e==null) {
            //TODO: this case is temporary until we get support for SPECIAL_ARGUMENTs
        }
        else {
            ProducedType et;
            if (a instanceof Tree.FunctionArgument) {
                et = ((Tree.FunctionArgument) a).getDeclarationModel()
                        .getProducedTypedReference(null, Collections.<ProducedType>emptyList())
                        .getFullType();
            }
            else {
                et = e.getTypeModel();
            }
            checkAssignable(et, paramType, a, 
                    "argument must be assignable to parameter " + 
                    p.getName() + " of " + pr.getDeclaration().getName());
        }
    }
    
    @Override public void visit(Tree.Annotation that) {
        withinAnnotation=true;
        super.visit(that);
        withinAnnotation=false;
        Declaration dec = ((Tree.MemberOrTypeExpression) that.getPrimary()).getDeclaration();
        if (dec!=null && !dec.isToplevel()) {
            that.getPrimary().addError("annotation must be a toplevel method reference");
        }
    }
    
    @Override public void visit(Tree.IndexExpression that) {
        super.visit(that);
        ProducedType pt = type(that);
        if (pt==null) {
            that.addError("could not determine type of receiver");
        }
        else {
            if (that.getIndexOperator() instanceof Tree.SafeIndexOp) {
                if (unit.isOptionalType(pt)) {
                    pt = unit.getDefiniteType(pt);
                }
                else {
                    that.getPrimary().addError("receiving type not of optional type: " +
                            pt.getDeclaration().getName() + " is not a subtype of Optional");
                }
            }
            if (that.getElementOrRange()==null) {
                that.addError("malformed index expression");
            }
            else {
                if (that.getElementOrRange() instanceof Tree.Element) {
                    ProducedType cst = pt.getSupertype(unit.getCorrespondenceDeclaration());
                    if (cst==null) {
                        that.getPrimary().addError("illegal receiving type for index expression: " +
                                pt.getDeclaration().getName() + " is not a subtype of Correspondence");
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
                    }
                }
                else {
                    ProducedType rst = pt.getSupertype(unit.getRangedDeclaration());
                    if (rst==null) {
                        that.getPrimary().addError("illegal receiving type for index range expression: " +
                                pt.getDeclaration().getName() + " is not a subtype of Ranged");
                    }
                    else {
                        List<ProducedType> args = rst.getTypeArgumentList();
                        ProducedType kt = args.get(0);
                        ProducedType rt = args.get(1);
                        Tree.ElementRange er = (Tree.ElementRange) that.getElementOrRange();
                        checkAssignable(er.getLowerBound().getTypeModel(), kt,
                                er.getLowerBound(), 
                                "lower bound must be assignable to index type");
                        if (er.getUpperBound()!=null) {
                            checkAssignable(er.getUpperBound().getTypeModel(), kt,
                                    er.getUpperBound(), 
                                    "upper bound must be assignable to index type");
                        }
                        that.setTypeModel(rt);
                    }
                }
            }
        }
    }

    private ProducedType type(Tree.PostfixExpression that) {
        Tree.Primary p = that.getPrimary();
        return p==null ? null : p.getTypeModel();
    }
    
    @Override public void visit(Tree.PostfixOperatorExpression that) {
        super.visit(that);
        visitIncrementDecrement(that, type(that), that.getTerm());
        checkAssignability(that.getTerm(), that);
    }

    @Override public void visit(Tree.PrefixOperatorExpression that) {
        super.visit(that);
        visitIncrementDecrement(that, type(that), that.getTerm());
        checkAssignability(that.getTerm(), that);
    }
    
    private ProducedType checkOperandType(ProducedType pt, TypeDeclaration td, 
            Node node, String message) {
        ProducedType supertype = pt.getSupertype(td);
        if (supertype==null) {
            node.addError(message + ": " + pt.getDeclaration().getName() +
                    " is not a subtype of " + td.getName());
        }
        return supertype;
    }

    private void checkOperandType(ProducedType pt, ProducedType operand, 
            Node node, String message) {
        if (!pt.isSupertypeOf(operand)) {
            node.addError(message + ": " + operand.getProducedTypeName() +
                    " is not a subtype of " + pt.getProducedTypeName());
        }
    }

    private ProducedType checkOperandTypes(ProducedType lhst, ProducedType rhst, 
            TypeDeclaration td, Node node, String message) {
        ProducedType ut = unionType(lhst, rhst, unit);
        ProducedType st = producedType(td, ut);
        checkOperandType(st, lhst, node, message);
        checkOperandType(st, rhst, node, message);
        return ut.getSupertype(td);
    }

    private void visitIncrementDecrement(Tree.Term that,
            ProducedType pt, Tree.Term term) {
        if (pt!=null) {
            ProducedType ot = checkOperandType(pt, unit.getOrdinalDeclaration(), 
                    term, "operand expression must be of ordinal type");
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

    private void visitComparisonOperator(Tree.BinaryOperatorExpression that, 
            TypeDeclaration type) {
        ProducedType lhst = leftType(that);
        ProducedType rhst = rightType(that);
        if ( rhst!=null && lhst!=null ) {
            checkOperandTypes(lhst, rhst, type, that, 
                    "operand expressions must be comparable");
        }
        that.setTypeModel( unit.getBooleanDeclaration().getType() );            
    }
    
    private void visitCompareOperator(Tree.CompareOp that) {
        ProducedType lhst = leftType(that);
        ProducedType rhst = rightType(that);
        if ( rhst!=null && lhst!=null ) {
            checkOperandTypes(lhst, rhst, unit.getComparableDeclaration(), that, 
                    "operand expressions must be comparable");
        }
        that.setTypeModel( unit.getComparisonDeclaration().getType() );            
    }
    
    private void visitRangeOperator(Tree.RangeOp that) {
        ProducedType lhst = leftType(that);
        ProducedType rhst = rightType(that);
        if ( rhst!=null && lhst!=null ) {
            checkOperandTypes(lhst, rhst, unit.getOrdinalDeclaration(), that,
                    "operand expressions must be of compatible ordinal type");
            ProducedType ct = checkOperandTypes(lhst, rhst, 
                    unit.getComparableDeclaration(), that, 
                    "operand expressions must be comparable");
            if (ct!=null) {
                ProducedType pt = producedType(unit.getRangeDeclaration(), 
                        ct.getTypeArgumentList().get(0));
                that.setTypeModel(pt);
            }
        }
    }
    
    private void visitEntryOperator(Tree.EntryOp that) {
        ProducedType lhst = leftType(that);
        ProducedType rhst = rightType(that);
        if ( rhst!=null && lhst!=null ) {
            checkOperandType(lhst, unit.getObjectDeclaration(), that.getLeftTerm(), 
                    "operand expression must support equality");
            checkOperandType(rhst, unit.getObjectDeclaration(), that.getRightTerm(), 
                    "operand expression must support equality");
            ProducedType et = unit.getEntryType(lhst, rhst);
            that.setTypeModel(et);
        }
    }
    
    private void visitArithmeticOperator(Tree.BinaryOperatorExpression that, 
            TypeDeclaration type) {
        ProducedType lhst = leftType(that);
        ProducedType rhst = rightType(that);
        if ( rhst!=null && lhst!=null ) {
            ProducedType lhsst = checkOperandType(lhst, type, that.getLeftTerm(), 
                    "operand expression must be of numeric type");
            ProducedType rhsst = checkOperandType(rhst, type, that.getRightTerm(), 
                    "operand expression must be of numeric type");
            if (rhsst!=null && lhsst!=null) {
                rhst = rhsst.getTypeArgumentList().get(0);
                lhst = lhsst.getTypeArgumentList().get(0);
                //find the common type to which arguments
                //can be widened, according to Castable
                ProducedType rt;
                ProducedType st;
                if (lhst.isSubtypeOf(unit.getCastableType(lhst)) && 
                        rhst.isSubtypeOf(unit.getCastableType(lhst))) {
                    //the lhs has a wider type
                    rt = lhst;
                    st = lhsst;
                }
                else if (lhst.isSubtypeOf(unit.getCastableType(rhst)) && 
                        rhst.isSubtypeOf(unit.getCastableType(rhst))) {
                    //the rhs has a wider type
                    rt = rhst;
                    st = rhsst;
                }
                else if (lhst.isExactly(rhst)) { 
                    //in case the args don't implement Castable at all, but
                    //they are exactly the same type, so no promotion is 
                    //necessary - note the language spec does not actually 
                    //bless this at present
                    rt = lhst;
                    st = lhsst;
                }
                else {
                    that.addError("operand expressions must be promotable to common numeric type: " + 
                            lhst.getProducedTypeName() + " and " + 
                            rhst.getProducedTypeName());
                    return;
                }
                checkAssignable(rt, producedType(type, rt), that, 
                        "operands must be of compatible numeric type");
                that.setTypeModel(rt);
            }
        }
    }

    private void visitArithmeticAssignOperator(Tree.BinaryOperatorExpression that, 
            TypeDeclaration type) {
        ProducedType lhst = leftType(that);
        ProducedType rhst = rightType(that);
        if ( rhst!=null && lhst!=null ) {
            ProducedType nt = checkOperandType(lhst, type, that.getLeftTerm(), 
                    "operand expression must be of numeric type");
            that.setTypeModel(lhst);
            if (nt!=null) {
                ProducedType t = nt.getTypeArgumentList().get(0);
                //that.setTypeModel(t); //stef requests lhst to make it easier on backend
                if (!rhst.isSubtypeOf(unit.getCastableType(t)) &&
                        !rhst.isExactly(lhst)) { //note the language spec does not actually bless this
                    that.getRightTerm().addError("operand expression must be promotable to declared type: " + 
                            rhst.getProducedTypeName() + " is not promotable to " +
                            nt.getProducedTypeName());
                }
                checkAssignable(t, lhst, that, 
                        "result type must be assignable to declared type");
            }
        }
    }

    private void visitSetOperator(Tree.BitwiseOp that) {
        //TypeDeclaration sd = unit.getSetDeclaration();
        ProducedType lhst = leftType(that);
        ProducedType rhst = rightType(that);
        if ( rhst!=null && lhst!=null ) {
            checkAssignable(lhst, unit.getSetType(unit.getObjectDeclaration().getType()), 
                    that.getLeftTerm(), "set operand expression must be a set");
            checkAssignable(lhst, unit.getSetType(unit.getObjectDeclaration().getType()), 
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
        if ( rhst!=null && lhst!=null ) {
            checkAssignable(lhst, unit.getSetType(unit.getObjectDeclaration().getType()), 
                    that.getLeftTerm(), "set operand expression must be a set");
            checkAssignable(lhst, unit.getSetType(unit.getObjectDeclaration().getType()), 
                    that.getRightTerm(), "set operand expression must be a set");
            ProducedType lhset = unit.getSetElementType(lhst);
            ProducedType rhset = unit.getSetElementType(rhst);
            if (that instanceof Tree.UnionAssignOp ||
                    that instanceof Tree.XorAssignOp) {
                checkAssignable(rhset, lhset, that.getRightTerm(), 
                        "resulting set element type must be assignable to to declared set element type");
            }            
            that.setTypeModel(unit.getSetType(lhst)); //in theory, we could make this narrower
        }
    }

    private void visitLogicalOperator(Tree.BinaryOperatorExpression that) {
        ProducedType bt = unit.getBooleanDeclaration().getType();
        checkAssignable(leftType(that), bt, that, 
                "logical operand expression must be a boolean value");
        checkAssignable(rightType(that), bt, that, 
                "logical operand expression must be a boolean value");
        that.setTypeModel(bt);
    }

    private void visitDefaultOperator(Tree.DefaultOp that) {
        ProducedType lhst = leftType(that);
        ProducedType rhst = rightType(that);
        if ( rhst!=null && lhst!=null ) {
            if (!unit.isOptionalType(lhst)) {
                that.getLeftTerm().addError("must be of optional type");
            }
            List<ProducedType> list = new ArrayList<ProducedType>();
            addToUnion(list, rhst);
            addToUnion(list, lhst.minus(unit.getNothingDeclaration()));
            if (list.size()==1) {
                that.setTypeModel(list.get(0));
            }
            else {
                UnionType ut = new UnionType(unit);
                ut.setCaseTypes(list);
                that.setTypeModel(ut.getType());
            }
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
                        ot.getProducedTypeName());
            }*/
        }
    }

    private void visitThenOperator(Tree.ThenOp that) {
        ProducedType lhst = leftType(that);
        ProducedType rhst = rightType(that);
        if ( lhst!=null ) {
            checkAssignable(lhst, unit.getBooleanDeclaration().getType(), that.getLeftTerm(), 
                    "operand expression must be a boolean value");
        }
        if ( rhst!=null ) {
            checkAssignable(rhst, unit.getObjectDeclaration().getType(), that.getRightTerm(),
                    "operand expression may not be an optional type");
            that.setTypeModel(unit.getOptionalType(rhst));
        }
    }

    private void visitInOperator(Tree.InOp that) {
        ProducedType lhst = leftType(that);
        ProducedType rhst = rightType(that);
        if ( rhst!=null && lhst!=null ) {
            checkAssignable(lhst, unit.getObjectDeclaration().getType(), that.getLeftTerm(), 
                    "operand expression must support equality");
            checkAssignable(rhst, unit.getCategoryDeclaration().getType(), that.getRightTerm(), 
                    "operand expression must be a category");
        }
        that.setTypeModel( unit.getBooleanDeclaration().getType() );
    }
    
    private void visitUnaryOperator(Tree.UnaryOperatorExpression that, 
            TypeDeclaration type) {
        ProducedType t = type(that);
        if (t!=null) {
            ProducedType nt = checkOperandType(t, type, that.getTerm(), 
                    "operand expression must be of correct type");
            if (nt!=null) {
                ProducedType at = nt.getTypeArguments().isEmpty() ? 
                        nt : nt.getTypeArgumentList().get(0);
                that.setTypeModel(at);
            }
        }
    }

    private void visitExistsOperator(Tree.Exists that) {
        checkOptional(type(that), that);
        that.setTypeModel(unit.getBooleanDeclaration().getType());
    }
    
    private void visitNonemptyOperator(Tree.Nonempty that) {
        checkEmpty(type(that), that);
        that.setTypeModel(unit.getBooleanDeclaration().getType());
    }
    
    private void visitIsOperator(Tree.IsOp that) {
        /*checkAssignable( type(that), 
                getOptionalType(getObjectDeclaration().getType()), 
                that.getTerm(), 
                "expression may not be of void type");*/
        Tree.Type rt = that.getType();
        if (rt!=null) {
            ProducedType t = rt.getTypeModel();
            if (t!=null) {
                checkReified(that, t);
                if (that.getTerm()!=null) {
                    ProducedType pt = that.getTerm().getTypeModel();
                    if (pt!=null && pt.isSubtypeOf(t)) {
                        that.addError("expression type is a subtype of the type: " +
                                pt.getProducedTypeName() + " is assignable to " +
                                t.getProducedTypeName());
                    }
                    else {
                        ProducedType it = intersectionType(t, pt, unit);
                        if (it.getDeclaration() instanceof BottomType) {
                            that.addError("tests assignability to Bottom type: intersection of " +
                                    pt.getProducedTypeName() + " and " + 
                                    t.getProducedTypeName() +
                                    " is empty");
                        }
                    }
                }
            }
        }
        that.setTypeModel(unit.getBooleanDeclaration().getType());
    }
    
    private void visitAssignOperator(Tree.AssignOp that) {
        ProducedType rhst = rightType(that);
        ProducedType lhst = leftType(that);
        if ( rhst!=null && lhst!=null ) {
            checkAssignable(rhst, lhst, that.getRightTerm(), 
                    "assigned expression must be assignable to declared type");
        }
        //that.setTypeModel(rhst); //stef requests lhst to make it easier on backend
        that.setTypeModel(lhst);
    }

    private static void checkAssignability(Tree.Term that, Node node) {
        if (that instanceof Tree.BaseMemberExpression ||
                that instanceof Tree.QualifiedMemberExpression) {
            ProducedReference pr = ((Tree.MemberOrTypeExpression) that).getTarget();
            if (pr!=null) {
                Declaration dec = pr.getDeclaration();
                if (!(dec instanceof Value | dec instanceof Getter)) {
                    node.addError("member cannot be assigned: " 
                            + dec.getName());
                }
                else if ( !((TypedDeclaration) dec).isVariable() ) {
                    node.addError("value is not variable: " 
                            + dec.getName());
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
    
    @Override public void visit(Tree.ArithmeticOp that) {
        super.visit(that);
        visitArithmeticOperator(that, getArithmeticDeclaration(that));
    }

    private Interface getArithmeticDeclaration(Tree.ArithmeticOp that) {
        if (that instanceof Tree.SumOp) {
            return unit.getSummableDeclaration();
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
        else if (that instanceof Tree.RemainderAssignOp) {
            return unit.getIntegralDeclaration();
        }
        else {
            return unit.getNumericDeclaration();
        }
    }

    @Override public void visit(Tree.BitwiseOp that) {
        super.visit(that);
        that.addWarning("Set operators not yet supported");
        visitSetOperator(that);
    }

    @Override public void visit(Tree.LogicalOp that) {
        super.visit(that);
        visitLogicalOperator(that);
    }

    @Override public void visit(Tree.EqualityOp that) {
        super.visit(that);
        visitComparisonOperator(that, unit.getObjectDeclaration());
    }

    @Override public void visit(Tree.ComparisonOp that) {
        super.visit(that);
        visitComparisonOperator(that, unit.getComparableDeclaration());
    }

    @Override public void visit(Tree.IdenticalOp that) {
        super.visit(that);
        visitComparisonOperator(that, unit.getIdentifiableObjectDeclaration());
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
        super.visit(that);
        visitAssignOperator(that);
        checkAssignability(that.getLeftTerm(), that);
    }
        
    @Override public void visit(Tree.ArithmeticAssignmentOp that) {
        super.visit(that);
        visitArithmeticAssignOperator(that, getArithmeticDeclaration(that));
        checkAssignability(that.getLeftTerm(), that);
    }
        
    @Override public void visit(Tree.LogicalAssignmentOp that) {
        super.visit(that);
        visitLogicalOperator(that);
        checkAssignability(that.getLeftTerm(), that);
    }
        
    @Override public void visit(Tree.BitwiseAssignmentOp that) {
        super.visit(that);
        that.addWarning("Set operators not yet supported");
        visitSetAssignmentOperator(that);
        checkAssignability(that.getLeftTerm(), that);
    }
        
    @Override public void visit(Tree.RangeOp that) {
        super.visit(that);
        visitRangeOperator(that);
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
        
    @Override public void visit(Tree.Extends that) {
        super.visit(that);
        that.addWarning("extends operator not yet supported");
    }
        
    @Override public void visit(Tree.Satisfies that) {
        super.visit(that);
        that.addWarning("satisfies operator not yet supported");
    }
        
    @Override public void visit(Tree.InOp that) {
        super.visit(that);
        visitInOperator(that);
    }
        
    //Atoms:
    
    private void checkOverloadedReference(Tree.MemberOrTypeExpression that) {
        if (that.getDeclaration() instanceof Functional &&
                ((Functional)that.getDeclaration()).isAbstraction() &&
                that.getSignature() != null) {
            that.addError("ambiguous reference to overloaded method or class: " +
                    that.getDeclaration().getName());
        }
    }
    
    @Override public void visit(Tree.BaseMemberExpression that) {
        //TODO: this does not correctly handle methods
        //      and classes which are not subsequently 
        //      invoked (should return the callable type)
        /*if (that.getTypeArgumentList()!=null)
            that.getTypeArgumentList().visit(this);*/
        super.visit(that);
        TypedDeclaration member = getBaseDeclaration(that, that.getSignature());
        if (member==null) {
            that.addError("method or attribute does not exist: " +
                    name(that.getIdentifier()), 100);
            unit.getUnresolvedReferences().add(that.getIdentifier());
        }
        else {
            that.setDeclaration(member);
            Tree.TypeArguments tal = that.getTypeArguments();
            if (explicitTypeArguments(member, tal)) {
                List<ProducedType> ta = getTypeArguments(tal);
                tal.setTypeModels(ta);
                visitBaseMemberExpression(that, member, ta, tal);
            }
            //otherwise infer type arguments later
            /*if (defaultArgument) {
                if (member.isClassOrInterfaceMember()) {
                    that.addWarning("references to this from default argument expressions not yet supported");
                }
            }*/
            checkOverloadedReference(that);
        }
    }

    @Override public void visit(Tree.QualifiedMemberExpression that) {
        /*that.getPrimary().visit(this);
        if (that.getTypeArgumentList()!=null)
            that.getTypeArgumentList().visit(this);*/
        super.visit(that);
        ProducedType pt = that.getPrimary().getTypeModel();
        if (pt!=null && that.getIdentifier()!=null && 
                !that.getIdentifier().getText().equals("")) {
            TypeDeclaration d = unwrap(pt, that).getDeclaration();
            TypedDeclaration member = (TypedDeclaration) d.getMember(name(that.getIdentifier()), 
                    unit, that.getSignature());
            if (member==null) {
                that.addError("member method or attribute does not exist: " +
                        name(that.getIdentifier()) + 
                        " in type " + d.getName(), 100);
                unit.getUnresolvedReferences().add(that.getIdentifier());
            }
            else {
                that.setDeclaration(member);
                if (!member.isVisible(that.getScope())) {
                    that.addError("member method or attribute is not visible: " +
                            name(that.getIdentifier()) + 
                            " of type " + d.getName(), 400);
                }
                if (member.isProtectedVisibility() &&
                        !(that.getPrimary() instanceof Tree.This) &&
                        !(that.getPrimary() instanceof Tree.Super)) {
                    that.addError("member method or attribute is not visible: " +
                            name(that.getIdentifier()) + 
                            " of type " + d.getName());
                }
                Tree.TypeArguments tal = that.getTypeArguments();
                if (explicitTypeArguments(member,tal)) {
                    List<ProducedType> ta = getTypeArguments(tal);
                    tal.setTypeModels(ta);
                    visitQualifiedMemberExpression(that, member, ta, tal);
                }
                //otherwise infer type arguments later
                checkOverloadedReference(that);
            }
            if (that.getPrimary() instanceof Tree.Super) {
                if (member!=null && member.isFormal()) {
                    that.addError("superclass member is formal");
                }
            }
        }
    }

    private void visitQualifiedMemberExpression(Tree.QualifiedMemberExpression that,
            TypedDeclaration member, List<ProducedType> typeArgs, Tree.TypeArguments tal) {
        ProducedType receivingType = that.getPrimary().getTypeModel();
        ProducedType receiverType = unwrap(receivingType, that);
        if (acceptsTypeArguments(receiverType, member, typeArgs, tal, that)) {
            ProducedTypedReference ptr = receiverType.getTypedMember(member, typeArgs);
            /*if (ptr==null) {
                that.addError("member method or attribute does not exist: " + 
                        member.getName() + " of type " + 
                        receiverType.getDeclaration().getName());
            }
            else {*/
                ProducedType t = ptr.getFullType(wrap(ptr.getType(), receivingType, that));
                that.setTarget(ptr); //TODO: how do we wrap ptr???
                that.setTypeModel(t);
            //}
        }
    }
    
    private void visitBaseMemberExpression(Tree.BaseMemberExpression that, TypedDeclaration member, 
            List<ProducedType> typeArgs, Tree.TypeArguments tal) {
        if (acceptsTypeArguments(member, typeArgs, tal, that)) {
            ProducedType outerType = that.getScope().getDeclaringType(member);
            ProducedTypedReference pr = member.getProducedTypedReference(outerType, typeArgs);
            that.setTarget(pr);
            ProducedType t = pr.getFullType();
            if (t==null || t.getDeclaration() instanceof UnknownType) {
                that.addError("could not determine type of method or attribute reference: " +
                        name(that.getIdentifier()));
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
        TypeDeclaration type = getBaseDeclaration(that, that.getSignature());
        if (type==null) {
            that.addError("type does not exist: " + 
                    name(that.getIdentifier()), 100);
            unit.getUnresolvedReferences().add(that.getIdentifier());
        }
        else {
            that.setDeclaration(type);
            Tree.TypeArguments tal = that.getTypeArguments();
            if (explicitTypeArguments(type, tal)) {
                List<ProducedType> ta = getTypeArguments(tal);
                tal.setTypeModels(ta);
                visitBaseTypeExpression(that, type, ta, tal);
            }
            //otherwise infer type arguments later
            checkOverloadedReference(that);
        }
    }
        
    @Override public void visit(Tree.QualifiedTypeExpression that) {
        super.visit(that);
        /*that.getPrimary().visit(this);
        if (that.getTypeArgumentList()!=null)
            that.getTypeArgumentList().visit(this);*/
        ProducedType pt = that.getPrimary().getTypeModel();
        if (that.getPrimary() instanceof Tree.QualifiedTypeExpression || 
                that.getPrimary() instanceof Tree.BaseTypeExpression) {
            //this is a qualified type name, not member reference
            pt = ((Tree.MemberOrTypeExpression) that.getPrimary()).getTarget().getType();
        }
        if (pt!=null) {
            TypeDeclaration d = unwrap(pt, that).getDeclaration();
            TypeDeclaration type = (TypeDeclaration) d.getMember(name(that.getIdentifier()), 
                    unit, that.getSignature());
            if (type==null) {
                that.addError("member type does not exist: " +
                        name(that.getIdentifier()) +
                        " in type " + d.getName(), 100);
                unit.getUnresolvedReferences().add(that.getIdentifier());
            }
            else {
                that.setDeclaration(type);
                if (!type.isVisible(that.getScope())) {
                    that.addError("member type is not visible: " +
                            name(that.getIdentifier()) +
                            " of type " + d.getName(), 400);
                }
                if (type.isProtectedVisibility() &&
                        !(that.getPrimary() instanceof Tree.This) &&
                        !(that.getPrimary() instanceof Tree.Super)) {
                    that.addError("member type is not visible: " +
                            name(that.getIdentifier()) +
                            " of type " + d.getName());
                }
                Tree.TypeArguments tal = that.getTypeArguments();
                if (explicitTypeArguments(type, tal)) {
                    List<ProducedType> ta = getTypeArguments(tal);
                    tal.setTypeModels(ta);
                    visitQualifiedTypeExpression(that, pt, type, ta, tal);
                    //otherwise infer type arguments later
                }
                checkOverloadedReference(that);
            }
            //TODO: this is temporary until we get metamodel reference expressions!
            if (that.getPrimary() instanceof Tree.BaseTypeExpression ||
                    that.getPrimary() instanceof Tree.QualifiedTypeExpression) {
                ProducedReference target = that.getTarget();
                if (target!=null) {
                    checkTypeBelongsToContainingScope(target.getType(), 
                            that.getScope(), that);
                }
            }
            if (!inExtendsClause && that.getPrimary() instanceof Tree.Super) {
                if (type!=null && type.isFormal()) {
                    that.addError("superclass member class is formal");
                }
            }
        }
    }

    private boolean explicitTypeArguments(Declaration dec, Tree.TypeArguments tal) {
        return !dec.isParameterized() || tal instanceof Tree.TypeArgumentList;
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
            acceptsTypeArguments(type, getTypeArguments(tal), tal, that);
            //the type has already been set by TypeVisitor
        }
    }
        

    private void visitQualifiedTypeExpression(Tree.QualifiedTypeExpression that,
            ProducedType receivingType, TypeDeclaration type, 
            List<ProducedType> typeArgs, Tree.TypeArguments tal) {
        ProducedType receiverType = unwrap(receivingType, that);
        if (acceptsTypeArguments(receiverType, type, typeArgs, tal, that)) {
            ProducedType t = receiverType.getTypeMember(type, typeArgs);
            that.setTypeModel(t.getFullType(wrap(t, receivingType, that)));
            that.setTarget(t);
        }
    }

    private void visitBaseTypeExpression(Tree.BaseTypeExpression that, TypeDeclaration type, 
            List<ProducedType> typeArgs, Tree.TypeArguments tal) {
        ProducedType outerType = that.getScope().getDeclaringType(type);
        ProducedType t = type.getProducedType(outerType, typeArgs);
        if (!type.isAlias()) {
            //TODO: remove this awful hack which means
            //      we can't define aliases for types
            //      with sequenced type parameters
            type = t.getDeclaration();
        }
        if ( acceptsTypeArguments(type, typeArgs, tal, that) ) {
            that.setTypeModel(t.getFullType());
            that.setTarget(t);
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
        if (defaultArgument) {
            that.addError("reference to outer from default argument expression");
        }
    }

    private boolean inExtendsClause = false;

    @Override public void visit(Tree.Super that) {
        if (inExtendsClause) {
            ClassOrInterface ci = getContainingClassOrInterface(that.getScope());
            if (ci!=null) {
                if (ci.isClassOrInterfaceMember()) {
                    ClassOrInterface s = (ClassOrInterface) ci.getContainer();
                    ProducedType t = s.getExtendedType();
                    //TODO: type arguments??
                    that.setTypeModel(t);
                }
            }
        }
        else {
            ClassOrInterface ci = getContainingClassOrInterface(that.getScope());
            //TODO: for consistency, move these errors to SelfReferenceVisitor
            if (ci==null) {
                that.addError("super appears outside a class definition");
            }
            else if (!(ci instanceof Class)) {
                that.addError("super appears inside an interface definition");
            }
            else {
                ProducedType t = ci.getExtendedType();
                //TODO: type arguments
                that.setTypeModel(t);
            }
        }
        if (defaultArgument) {
            that.addError("reference to super from default argument expression");
        }
    }
    
    @Override public void visit(Tree.This that) {
        ClassOrInterface ci = getContainingClassOrInterface(that.getScope());
        if (ci==null) {
            that.addError("this appears outside a class or interface definition");
        }
        else {
            that.setTypeModel(ci.getType());
        }
        /*if (defaultArgument) {
            that.addWarning("references to this from default argument expressions not yet supported");
        }*/
    }
    
    @Override public void visit(Tree.SequenceEnumeration that) {
        super.visit(that);
        ProducedType st;
        if ( that.getExpressionList()==null ) {
            st = unit.getEmptyDeclaration().getType();
        }
        else {
            ProducedType et;
            List<ProducedType> list = new ArrayList<ProducedType>();
            for (Tree.Expression e: that.getExpressionList().getExpressions()) {
                if (e.getTypeModel()!=null) {
                    addToUnion(list, e.getTypeModel());
                }
            }
            if (list.isEmpty()) {
//                that.addError("could not infer type of sequence enumeration");
                return;
            }
            else if (list.size()==1) {
                et = list.get(0);
            }
            else {
                UnionType ut = new UnionType(unit);
                ut.setExtendedType( unit.getObjectDeclaration().getType() );
                ut.setCaseTypes(list);
                et = ut.getType(); 
            }
            st = unit.getSequenceType(et);
        }
        that.setTypeModel(st);
    }

    @Override public void visit(Tree.CatchVariable that) {
        super.visit(that);
        Variable var = that.getVariable();
        if (var!=null) {
            ProducedType et = unit.getExceptionDeclaration().getType();
            if (var.getType() instanceof Tree.LocalModifier) {
                var.getType().setTypeModel(et);
                var.getDeclarationModel().setType(et);
            }
            else {
                checkAssignable(var.getType().getTypeModel(), et, 
                        var.getType(), 
                        "catch type must be an exception type");
            }
        }
    }
    
    @Override public void visit(Tree.StringTemplate that) {
        super.visit(that);
        for (Tree.Expression e: that.getExpressions()) {
            checkAssignable(e.getTypeModel(), unit.getObjectDeclaration().getType(), e, 
                    "interpolated expression must be assignable to Object");
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
        setLiteralType(that, unit.getCharacterDeclaration());
    }
    
    @Override public void visit(Tree.QuotedLiteral that) {
        setLiteralType(that, unit.getQuotedDeclaration());
        String fn = that.getUnit().getFilename();
        if (!"package.ceylon".equals(fn) && !"module.ceylon".equals(fn)) {
            that.addWarning("single-quoted literals are not yet supported");
        }
    }
    
    private void setLiteralType(Tree.Atom that, TypeDeclaration languageType) {
        that.setTypeModel(languageType.getType());
    }
    
    @Override
    public void visit(Tree.CompilerAnnotation that) {
        //don't visit the argument       
    }
    
    @Override
    public void visit(Tree.MatchCase that) {
        super.visit(that);
        for (Tree.Expression e: that.getExpressionList().getExpressions()) {
            ProducedType t = e.getTypeModel();
            if (t!=null) {
                TypeDeclaration dec = t.getDeclaration();
                if (!dec.isToplevel() || isUpperCase(dec.getName().charAt(0))) {
                    e.addError("case must refer to a toplevel object declaration");
                }
            }
        }
    }
    
    @Override
    public void visit(Tree.SatisfiesCase that) {
        super.visit(that);
        that.addWarning("satisfies cases are not yet supported");
    }
    
    @Override
    public void visit(Tree.IsCase that) {
        Tree.Type t = that.getType();
        if (t!=null) {
            t.visit(this);
        }
        Variable v = that.getVariable();
        if (v!=null) {
            v.visit(this);
            if (t!=null) {
                ProducedType pt = t.getTypeModel();
                if (pt!=null) {
                    v.getType().setTypeModel(pt);
                    v.getDeclarationModel().setType(pt);
                }
            }
        }
    }
    
    @Override
    public void visit(Tree.SwitchStatement that) {
        super.visit(that);
        Expression e = that.getSwitchClause().getExpression();
        if (e!=null) {
            ProducedType switchType = e.getTypeModel().getUnionOfCases(true);            
            for (CaseClause cc: that.getSwitchCaseList().getCaseClauses()) {
                ProducedType ct = getType(cc);
                if (ct!=null) {
                    checkAssignable(ct, switchType, cc, 
                            "case type must be a case of the switch type");
                    for (CaseClause occ: that.getSwitchCaseList().getCaseClauses()) {
                        if (occ==cc) break;
                        ProducedType oct = getType(occ);
                        if (oct!=null) {
                            //TODO: the following test doesn't work for cases of an interface!
                            if (!intersectionType(ct, oct, unit)
                                    .isExactly(unit.getBottomDeclaration().getType())) {
                                cc.getCaseItem().addError("cases are not disjoint: " + 
                                    ct.getProducedTypeName() + " and " + 
                                        oct.getProducedTypeName());
                            }
                        }
                    }
                }
            }
            
            if (that.getSwitchCaseList().getElseClause()==null) {
                List<ProducedType> list = new ArrayList<ProducedType>();
                for (CaseClause cc: that.getSwitchCaseList().getCaseClauses()) {
                    ProducedType ct = getType(cc);
                    if (ct!=null) {
                        addToUnion(list, ct);
                    }
                }
                UnionType ut = new UnionType(unit);
                ut.setCaseTypes(list);
                checkAssignable(switchType, ut.getType(), that, 
                        "case types must cover all cases of the switch type or an else clause must appear");
            }
        }
    }

    private ProducedType getType(Tree.CaseClause cc) {
        CaseItem ci = cc.getCaseItem();
        if (ci instanceof Tree.IsCase) {
            Type t = ((Tree.IsCase) ci).getType();
            if (t!=null) {
                return t.getTypeModel().getUnionOfCases(true);
            }
            else {
                return null;
            }
        }
        else if (ci instanceof Tree.MatchCase) {
            List<ProducedType> list = new ArrayList<ProducedType>();
            for (Tree.Expression e: ((Tree.MatchCase) ci).getExpressionList().getExpressions()) {
                if (e.getTypeModel()!=null) {
                    addToUnion(list, e.getTypeModel());
                }
            }
            UnionType ut = new UnionType(unit);
            ut.setCaseTypes(list);
            return ut.getType();
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
                                                    + ct.getProducedTypeName());
                                }
                                if (ct.getDeclaration() instanceof UnionType) {
                                    for (ProducedType ut: ct.getDeclaration().getCaseTypes()) {
                                        if ( ut.substitute(ct.getTypeArguments()).isSubtypeOf(ect) ) {
                                            cc.getCatchVariable().getVariable().getType()
                                                    .addError("exception type is already handled by earlier catch clause: "
                                                            + ut.getProducedTypeName());
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

    private static boolean acceptsTypeArguments(Declaration member, List<ProducedType> typeArguments, 
            Tree.TypeArguments tal, Node parent) {
        return acceptsTypeArguments(null, member, typeArguments, tal, parent);
    }
    
    private static boolean isGeneric(Declaration member) {
        return member instanceof Generic && 
            !((Generic) member).getTypeParameters().isEmpty();
    }
    
    private static boolean acceptsTypeArguments(ProducedType receiver, Declaration member, 
            List<ProducedType> typeArguments, Tree.TypeArguments tal, Node parent) {
        if (member==null) return false;
        if (isGeneric(member)) {
            List<TypeParameter> params = ((Generic) member).getTypeParameters();
            if ( params.size()==typeArguments.size() ) {
                for (int i=0; i<params.size(); i++) {
                    TypeParameter param = params.get(i);
                    ProducedType argType = typeArguments.get(i);
                    //Map<TypeParameter, ProducedType> self = Collections.singletonMap(param, arg);
                    for (ProducedType st: param.getSatisfiedTypes()) {
                        //sts = sts.substitute(self);
                        ProducedType sts = st.getProducedType(receiver, member, typeArguments);
                        if (argType!=null) {
                            if (!argType.isSubtypeOf(sts)) {
                                if (tal instanceof Tree.InferredTypeArguments) {
                                    parent.addError("inferred type argument " + argType.getProducedTypeName()
                                            + " to type parameter " + param.getName()
                                            + " of declaration " + member.getName()
                                            + " not assignable to " + sts.getProducedTypeName());
                                }
                                else {
                                    ( (Tree.TypeArgumentList) tal ).getTypes()
                                            .get(i).addError("type parameter " + param.getName() 
                                            + " of declaration " + member.getName()
                                            + " has argument " + argType.getProducedTypeName() 
                                            + " not assignable to " + sts.getProducedTypeName());
                                }
                                return false;
                            }
                        }
                    }
                    boolean asec = argumentSatisfiesEnumeratedConstraint(receiver, member, 
                            typeArguments, argType, param);
                    if (!asec) {
                        if (tal instanceof Tree.InferredTypeArguments) {
                            parent.addError("inferred type argument " + argType.getProducedTypeName()
                                    + " to type parameter " + param.getName()
                                    + " of declaration " + member.getName()
                                    + " not one of the enumerated cases");
                        }
                        else {
                            ( (Tree.TypeArgumentList) tal ).getTypes()
                            .get(i).addError("type parameter " + param.getName() 
                                    + " of declaration " + member.getName()
                                    + " has argument " + argType.getProducedTypeName() 
                                    + " not one of the enumerated cases");
                        }
                        return false;
                    }
                }
                return true;
            }
            else {
                if (tal==null || tal instanceof Tree.InferredTypeArguments) {
                    parent.addError("requires type arguments: " + member.getName());
                }
                else {
                    tal.addError("wrong number of type arguments to: " + member.getName());
                }
                return false;
            }
        }
        else {
            boolean empty = typeArguments.isEmpty();
            if (!empty) {
                tal.addError("does not accept type arguments: " + 
                        member.getName());
            }
            return empty;
        }
    }

    private static boolean argumentSatisfiesEnumeratedConstraint(ProducedType receiver, 
            Declaration member, List<ProducedType> typeArguments, ProducedType argType,
            TypeParameter param) {
        
        List<ProducedType> caseTypes = param.getCaseTypes();
        if (caseTypes==null) {
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
            if (argCaseTypes!=null) {
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

    @Override 
    public void visit(Tree.ExtendedType that) {
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
            }
        }
    }
    
    @Override 
    public void visit(Tree.SatisfiedTypes that) {
        super.visit(that);
        TypeDeclaration td = (TypeDeclaration) that.getScope();
        for (Tree.StaticType t: that.getTypes()) {
            ProducedType type = t.getTypeModel();
            if (type!=null) {
                checkSelfTypes(t, td, type);
                checkExtensionOfMemberType(t, td, type);
                /*if (!(td instanceof TypeParameter)) {
                    checkCaseOfSupertype(t, td, type);
                }*/
            }
        }
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
                    type.getDeclaration().getName());
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
        
        if (!(td instanceof TypeParameter)) {
            for (Tree.StaticType t: that.getTypes()) {
                ProducedType type = t.getTypeModel();
                if (!(type.getDeclaration() instanceof TypeParameter)) {
                    //it's not a self type
                    if (type!=null) {
                        checkAssignable(type, td.getType(), t, 
                                "case type must be a subtype of enumerated type");
                    }
                }
            }
            for (Tree.BaseMemberExpression bme: that.getBaseMemberExpressions()) {
                ProducedType type = bme.getTypeModel();
                if (type!=null) {
                    checkAssignable(type, td.getType(), bme, 
                            "case type must be a subtype of enumerated type");
                }
            }
        }
        
        //TODO: get rid of this awful hack:
        td.setCaseTypes(cases);
    }

    private void checkExtensionOfMemberType(Node that, TypeDeclaration td,
            ProducedType type) {
        ProducedType qt = type.getQualifyingType();
        if (qt!=null && td instanceof ClassOrInterface) {
            Scope s = td;
            while (s!=null) {
                s = s.getContainer();
                if (s instanceof TypeDeclaration) {
                    TypeDeclaration otd = (TypeDeclaration) s;
                    if ( otd.getType().isSubtypeOf(qt) ) {
                        return;
                    }
                }
            }
            that.addError("containing type " + qt.getDeclaration().getName() + 
                    " of supertype " + type.getDeclaration().getName() + 
                    " is not an outer type or supertype of any outer type of " +
                    td.getName());
        }
    }
    
    private void checkSelfTypes(Node that, TypeDeclaration td, ProducedType type) {
        if (!(td instanceof TypeParameter)) { //TODO: is this really ok?!
            List<TypeParameter> params = type.getDeclaration().getTypeParameters();
            List<ProducedType> args = type.getTypeArgumentList();
            for (int i=0; i<params.size(); i++) {
                TypeParameter param = params.get(i);
                if ( param.isSelfType() && args.size()>i ) {
                    ProducedType arg = args.get(i);
                    TypeDeclaration std = param.getSelfTypedDeclaration();
                    ProducedType at;
                    if (param.getContainer().equals(std)) {
                        at = td.getType();
                    }
                    else {
                        //TODO: lots wrong here?
                        TypeDeclaration mtd = (TypeDeclaration) td.getMember(std.getName(), null);
                        at = mtd==null ? null : mtd.getType();
                    }
                    if (at!=null) {
                        checkAssignable(at, arg, std, that,
                                "type argument does not satisfy self type constraint on type parameter " + 
                                    param.getName() + " of " + type.getDeclaration().getName());
                    }
                }
            }
        }
    }

    private void validateEnumeratedSupertypes(Node that, Class d) {
        ProducedType type = d.getType();
        for (ProducedType supertype: type.getSupertypes()) {
            if (!type.isExactly(supertype)) {
                TypeDeclaration std = supertype.getDeclaration();
                if (std.getCaseTypes()!=null) {
                    List<ProducedType> types=new ArrayList<ProducedType>();
                    for (ProducedType ct: std.getCaseTypes()) {
                        ProducedType cst = type.getSupertype(ct.getDeclaration());
                        if (cst!=null) {
                            types.add(cst);
                        }
                    }
                    if (types.isEmpty()) {
                        that.addError("concrete type is not a subtype of any case of enumerated supertype: " + 
                                d.getName() + " is a subtype of " + std.getName());
                    }
                    else if (types.size()>1) {
                        StringBuilder sb = new StringBuilder();
                        for (ProducedType pt: types) {
                            sb.append(pt.getProducedTypeName()).append(" and ");
                        }
                        sb.setLength(sb.length()-5);
                        that.addError("concrete type is a subtype of multiple cases of enumerated supertype: " + 
                                d.getName() + " is a subtype of " + sb);
                    }
                }
            }
        }
    }

    private void validateEnumeratedSupertypeArguments(Node that, ClassOrInterface d) {
        ProducedType type = d.getType();
        for (ProducedType supertype: type.getSupertypes()) {
            if (!type.isExactly(supertype)) {
                List<TypeDeclaration> ctds = supertype.getDeclaration().getCaseTypeDeclarations();
                if (ctds!=null) {
                    for (TypeDeclaration ct: ctds) {
                        if (ct.equals(d)) {
                            List<TypeParameter> params = supertype.getDeclaration().getTypeParameters();
                            for (int i=0; i<params.size(); i++) {
                                TypeParameter p = params.get(i);
                                ProducedType arg = supertype.getTypeArguments().get(p);
                                if (arg!=null) {
                                    TypeDeclaration td = arg.getDeclaration();
                                    if (td instanceof TypeParameter && ((TypeParameter) td).getDeclaration().equals(d)) {
                                        if (p.isCovariant() && !((TypeParameter) td).isCovariant()) {
                                            that.addError("argument to covariant type parameter of supertype must be covariant: " + 
                                                    p.getName() + " of "+ supertype.getDeclaration().getName());
                                        }
                                        if (p.isContravariant() && !((TypeParameter) td).isContravariant()) {
                                            that.addError("argument to contravariant type parameter of supertype must be contravariant: " + 
                                                    p.getName() + " of "+ supertype.getDeclaration().getName());
                                        }
                                    }
                                    else if (p.isCovariant()) {
                                        if (!(td instanceof BottomType)) {
                                            that.addError("argument to covariant type parameter of supertype must be a type parameter or Bottom: " + 
                                                    p.getName() + " of "+ supertype.getDeclaration().getName());
                                        }
                                    }
                                    //TODO!!!!!
                                    /*else if (p.isContravariant()) {
                                    if (!(arg.isExactly(intersection of p.getSatisfiedTypes())) {
                                        that.addError("argument to covariant type parameter of supertype must be a type parameter or ..." + 
                                                p.getName() + " of "+ supertype.getProducedTypeName());
                                    }
                                }*/
                                    else {
                                        that.addError("argument to type parameter of supertype must be a type parameter: " + 
                                                p.getName() + " of "+ supertype.getDeclaration().getName());
                                    }
                                }
                            }
                            break;
                        }
                    }
                }
            }
        }
    }
    
    @Override public void visit(Tree.Term that) {
        super.visit(that);
        if (that.getTypeModel()==null) {
            that.setTypeModel( defaultType() );
        }
        if (that instanceof Tree.Expression) {
            Tree.Expression expr = (Tree.Expression)that;
            Tree.Term term = expr.getTerm();
            if (term instanceof Tree.QualifiedMemberOrTypeExpression) {
                Tree.QualifiedMemberOrTypeExpression qme = (Tree.QualifiedMemberOrTypeExpression)term;
                if (qme.getMemberOperator() instanceof Tree.SpreadOp
                        && qme.getTypeModel().getDeclaration().getQualifiedNameString().equals("ceylon.language.Callable")) {
                    that.addWarning("spread method references not supported yet");
                }
                if (qme.getMemberOperator() instanceof Tree.SafeMemberOp
                        && qme.getTypeModel().getDeclaration().getQualifiedNameString().equals("ceylon.language.Callable")) {
                    that.addWarning("null-safe method references not supported yet");
                }
            }
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
        ut.setExtendedType(unit.getVoidDeclaration().getType());
        return ut.getType();
    }
    
}
