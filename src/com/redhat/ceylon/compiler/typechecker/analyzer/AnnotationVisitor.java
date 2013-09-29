package com.redhat.ceylon.compiler.typechecker.analyzer;

import static com.redhat.ceylon.compiler.typechecker.analyzer.Util.checkAssignable;
import static com.redhat.ceylon.compiler.typechecker.model.Module.LANGUAGE_MODULE_NAME;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.redhat.ceylon.compiler.typechecker.model.Class;
import com.redhat.ceylon.compiler.typechecker.model.ClassOrInterface;
import com.redhat.ceylon.compiler.typechecker.model.Declaration;
import com.redhat.ceylon.compiler.typechecker.model.Functional;
import com.redhat.ceylon.compiler.typechecker.model.IntersectionType;
import com.redhat.ceylon.compiler.typechecker.model.Method;
import com.redhat.ceylon.compiler.typechecker.model.MethodOrValue;
import com.redhat.ceylon.compiler.typechecker.model.Package;
import com.redhat.ceylon.compiler.typechecker.model.Parameter;
import com.redhat.ceylon.compiler.typechecker.model.ProducedType;
import com.redhat.ceylon.compiler.typechecker.model.TypeDeclaration;
import com.redhat.ceylon.compiler.typechecker.model.TypedDeclaration;
import com.redhat.ceylon.compiler.typechecker.model.UnionType;
import com.redhat.ceylon.compiler.typechecker.model.Unit;
import com.redhat.ceylon.compiler.typechecker.model.Value;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.Annotation;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.AnnotationList;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.PositionalArgument;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.Term;
import com.redhat.ceylon.compiler.typechecker.tree.Visitor;

public class AnnotationVisitor extends Visitor {
    
    private static boolean isIllegalAnnotationParameterType(ProducedType pt) {
        if (pt!=null) {
            TypeDeclaration ptd = pt.getDeclaration();
            if (ptd instanceof IntersectionType || ptd instanceof UnionType) {
                return true;
            }
            Unit unit = pt.getDeclaration().getUnit();
            if (!ptd.isAnnotation() && !isEnum(ptd) &&
                    !ptd.equals(unit.getBooleanDeclaration()) &&
                    !ptd.equals(unit.getStringDeclaration()) &&
                    !ptd.equals(unit.getIntegerDeclaration()) &&
                    !ptd.equals(unit.getFloatDeclaration()) &&
                    !ptd.equals(unit.getCharacterDeclaration()) &&
                    !ptd.equals(unit.getIterableDeclaration()) &&
                    !ptd.equals(unit.getSequentialDeclaration()) &&
                    !pt.isSubtypeOf(unit.getType(unit.getDeclarationDeclaration()))) {
                return true;
            }
            if (ptd.equals(unit.getIterableDeclaration()) ||
                    ptd.equals(unit.getSequentialDeclaration())) {
                if (isIllegalAnnotationParameterType(unit.getIteratedType(pt))) {
                    return true;
                }
            }
        }
        return false;
    }
    
    private static boolean isEnum(TypeDeclaration ptd) {
        List<TypeDeclaration> ctds = ptd.getCaseTypeDeclarations();
        if (ctds==null) {
            return false;
        }
        else {
            for (TypeDeclaration td: ctds) {
                if (!td.isAnonymous()) return false;
            }
            return true;
        }
    }
    
    private void checkAnnotationParameter(Functional a, Tree.Parameter pn) {
        Parameter p = pn.getParameterModel();
        ProducedType pt = p.getType();
        if (pt!=null && isIllegalAnnotationParameterType(pt)) {
            pn.addError("illegal annotation parameter type: " + pt.getProducedTypeName());
        }
        Tree.SpecifierOrInitializerExpression se = null;
        if (pn instanceof Tree.InitializerParameter) {
            se = ((Tree.InitializerParameter) pn).getSpecifierExpression();
        }
        else if (pn instanceof Tree.ParameterDeclaration) {
            Tree.TypedDeclaration td = ((Tree.ParameterDeclaration) pn).getTypedDeclaration();
            if (td instanceof Tree.MethodDeclaration) {
                se = ((Tree.MethodDeclaration) td).getSpecifierExpression();
            }
            else if (td instanceof Tree.AttributeDeclaration) {
                se = ((Tree.AttributeDeclaration) td).getSpecifierOrInitializerExpression();
            }
        }
        if (se!=null) {
            checkAnnotationArgument(a, se.getExpression(), pt);
        }
    }
    
    private void checkAnnotationArgument(Functional a, Tree.Expression e, ProducedType pt) {
        if (e!=null) {
            Tree.Term term = e.getTerm();
            if (term instanceof Tree.Literal) {
                //ok
            }
            else if (term instanceof Tree.NegativeOp && 
                    ((Tree.NegativeOp) term).getTerm() instanceof Tree.Literal) {
                //ok
            }
            else if (term instanceof Tree.MetaLiteral) {
                //ok
            }
            else if (term instanceof Tree.Tuple) {
                Tree.SequencedArgument sa = ((Tree.Tuple) term).getSequencedArgument();
                if (sa!=null) {
                    for (PositionalArgument arg: sa.getPositionalArguments()) {
                        if (arg instanceof Tree.ListedArgument){
                            Tree.Expression expression = ((Tree.ListedArgument) arg).getExpression();
                            if (expression!=null) {
                                checkAnnotationArgument(a, expression, arg.getTypeModel());
                            }
                        }
                        else {
                            e.addError("illegal annotation argument: must be a literal value, metamodel reference, annotation instantiation, or parameter reference");
                        }
                    }
                }
            }
            else if (term instanceof Tree.SequenceEnumeration) {
                Tree.SequencedArgument sa = ((Tree.SequenceEnumeration) term).getSequencedArgument();
                if (sa!=null) {
                    for (Tree.PositionalArgument arg: sa.getPositionalArguments()){
                        if (arg instanceof Tree.ListedArgument){
                            Tree.Expression expression = ((Tree.ListedArgument) arg).getExpression();
                            if (expression!=null) {
                                checkAnnotationArgument(a, expression, arg.getTypeModel());
                            }
                        }
                        else {
                            e.addError("illegal annotation argument: must be a literal value, metamodel reference, annotation instantiation, or parameter reference");
                        }
                    }
                }
            }
            else if (term instanceof Tree.InvocationExpression) {
                checkAnnotationInstantiation(a, e, pt);
            }
            else if (term instanceof Tree.BaseMemberExpression) {
                Declaration d = ((Tree.BaseMemberExpression) term).getDeclaration();
                if (a!=null && d!=null && d.isParameter()) {
                    if (!((MethodOrValue) d).getInitializerParameter().getDeclaration().equals(a)) {
                        e.addError("illegal annotation argument: must be a reference to a parameter of the annotation");
                    }
                }
                else if (d instanceof Value &&
                        (((Value) d).isEnumValue() || ((Value) d).getTypeDeclaration().isAnonymous())) {
                    //ok
                }
                else {
                    e.addError("illegal annotation argument: must be a literal value, metamodel reference, annotation instantiation, or parameter reference");
                }
            }
            else {
                e.addError("illegal annotation argument: must be a literal value, metamodel reference, annotation instantiation, or parameter reference");
            }
        }
    }

    @Override 
    public void visit(Tree.PackageDescriptor that) {
        super.visit(that);
        Unit unit = that.getUnit();
        checkAnnotations(that.getAnnotationList(), 
                unit.getPackageDeclarationType(), null);
    }
    
    @Override 
    public void visit(Tree.ModuleDescriptor that) {
        super.visit(that);
        Unit unit = that.getUnit();
        checkAnnotations(that.getAnnotationList(), 
                unit.getModuleDeclarationType(), null);
    }
    
    @Override 
    public void visit(Tree.ImportModule that) {
        super.visit(that);
        Unit unit = that.getUnit();
        checkAnnotations(that.getAnnotationList(), 
                unit.getImportDeclarationType(), null);
    }
    
    @Override
    public void visit(Tree.AnyClass that) {
        super.visit(that);
        Class c = that.getDeclarationModel();
        if (c.isAnnotation()) {
            checkAnnotationType(that, c);
        }
        
        TypeDeclaration dm = that.getDeclarationModel();
        Unit unit = that.getUnit();
        checkAnnotations(that.getAnnotationList(), 
                unit.getClassDeclarationType(),
                unit.getClassMetatype(dm.getProducedType(qualifyingType(dm), 
                        Collections.<ProducedType>emptyList())));
    }

    @Override 
    public void visit(Tree.AnyInterface that) {
        super.visit(that);
        TypeDeclaration dm = that.getDeclarationModel();
        Unit unit = that.getUnit();
        checkAnnotations(that.getAnnotationList(), 
                unit.getInterfaceDeclarationType(),
                unit.getInterfaceMetatype(dm.getProducedType(qualifyingType(dm), 
                        Collections.<ProducedType>emptyList())));
    }
    
    @Override
    public void visit(Tree.AnyAttribute that) {
        super.visit(that);
        TypedDeclaration dm = that.getDeclarationModel();
        Unit unit = that.getUnit();
        checkAnnotations(that.getAnnotationList(), 
                unit.getValueDeclarationType(dm),
                unit.getValueMetatype(dm.getProducedTypedReference(qualifyingType(dm), 
                        Collections.<ProducedType>emptyList())));
    }

    @Override
    public void visit(Tree.AnyMethod that) {
        super.visit(that);
        Method a = that.getDeclarationModel();
        if (a.isAnnotation()) {
            checkAnnotationConstructor(that, a);
        }
        
        TypedDeclaration dm = that.getDeclarationModel();
        Unit unit = that.getUnit();
        checkAnnotations(that.getAnnotationList(), 
                unit.getFunctionDeclarationType(),
                unit.getFunctionMetatype(dm.getProducedTypedReference(qualifyingType(dm), 
                        Collections.<ProducedType>emptyList())));
    }

    private void checkAnnotationType(Tree.AnyClass that, Class c) {
        if (c.isParameterized()) {
            that.addError("annotation class may not be a parameterized type");
        }
        /*if (c.isAbstract()) {
            that.addError("annotation class may not be abstract");
        }*/
        if (!c.isFinal()) {
            that.addError("annotation class must be final");
        }
        if (!c.getExtendedTypeDeclaration()
                .equals(that.getUnit().getBasicDeclaration())) {
            that.addError("annotation class must directly extend Basic");
        }
        TypeDeclaration annotationDec = that.getUnit().getAnnotationDeclaration();
        if (!c.inherits(annotationDec)) {
            that.addError("annotation class must be a subtype of Annotation");
        }
        for (Tree.Parameter pn: that.getParameterList().getParameters()) {
            checkAnnotationParameter(c, pn);
        }
        if (that instanceof Tree.ClassDefinition) {
            Tree.ClassBody body = ((Tree.ClassDefinition) that).getClassBody();
            if (body!=null && getExecutableStatements(body).size()>0) {
                that.addError("annotation class body may not contain executable statements");
            }
        }
    }

    private void checkAnnotationConstructor(Tree.AnyMethod that, Method a) {
        Tree.Type type = that.getType();
        if (type!=null) {
            ProducedType t = type.getTypeModel();
            if (t!=null && t.getDeclaration()!=null) {
                if (t.getDeclaration().isAnnotation()) {
                    if (!that.getUnit().getPackage().getQualifiedNameString()
                            .equals(LANGUAGE_MODULE_NAME)) {
                        String packageName = t.getDeclaration()
                                .getUnit().getPackage().getQualifiedNameString();
                        String typeName = t.getDeclaration().getName();
                        if (packageName.equals(LANGUAGE_MODULE_NAME) && 
                                (typeName.equals("Shared") ||
                                typeName.equals("Abstract") || 
                                typeName.equals("Default") ||
                                typeName.equals("Formal") ||
                                typeName.equals("Actual") ||
                                typeName.equals("Final") ||
                                typeName.equals("Variable") ||
                                typeName.equals("Late") ||
                                typeName.equals("Native") ||
                                typeName.equals("Deprecated") ||
                                typeName.equals("Annotation"))) {
                            type.addError("annotation constructor may not return modifier annotation type");
                        }
                    }
                } 
                else {
                    type.addError("annotation constructor must return an annotation type");
                }
            }
        }
        List<Tree.ParameterList> pls = that.getParameterLists();
        if (pls.size() == 1) {
            for (Tree.Parameter pn: pls.get(0).getParameters()) {
                checkAnnotationParameter(a, pn);
            }
        }
        else {
            that.addError("annotation constructor must have exactly one parameter list");
        }
        if (that instanceof Tree.MethodDefinition) {
            Tree.Block block = ((Tree.MethodDefinition) that).getBlock();
            if (block!=null) {
                List<Tree.Statement> list = getExecutableStatements(block);
                if (list.size()==1) {
                    Tree.Statement s = list.get(0);
                    if (s instanceof Tree.Return) {
                        Tree.Expression e = ((Tree.Return) s).getExpression();
                        checkAnnotationInstantiation(a, e, a.getType());
                    }
                    else {
                        s.addError("annotation constructor body must return an annotation instance");
                    }
                }
                else {
                    block.addError("annotation constructor body must have exactly one statement");
                }
            }
        }
        else {
            Tree.SpecifierExpression se = ((Tree.MethodDeclaration) that).getSpecifierExpression();
            if (se!=null) {
                checkAnnotationInstantiation(a, se.getExpression(), a.getType());
            }
        }
    }
    
    private static List<Tree.Statement> getExecutableStatements(Tree.Body block) {
        List<Tree.Statement> list = new ArrayList<Tree.Statement>();
        for (Tree.Statement s: block.getStatements()) {
            if (Util.isExecutableStatement(block.getUnit(), s)) {
                list.add(s);
            }
        }
        return list;
    }
    
    private void checkAnnotationInstantiation(Functional a, Tree.Expression e, ProducedType pt) {
        if (e!=null) {
            Term term = e.getTerm();
            if (term instanceof Tree.InvocationExpression) {
                Tree.InvocationExpression ie = (Tree.InvocationExpression) term;
                /*if (!ie.getTypeModel().isExactly(pt)) {
                    ie.addError("annotation constructor must return exactly the annotation type");
                }*/
                Tree.Primary primary = ie.getPrimary();
                if (!(primary instanceof Tree.BaseTypeExpression)
                        && (!(primary instanceof Tree.BaseMemberExpression)
                                || !((Tree.BaseMemberExpression)primary).getDeclaration().isAnnotation())) {
                    term.addError("annotation constructor must return a newly-instantiated annotation");
                }
                checkAnnotationArguments(a, ie);
            }
            else {
                term.addError("annotation constructor must return a newly-instantiated annotation");
            }
        }
    }

    private void checkAnnotationArguments(Functional a, Tree.InvocationExpression ie) {
        Tree.PositionalArgumentList pal = ie.getPositionalArgumentList();
        Tree.NamedArgumentList nal = ie.getNamedArgumentList();
        if (pal!=null) {
            checkPositionalArguments(a, pal.getPositionalArguments());
        }
        if (nal!=null) {
            checkNamedArguments(a, nal);
            if (nal.getSequencedArgument()!=null) {
                nal.getSequencedArgument().addError("illegal annotation argument");
//                checkPositionlArgument(a, nal.getSequencedArgument().getPositionalArguments());
            }
        }
    }

    private void checkNamedArguments(Functional a, Tree.NamedArgumentList nal) {
        for (Tree.NamedArgument na: nal.getNamedArguments()) {
            if (na!=null) {
                if (na instanceof Tree.SpecifiedArgument) {
                    Tree.SpecifierExpression se = ((Tree.SpecifiedArgument) na).getSpecifierExpression();
                    if (se!=null && na.getParameter()!=null) {
                        checkAnnotationArgument(a, se.getExpression(),
                                na.getParameter().getType());
                    }
                }
                else {
                    na.addError("illegal annotation argument");
                }
            }
        }
    }

    private void checkPositionalArguments(Functional a, List<Tree.PositionalArgument> pal) {
        for (Tree.PositionalArgument pa: pal) {
            if (pa!=null && pa.getParameter()!=null) {
                if (pa instanceof Tree.ListedArgument) {
                    checkAnnotationArgument(a, ((Tree.ListedArgument) pa).getExpression(),
                            pa.getParameter().getType());
                }
                else if (pa instanceof Tree.SpreadArgument) {
                    checkAnnotationArgument(a, ((Tree.SpreadArgument) pa).getExpression(),
                            pa.getParameter().getType());
                }
                else {
                    pa.addError("illegal annotation argument");
                }
            }
        }
    }
    
    @Override 
    public void visit(Tree.DocLink that) {
        super.visit(that);
        String text = that.getText();
        int scopeIndex = text.indexOf("::");
        String packageName = scopeIndex<0 ? 
                null : text.substring(0, scopeIndex);
        String path = scopeIndex<0 ? 
                text : text.substring(scopeIndex+2);
        String[] names = path.split("\\.");
        Declaration base = null;
        if (packageName==null) {
            if (names.length > 0) {
                base = that.getScope().getMemberOrParameter(that.getUnit(), 
                    names[0], null, false);
            }
        }
        else {
            Package pack = that.getUnit().getPackage().getModule()
                    .getPackage(packageName);
            if (pack==null) {
                base = null;
                that.addUsageWarning("package does not exist: " + packageName);
            }
            else {
                that.setPkg(pack);
                if (names.length >0) {
                    base = pack.getDirectMember(names[0], null, false);
                }
            }
        }
        if (base==null) {
            that.addUsageWarning("declaration does not exist: " + names[0]);
        }
        else {
            that.setBase(base);
            if (names.length>1) {
                that.setQualified(new ArrayList<Declaration>(names.length-1));
            }
            for (int i=1; i<names.length; i++) {
                if (base instanceof TypeDeclaration) {
                    Declaration qualified = ((TypeDeclaration) base).getMember(names[i], null, false);
                    if (qualified==null) {
                        that.addUsageWarning("member declaration does not exist: " + names[i]);
                        break;
                    }
                    else {
                        that.getQualified().add(qualified);
                        base = qualified;
                    }
                }
                else {
                    that.addUsageWarning("not a type declaration: " + base.getName());
                    break;
                }
            }
        }
    }

    @Override 
    public void visit(Tree.Annotation that) {
        super.visit(that);
        Declaration dec = ((Tree.MemberOrTypeExpression) that.getPrimary()).getDeclaration();
        /*if (dec!=null && !dec.isToplevel()) {
            that.getPrimary().addError("annotation must be a toplevel function reference");
        }*/
        if (dec!=null) {
            if (!dec.isAnnotation()) {
                that.getPrimary().addError("not an annotation constructor");
            }
            else {
                checkAnnotationArguments(null, (Tree.InvocationExpression) that);
            }
        }
    }
    
    private static ProducedType qualifyingType(Declaration d) {
        if (d.isClassOrInterfaceMember()) {
            return ((ClassOrInterface) d.getContainer()).getType();
        }
        else {
            return null;
        }
    }

    private void checkAnnotations(AnnotationList annotationList,
            ProducedType declarationType, ProducedType metatype) {
        Unit unit = annotationList.getUnit();
        List<Annotation> anns = annotationList.getAnnotations();
        for (Annotation ann: anns) {
            ProducedType t = ann.getTypeModel();
            if (t!=null) {
                ProducedType pet = t.getSupertype(unit.getConstrainedAnnotationDeclaration());
                if (pet!=null && pet.getTypeArgumentList().size()>2) {
                    ProducedType ct = pet.getTypeArgumentList().get(2);
                    ProducedType mt = declarationType; //intersectionType(declarationType,metatype, unit);
                    checkAssignable(mt, ct, ann, 
                            "annotated program element does not satisfy annotation constraints");
                }
            }
        }
        for (int i=0; i<anns.size(); i++) {
            ProducedType t = anns.get(i).getTypeModel();
            if (t!=null && t.getSupertype(unit.getOptionalAnnotationDeclaration())!=null) {
                for (int j=0; j<i; j++) {
                    ProducedType ot = anns.get(j).getTypeModel();
                    if (ot!=null && ot.getDeclaration().equals(t.getDeclaration())) {
                        anns.get(i).addError("duplicate annotation: there are multiple annotations of type " + 
                                t.getDeclaration().getName());
                        break;
                    }
                }
            }
        }
    }
    
}
