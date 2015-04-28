package com.redhat.ceylon.compiler.typechecker.model;

import static com.redhat.ceylon.compiler.typechecker.model.Util.contains;
import static com.redhat.ceylon.compiler.typechecker.model.Util.erase;
import static com.redhat.ceylon.compiler.typechecker.model.Util.isOverloadedVersion;
import static java.util.Collections.emptyList;

import java.util.List;
import java.util.Objects;

/**
 * Represents a named, annotated program element:
 * a class, interface, type parameter, parameter,
 * method, local, or attribute.
 *
 * @author Gavin King
 */
public abstract class Declaration 
        extends Element 
        implements Referenceable, Annotated {

	private String name;
	private String qualifier;
	private boolean shared;
	private boolean formal;
	private boolean actual;
	private boolean deprecated;
	private boolean def;
	private boolean annotation;
    private Scope visibleScope;
    private Declaration refinedDeclaration = this;
    private boolean staticallyImportable;
    private boolean protectedVisibility;
    private boolean packageVisibility;
    private String qualifiedNameAsStringCache;
	private String nativeBackend;
	private boolean otherInstanceAccess;
    private DeclarationCompleter actualCompleter;

    public Scope getVisibleScope() {
        return visibleScope;
    }

    public void setVisibleScope(Scope visibleScope) {
        this.visibleScope = visibleScope;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isShared() {
        return shared;
    }

    public void setShared(boolean shared) {
        this.shared = shared;
    }

    public boolean isParameterized() {
        return false;
    }
    
    public boolean isDeprecated() {
		return deprecated;
	}
    
    public void setDeprecated(boolean deprecated) {
		this.deprecated = deprecated;
	}

    String toStringName() {
        Scope c = getContainer();
        String name = getName();
        if (name==null) name = "";
        if (c instanceof Declaration) {
            return ((Declaration)c).toStringName() + 
                    "." + name;
        }
        else {
            return name;
        }
    }
    
    @Override
    public String toString() {
        return getClass().getSimpleName() + 
                "[" + toStringName() + "]";
    }
    
    @Override
    public String getQualifiedNameString() {
        if(qualifiedNameAsStringCache == null){
            String qualifier = getContainer().getQualifiedNameString();
            String name = getName();
            if (qualifier.isEmpty()) {
                qualifiedNameAsStringCache = name; 
            }
            else if (getContainer() instanceof Package) {
                qualifiedNameAsStringCache = qualifier + "::" + name;
            }
            else {
                qualifiedNameAsStringCache = qualifier + "." + name;
            }
        }
        return qualifiedNameAsStringCache;
    }
    
    public boolean isAnnotation() {
        return annotation;
    }
    
    public void setAnnotation(boolean annotation) {
        this.annotation = annotation;
    }

    public boolean isActual() {
        if (actualCompleter != null) {
            completeActual();
        }
        return actual;
    }

    public void setActual(boolean actual) {
        this.actual = actual;
    }

    public boolean isFormal() {
        return formal;
    }
    
    public void setFormal(boolean formal) {
        this.formal = formal;
    }

    public boolean isNative() {
        return getNative() != null;
    }
    
    public String getNative() {
    	return nativeBackend;
    }
    
    public void setNative(String backend) {
    	this.nativeBackend=backend;
    }

    public boolean isDefault() {
        return def;
    }

    public void setDefault(boolean def) {
        this.def = def;
    }
    
    public Declaration getRefinedDeclaration() {
        if (actualCompleter != null) {
            completeActual();
        }
		return refinedDeclaration;
	}
    
    private void completeActual() {
        DeclarationCompleter completer = actualCompleter;
        actualCompleter = null;
        completer.completeActual(this);
    }

    public void setRefinedDeclaration(Declaration refinedDeclaration) {
		this.refinedDeclaration = refinedDeclaration;
	}
    
    /**
     * Determine if this declaration is visible
     * in the given scope, by considering if it
     * is shared or directly defined in a
     * containing scope.
     */
    public boolean isVisible(Scope scope) {
        Scope vs = getVisibleScope();
        if (vs==null) {
            return true;
        }
        else {
            return contains(vs, scope);
        }
        /*
        * Note that this implementation is not quite
        * right, since for a shared member
        * declaration it does not check if the
        * containing declaration is also visible in
        * the given scope, but this is okay for now
        * because of how this method is used.
        */
        /*if (isShared()) {
            return true;
        }
        else {
            return isDefinedInScope(scope);
        }*/
    }

    /**
     * Determine if this declaration is directly
     * defined in a containing scope of the given
     * scope.
     */
    public boolean isDefinedInScope(Scope scope) {
        while (scope!=null) {
            if (getContainer()==scope) {
                return true;
            }
            scope = scope.getContainer();
        }
        return false;
    }

    public boolean isCaptured() {
        return false;
    }

    public boolean isToplevel() {
        return getContainer() instanceof Package;
    }

    public boolean isClassMember() {
        return getContainer() instanceof Class;
    }

    public boolean isInterfaceMember() {
        return getContainer() instanceof Interface;
    }

    public boolean isClassOrInterfaceMember() {
        return getContainer() instanceof ClassOrInterface;
    }
    
    public boolean isMember() {
    	return false;
    }
    
    @Override
    public List<Annotation> getAnnotations() {
        return emptyList();
    }
    
    public boolean isStaticallyImportable() {
        return staticallyImportable;
    }
    
    public void setStaticallyImportable(boolean staticallyImportable) {
        this.staticallyImportable = staticallyImportable;
    }
    
    public boolean isProtectedVisibility() {
        return protectedVisibility;
    }
    
    public void setProtectedVisibility(boolean protectedVisibility) {
        this.protectedVisibility = protectedVisibility;
    }
    
    public boolean isPackageVisibility() {
        return packageVisibility;
    }
    
    public void setPackageVisibility(boolean packageVisibility) {
        this.packageVisibility = packageVisibility;
    }

    /**
     * Get a produced reference for this declaration
     * by binding explicit or inferred type arguments
     * and type arguments of the type of which this
     * declaration is a member, in the case that this
     * is a member.
     *
     * @param outerType the qualifying produced
     * type or null if this is not a
     * nested type declaration
     * @param typeArguments arguments to the type
     * parameters of this declaration
     */
    public abstract ProducedReference getProducedReference(ProducedType pt,
            List<ProducedType> typeArguments);

    public abstract ProducedReference getReference();
    
    protected java.lang.Class<?> getModelClass() {
        return getClass();
    }
    
    @Override
    public boolean equals(Object object) {
        if (this==object) {
            return true;
        }
        
        if (object == null) {
            return false;
        }
        
        if (object instanceof Declaration) {
            if (this.getModelClass() != ((Declaration) object).getModelClass()) {
                return false;
            }
            Declaration that = (Declaration) object;
            String thisName = getName();
            String thatName = that.getName();
            if (!Objects.equals(getQualifier(), that.getQualifier())) {
                return false;
            }
            Scope thisContainer = getAbstraction(getContainer());
            Scope thatContainer = getAbstraction(that.getContainer());
            if (thisName!=thatName && 
                    (thisName==null || thatName==null || 
                        !thisName.equals(thatName)) ||
                that.getDeclarationKind()!=getDeclarationKind() ||
                thisContainer==null || thatContainer==null ||
                    !thisContainer.equals(thatContainer)) {
                return false;
            }
            else if (this instanceof Functional && 
                    that instanceof Functional) {
                Functional thisFunction = (Functional) this;
                Functional thatFunction = (Functional) that;
                boolean thisIsAbstraction = thisFunction.isAbstraction();
                boolean thatIsAbstraction = thatFunction.isAbstraction();
                boolean thisIsOverloaded = thisFunction.isOverloaded();
                boolean thatIsOverloaded = thatFunction.isOverloaded();
                if (thisIsAbstraction!=thatIsAbstraction ||
                    thisIsOverloaded!=thatIsOverloaded) {
                    return false;
                }
                if (!thisIsOverloaded && !thatIsOverloaded) {
                    return true;
                }
                if (thisIsAbstraction && thatIsAbstraction) {
                    return true;
                }
                List<ParameterList> thisParamLists = thisFunction.getParameterLists();
                List<ParameterList> thatParamLists = thatFunction.getParameterLists();
                if (thisParamLists.size()!=thatParamLists.size()) {
                    return false;
                }
                for (int i=0; i<thisParamLists.size(); i++) {
                    List<Parameter> thisParams = thisParamLists.get(i).getParameters();
                    List<Parameter> thatParams = thatParamLists.get(i).getParameters();
                    if (thisParams.size()!=thatParams.size()) {
                        return false;
                    }
                    for (int j=0; j<thisParams.size(); j++) {
                        Parameter thisParam = thisParams.get(j);
                        Parameter thatParam = thatParams.get(j);
                        if (thisParam!=thatParam) {
                            if (thisParam!=null && thatParam!=null) {
                                ProducedType thisParamType = thisParam.getType();
                                ProducedType thatParamType = thatParam.getType();
                                if (thisParamType!=null && thatParamType!=null) {
                                    if (!erase(thisParamType.getDeclaration())
                                            .equals(erase(thatParamType.getDeclaration()))) {
                                        return false;
                                    }
                                }
                                else if (thisParamType!=thatParamType) {
                                    return false;
                                }
                            }
                            else if (thisParam!=thatParam) {
                                return false;
                            }
                        }
                    }
                }
                return true;
            }
            else {
                return true;
            }
        }
        else {
            return false;
        }
    }

    private Scope getAbstraction(Scope container) {
        if (container instanceof Class && 
                isOverloadedVersion((Class) container)) {
            container = ((Class) container).getExtendedTypeDeclaration();
        }
        return container;
    }
    
    @Override
    public int hashCode() {
        int ret = 17;
        Scope container = getContainer();
        ret = (37 * ret) + (container == null ? 0 : container.hashCode());
        String qualifier = getQualifier();
        ret = (37 * ret) + (qualifier == null ? 0 : qualifier.hashCode());
        String name = getName();
        ret = (37 * ret) + (name == null ? 0 : name.hashCode());
        // make sure we don't consider getter/setter or value/anonymous-type equal
        ret = (37 * ret) + (isSetter() ? 0 : 1);
        ret = (37 * ret) + (isAnonymous() ? 0 : 1);
        return ret;
    }
    
    /**
     * Does this declaration refine the given declaration?
     * @deprecated does not take overloading into account
     */
    public boolean refines(Declaration other) {
        if (equals(other)) {
            return true;
        }
        else {
            if (isClassOrInterfaceMember()) {
                ClassOrInterface type = (ClassOrInterface) getContainer();
                return other.getName()!=null && getName()!=null &&
                        other.getName().equals(getName()) && 
                        isShared() && other.isShared() &&
                        //other.getDeclarationKind()==getDeclarationKind() &&
                        type.isMember(other);
            }
            else {
                return false;
            }
        }
    }
    
    public boolean isAnonymous() {
        return false;
    }
    
    /**
     * Return true if this declaration has a system-generated name, rather than a user-generated name.
     * At the moment only object expressions and function expressions are not named. This is different from
     * isAnonymous() because named object declarations are anonymous but named.
     */
    public boolean isNamed() {
        return true;
    }
    
    /**
     * Return true IFF this is not a real type but a pseudo-type generated by the model loader to pretend that
     * Java enum values have an anonymous type. This is overridden and implemented in Class.
     */
    public boolean isJavaEnum() {
        return false;
    }
    
    public abstract DeclarationKind getDeclarationKind();
    
    @Override
    public String getNameAsString() {
    	return getName();
    }
    
    public String getName(Unit unit) {
    	return unit==null ? getName() : unit.getAliasedName(this);
    }
    
    public boolean getOtherInstanceAccess() {
    	return otherInstanceAccess;
    }

	public void setOtherInstanceAccess(boolean access) {
		otherInstanceAccess=access;
	}
	
	public boolean isParameter() {
	    return false;
	}

    public boolean isSetter() {
        return false;
    }

    public boolean isFunctional() {
        return false;
    }

    protected abstract int hashCodeForCache();

    protected abstract boolean equalsForCache(Object o);

    public String getQualifier() {
        return isParameter() ? null : qualifier;
    }

    public void setQualifier(String qualifier) {
        this.qualifier = qualifier;
    }
    
    public String getPrefixedName(){
        String qualifier = getQualifier();
        return qualifier==null || isParameter() ? name : qualifier + name;
    }

    public boolean sameKind(Declaration m) {
        return m!=null && m.getModelClass()==getModelClass();
    }

    public DeclarationCompleter getActualCompleter() {
        return actualCompleter;
    }

    public void setActualCompleter(DeclarationCompleter actualCompleter) {
        this.actualCompleter = actualCompleter;
    }
}
