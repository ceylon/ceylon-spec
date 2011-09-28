package com.redhat.ceylon.compiler.typechecker.analyzer;

import static com.redhat.ceylon.compiler.typechecker.analyzer.Util.checkTypeBelongsToContainingScope;
import static com.redhat.ceylon.compiler.typechecker.analyzer.Util.getBaseDeclaration;
import static com.redhat.ceylon.compiler.typechecker.analyzer.Util.getTypeArguments;
import static com.redhat.ceylon.compiler.typechecker.model.Util.addToIntersection;
import static com.redhat.ceylon.compiler.typechecker.model.Util.addToUnion;
import static com.redhat.ceylon.compiler.typechecker.model.Util.getContainingClassOrInterface;
import static com.redhat.ceylon.compiler.typechecker.tree.Util.name;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.redhat.ceylon.compiler.typechecker.model.Class;
import com.redhat.ceylon.compiler.typechecker.model.ClassOrInterface;
import com.redhat.ceylon.compiler.typechecker.model.Declaration;
import com.redhat.ceylon.compiler.typechecker.model.Import;
import com.redhat.ceylon.compiler.typechecker.model.ImportList;
import com.redhat.ceylon.compiler.typechecker.model.Interface;
import com.redhat.ceylon.compiler.typechecker.model.IntersectionType;
import com.redhat.ceylon.compiler.typechecker.model.Module;
import com.redhat.ceylon.compiler.typechecker.model.Package;
import com.redhat.ceylon.compiler.typechecker.model.ProducedType;
import com.redhat.ceylon.compiler.typechecker.model.TypeDeclaration;
import com.redhat.ceylon.compiler.typechecker.model.TypeParameter;
import com.redhat.ceylon.compiler.typechecker.model.TypedDeclaration;
import com.redhat.ceylon.compiler.typechecker.model.UnionType;
import com.redhat.ceylon.compiler.typechecker.model.Unit;
import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.ImportMemberOrTypeList;
import com.redhat.ceylon.compiler.typechecker.tree.Visitor;
import com.redhat.ceylon.compiler.typechecker.util.PrintUtil;

/**
 * Second phase of type analysis.
 * Scan the compilation unit looking for literal type 
 * declarations and maps them to the associated model 
 * objects. Also builds up a list of imports for the 
 * compilation unit. Finally, assigns types to the 
 * associated model objects of declarations declared 
 * using an explicit type (this must be done in
 * this phase, since shared declarations may be used
 * out of order in expressions).
 * 
 * @author Gavin King
 *
 */
public class TypeVisitor extends Visitor {
    
    private Unit unit;
            
    @Override public void visit(Tree.CompilationUnit that) {
        unit = that.getUnit();
        super.visit(that);
    }
        
    @Override
    public void visit(Tree.Import that) {
        Package importedPackage = getPackage(that.getImportPath());
        if (importedPackage!=null) {
            ImportList il = (ImportList) that.getScope();
            il.setImportedPackage(importedPackage);
            that.setImportList(il);
            Set<String> names = new HashSet<String>();
            ImportMemberOrTypeList imtl = that.getImportMemberOrTypeList();
            if (imtl!=null) {
                for (Tree.ImportMemberOrType member: imtl
                        .getImportMemberOrTypes()) {
                    String name = importMember(member, importedPackage, il);
                    names.add(name);
                }
                if (imtl.getImportWildcard()!=null) {
                    importAllMembers(importedPackage, names, il);
                }
            }
        }
    }

    private void importAllMembers(Package importedPackage, Set<String> ignoredMembers, ImportList il) {
        for (Declaration dec: importedPackage.getMembers()) {
            if (dec.isShared() && !ignoredMembers.contains(dec.getName())) {
                Import i = new Import();
                i.setAlias(dec.getName());
                i.setDeclaration(dec);
                unit.getImports().add(i);
                il.getImports().add(i);
            }
        }
    }

    private Package getPackage(Tree.ImportPath path) {
        if (path!=null && !path.getIdentifiers().isEmpty()) {
            Module module = unit.getPackage().getModule();
            for (Package pkg: module.getAllPackages()) {
                if ( hasName(path.getIdentifiers(), pkg) ) {
                    return pkg;
                }
            }
            path.addError("package not found: " + 
                    PrintUtil.importNodeToString(path.getIdentifiers()));
        }
        return null;
    }

    private boolean hasName(List<Tree.Identifier> importPath, Package mp) {
        if (mp.getName().size()==importPath.size()) {
            for (int i=0; i<mp.getName().size(); i++) {
                if (!mp.getName().get(i).equals(name(importPath.get(i)))) {
                    return false;
                }
            }
            return true;
        }
        else {
            return false;
        }
    }
    
    private String importMember(Tree.ImportMemberOrType member, Package importedPackage, ImportList il) {
        Import i = new Import();
        Tree.Alias alias = member.getAlias();
        String name = name(member.getIdentifier());
        if (alias==null) {
            i.setAlias(name);
        }
        else {
            i.setAlias(name(alias.getIdentifier()));
        }
        Declaration d = importedPackage.getMember(name);
        if (d==null) {
            member.getIdentifier().addError("imported declaration not found: " + 
                    name);
        }
        else {
            if (!d.isShared()) {
                member.getIdentifier().addError("imported declaration is not shared: " +
                        name);
            }
            i.setDeclaration(d);
            member.setDeclarationModel(d);
            unit.getImports().add(i);
            il.getImports().add(i);
        }
        return name;
    }
        
    @Override 
    public void visit(Tree.UnionType that) {
        super.visit(that);
        UnionType ut = new UnionType(unit);
        List<ProducedType> types = new ArrayList<ProducedType>();
        for (Tree.StaticType st: that.getStaticTypes()) {
            addToUnion( types, st.getTypeModel() );
        }
        ut.setCaseTypes(types);
        ProducedType pt = ut.getType();
        that.setTypeModel(pt);
        //that.setTarget(pt);
    }

    @Override 
    public void visit(Tree.IntersectionType that) {
        super.visit(that);
        IntersectionType it = new IntersectionType(unit);
        List<ProducedType> types = new ArrayList<ProducedType>();
        for (Tree.StaticType st: that.getStaticTypes()) {
            addToIntersection( types, st.getTypeModel() );
        }
        it.setSatisfiedTypes(types);
        ProducedType pt = it.getType();
        that.setTypeModel(pt);
        //that.setTarget(pt);
    }

    @Override 
    public void visit(Tree.BaseType that) {
        super.visit(that);
        TypeDeclaration type = getBaseDeclaration(that);
        if (type==null) {
            that.addError("type declaration not found: " + 
                    name(that.getIdentifier()), 100);
            unit.getUnresolvedReferences().add(that.getIdentifier());
        }
        else {
            ProducedType outerType = that.getScope().getDeclaringType(type);
            visitSimpleType(that, outerType, type);
        }
    }

    public void visit(Tree.SuperType that) {
        //if (inExtendsClause) { //can't appear anywhere else in the tree!
            ClassOrInterface ci = getContainingClassOrInterface(that.getScope());
            if (ci!=null) {
                if (ci.isClassOrInterfaceMember()) {
                    ClassOrInterface s = (ClassOrInterface) ci.getContainer();
                    ProducedType t = s.getExtendedType();
                    //TODO: type arguments
                    that.setTypeModel(t);
                }
                else {
                    that.addError("super appears in extends for non-member class");
                }
            }
        //}
    }

    public void visit(Tree.QualifiedType that) {
        super.visit(that);
        ProducedType pt = that.getOuterType().getTypeModel();
        if (pt!=null) {
            TypeDeclaration type = (TypeDeclaration) pt.getDeclaration()
                        .getMember(name(that.getIdentifier()));
            if (type==null) {
                that.addError("member type declaration not found: " + 
                        name(that.getIdentifier()), 100);
                unit.getUnresolvedReferences().add(that.getIdentifier());
            }
            else {
                if (!type.isVisible(that.getScope())) {
                    that.addError("member type is not visible: " +
                            name(that.getIdentifier()));
                }
                visitSimpleType(that, pt, type);
            }
        }
    }

    private void visitSimpleType(Tree.SimpleType that, ProducedType ot, TypeDeclaration dec) {
        Tree.TypeArgumentList tal = that.getTypeArgumentList();
        List<ProducedType> ta = getTypeArguments(tal);
        if (tal!=null) tal.setTypeModels(ta);
        //if (acceptsTypeArguments(dec, ta, tal, that)) {
            ProducedType pt = dec.getProducedType(ot, ta);
            that.setTypeModel(pt);
            that.setDeclarationModel(dec);
        //}
    }
    
    /*private void visitExtendedType(Tree.QualifiedType that, ProducedType ot, TypeDeclaration dec) {
        Tree.TypeArgumentList tal = that.getTypeArgumentList();
        List<ProducedType> typeArguments = getTypeArguments(tal);
        //if (acceptsTypeArguments(dec, typeArguments, tal, that)) {
            ProducedType pt = dec.getProducedType(ot, typeArguments);
            that.setTypeModel(pt);
        //}
    }*/
        
    @Override 
    public void visit(Tree.VoidModifier that) {
        that.setTypeModel(unit.getVoidDeclaration().getType());
    }

    public void visit(Tree.SequencedType that) {
        super.visit(that);
        ProducedType type = that.getType().getTypeModel();
        if (type!=null) {
            that.setTypeModel(unit.getEmptyType(unit.getSequenceType(type)));
        }
    }

    @Override 
    public void visit(Tree.TypedDeclaration that) {
        super.visit(that);
        setType(that, that.getType(), that.getDeclarationModel());
    }

    @Override 
    public void visit(Tree.TypedArgument that) {
        super.visit(that);
        setType(that, that.getType(), that.getDeclarationModel());
    }
        
    private void setType(Node that, Tree.Type type, TypedDeclaration td) {
        if (type==null) {
            that.addError("missing type of declaration: " + td.getName());
        }
        else if (!(type instanceof Tree.LocalModifier)) { //if the type declaration is missing, we do type inference later
            ProducedType t = type.getTypeModel();
            if (t==null) {
                //TODO: this case is temporary until we
                //      add support for sequenced parameters
            }
            else {
                td.setType(t);
            }
        }
    }
    
    private void defaultSuperclass(Tree.ExtendedType et, TypeDeclaration c) {
        if (et==null) {
            //TODO: should be BaseObject, according to the spec!
            c.setExtendedType(unit.getIdentifiableObjectDeclaration().getType());
        }
    }

    @Override 
    public void visit(Tree.ObjectDefinition that) {
        defaultSuperclass(that.getExtendedType(), 
                that.getDeclarationModel().getTypeDeclaration());
        super.visit(that);
    }

    @Override 
    public void visit(Tree.ObjectArgument that) {
        defaultSuperclass(that.getExtendedType(), 
                that.getDeclarationModel().getTypeDeclaration());
        super.visit(that);
    }

    @Override 
    public void visit(Tree.ClassDefinition that) {
        Class c = that.getDeclarationModel();
        Class vd = unit.getVoidDeclaration();
        if (c!=vd) {
            defaultSuperclass(that.getExtendedType(), c);
        }
        super.visit(that);
    }

    @Override 
    public void visit(Tree.InterfaceDefinition that) {
        that.getDeclarationModel().setExtendedType(unit.getObjectDeclaration().getType());
        super.visit(that);
    }

    @Override 
    public void visit(Tree.TypeParameterDeclaration that) {
        that.getDeclarationModel().setExtendedType(unit.getVoidDeclaration().getType());
        super.visit(that);
    }
    
    @Override 
    public void visit(Tree.ClassDeclaration that) {
        super.visit(that);
        if (that.getTypeSpecifier()==null) {
            that.addError("missing class body or aliased class reference");
        }
        else {
            Tree.SimpleType et = that.getTypeSpecifier().getType();
            if (et==null) {
                that.addError("malformed aliased class");
            }
            else {
                ProducedType type = et.getTypeModel();
                if (type!=null) {
                    if (!(type.getDeclaration() instanceof Class)) {
                        et.addError("not a class: " + 
                                type.getDeclaration().getName());
                    }
                    that.getDeclarationModel().setExtendedType(type);
                }
            }
        }
    }
    
    @Override 
    public void visit(Tree.InterfaceDeclaration that) {
        super.visit(that);
        if (that.getTypeSpecifier()==null) {
            that.addError("missing interface body or aliased interface reference");
        }
        else {
            Tree.SimpleType et = that.getTypeSpecifier().getType();
            if (et==null) {
                that.addError("malformed aliased interface");
            }
            else {
                ProducedType type = et.getTypeModel();
                if (type!=null) {
                    if (!(type.getDeclaration() instanceof Interface)) {
                        et.addError("not an interface: " + 
                                type.getDeclaration().getName());
                    }
                    that.getDeclarationModel().setExtendedType(type);
                }
            }
        }
    }
    
    @Override
    public void visit(Tree.MethodDeclaration that) {
        super.visit(that);
        if (that.getSpecifierExpression()==null
                && that.getType() instanceof Tree.FunctionModifier) {
            that.getType().addError("method must specify an explicit return type or definition");
        }
    }
    
    @Override
    public void visit(Tree.AttributeDeclaration that) {
        super.visit(that);
        if (that.getSpecifierOrInitializerExpression()==null
                && that.getType() instanceof Tree.ValueModifier) {
            that.getType().addError("attribute must specify an explicit type or definition", 200);
        }
    }
    
    @Override 
    public void visit(Tree.ExtendedType that) {
        super.visit(that);
        TypeDeclaration td = (TypeDeclaration) that.getScope();
        Tree.SimpleType et = that.getType();
        if (et==null) {
            that.addError("malformed extended type");
        }
        else {
            /*if (et instanceof Tree.QualifiedType) {
                Tree.QualifiedType st = (Tree.QualifiedType) et;
                ProducedType pt = st.getOuterType().getTypeModel();
                if (pt!=null) {
                    TypeDeclaration superclass = (TypeDeclaration) getMemberDeclaration(pt.getDeclaration(), st.getIdentifier(), context);
                    if (superclass==null) {
                        that.addError("member type declaration not found: " + 
                                st.getIdentifier().getText());
                    }
                    else {
                        visitExtendedType(st, pt, superclass);
                    }
                }
            }*/
            ProducedType type = et.getTypeModel();
            if (type!=null) {
                if (et instanceof Tree.QualifiedType) {
                    if ( !(((Tree.QualifiedType) et).getOuterType() instanceof Tree.SuperType) ) {
                        checkTypeBelongsToContainingScope(type, td.getContainer(), et);
                    }
                }
                if (that.getInvocationExpression()!=null) {
                    Tree.Primary pr = that.getInvocationExpression().getPrimary();
                    if (pr instanceof Tree.ExtendedTypeExpression) {
                        pr.setTypeModel(type);
                        pr.setDeclaration(type.getDeclaration());
                        ( (Tree.ExtendedTypeExpression) pr).setTarget(type);
                    }
                }
                if (type.getDeclaration() instanceof TypeParameter) {
                    et.addError("directly extends a type parameter: " + 
                            type.getProducedTypeName());
                }
                else if (type.getDeclaration() instanceof Interface) {
                    et.addError("extends an interface: " + 
                            type.getProducedTypeName());
                }
                else {
                    td.setExtendedType(type);
                }
            }
        }
    }
    
    @Override 
    public void visit(Tree.SatisfiedTypes that) {
        super.visit(that);
        TypeDeclaration td = (TypeDeclaration) that.getScope();
        List<ProducedType> list = new ArrayList<ProducedType>();
        if ( that.getTypes().isEmpty() ) {
            that.addError("missing types in satisfies");
        }
        for (Tree.StaticType t: that.getTypes()) {
            ProducedType type = t.getTypeModel();
            if (type!=null) {
                if (!(td instanceof TypeParameter)) {
                    if (type.getDeclaration() instanceof TypeParameter) {
                        t.addError("directly satisfies type parameter: " + 
                                type.getProducedTypeName());
                        continue;
                    }
                    if (type.getDeclaration() instanceof Class) {
                        t.addError("satisfies a class: " + 
                                type.getProducedTypeName());
                        continue;
                    }
                    if (t instanceof Tree.QualifiedType) {
                        checkTypeBelongsToContainingScope(type, td.getContainer(), t);
                    }
                }
                list.add(type);
            }
        }
        td.setSatisfiedTypes(list);
    }

    
    
    /*@Override 
    public void visit(Tree.TypeConstraint that) {
        super.visit(that);
        if (that.getSelfType()!=null) {
            TypeDeclaration td = (TypeDeclaration) that.getSelfType().getScope();
            TypeParameter tp = that.getDeclarationModel();
            td.setSelfType(tp.getType());
            if (tp.isSelfType()) {
                that.addError("type parameter may not act as self type for two different types");
            }
            else {
                tp.setSelfTypedDeclaration(td);
            }
        }
    }*/

    @Override 
    public void visit(Tree.CaseTypes that) {
        super.visit(that);
        if (that.getTypes()!=null) {
            for (Tree.SimpleType st: that.getTypes()) {
                ProducedType stt = st.getTypeModel();
				if (stt!=null && stt.getDeclaration() instanceof TypeParameter) {
                    TypeDeclaration td = (TypeDeclaration) that.getScope();
                    if (!(td instanceof TypeParameter)) {
                        TypeParameter tp = (TypeParameter) stt.getDeclaration();
                        td.setSelfType(stt);
                        if (tp.isSelfType()) {
                            st.addError("type parameter may not act as self type for two different types");
                        }
                        else {
                            tp.setSelfTypedDeclaration(td);
                        }
                        if (that.getTypes().size()>1) {
                            st.addError("a type may not have more than one self type");
                        }
                    }
                }
            }
        }
    }
    
}
