package com.redhat.ceylon.compiler.typechecker.model;

import static com.redhat.ceylon.compiler.typechecker.model.ProducedType.depth;
import static com.redhat.ceylon.compiler.typechecker.model.Util.isAbstraction;
import static com.redhat.ceylon.compiler.typechecker.model.Util.producedType;
import static java.util.Collections.emptyMap;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.redhat.ceylon.compiler.typechecker.context.ProducedTypeCache;

/**
 * A produced type or produced reference to a
 * method or attribute
 *
 * @author Gavin King
 */
public abstract class ProducedReference {
    
    ProducedReference() {}

    private Map<TypeParameter, ProducedType> typeArguments = emptyMap();
    private Declaration declaration;
    private ProducedType qualifyingType;
    
    //cache
    private Map<TypeParameter, ProducedType> typeArgumentsWithDefaults;
    
    public ProducedType getQualifyingType() {
        return qualifyingType;
    }
    
    void setQualifyingType(ProducedType qualifyingType) {
        this.qualifyingType = qualifyingType;
    }
    
    public Declaration getDeclaration() {
        return declaration;
    }
    
    void setDeclaration(Declaration declaration) {
        this.declaration = declaration;
    }
    
    public Map<TypeParameter, ProducedType> getTypeArguments() {
        Declaration declaration = getDeclaration();
        if (declaration instanceof Generic) {
            if (typeArgumentsWithDefaults == null ||
                    !ProducedTypeCache.isEnabled()) {
                if (depth.get()>50) {
                    throw new RuntimeException("undecidable default type arguments");
                }
                depth.set(depth.get()+1);
                try {
                    typeArgumentsWithDefaults = 
                            fillInDefaultTypeArguments(declaration,
                                    typeArguments);
                }
                finally { 
                    depth.set(depth.get()-1);
                }
            }
            return typeArgumentsWithDefaults;
        }
        else {
            return typeArguments;
        }
    }

    private static Map<TypeParameter, ProducedType> fillInDefaultTypeArguments(
            Declaration declaration,
            Map<TypeParameter, ProducedType> typeArguments) {
        Map<TypeParameter, ProducedType> result = typeArguments;
        List<TypeParameter> typeParameters = 
                ((Generic) declaration).getTypeParameters();
        for (int i=0, l=typeParameters.size(); i<l; i++) {
            TypeParameter pt = typeParameters.get(i);
            ProducedType dta = pt.getDefaultTypeArgument();
            if (dta!=null) {
                if (!typeArguments.containsKey(pt)) {
                    // only make a copy of typeArguments if required
                    if (typeArguments == result) {
                        // make a copy big enough to fit every type parameter
                        result = new HashMap<TypeParameter,ProducedType>(typeParameters.size());
                        result.putAll(typeArguments);
                    }
                    result.put(pt, dta.substitute(result));
                }
            }
        }
        return result;
    }
    
    void setTypeArguments(Map<TypeParameter,ProducedType> typeArguments) {
        this.typeArguments = typeArguments;
    }
        
    public abstract ProducedType getType();
    
    public ProducedType getFullType() {
    	return getFullType(getType());
    }
    
    /**
     * @param wrappedType the return type of this member for
     *                    a ?. or *. expression, i.e.
     *                    T?, [T*], or [T+]
     */
    public ProducedType getFullType(ProducedType wrappedType) {
        if (declaration instanceof Functional) {
            if (isAbstraction(declaration)) {
                Unit unit = declaration.getUnit();
                //for an unresolved overloaded method we don't 
                //know the parameter types, but we do know that
                //there is only one parameter list
                return producedType(unit.getCallableDeclaration(), 
                        wrappedType,
                        new UnknownType(unit).getType());
            }
            else {
                return getDeclaration().getUnit()
                        .getCallableType(this, wrappedType);
            }
        }
        else {
            return wrappedType;
        }
    }

    public boolean isFunctional() {
        return getDeclaration() instanceof Functional;
    }

    /**
     * Get the type of a parameter, after substitution of
     * type arguments.
     */
    public ProducedTypedReference getTypedParameter(Parameter p) {
        ProducedTypedReference ptr = new ProducedTypedReference(false, true);
        if (p.getModel() != null) {
            ptr.setDeclaration(p.getModel());
        }
        ptr.setQualifyingType(getQualifyingType());
        ptr.setTypeArguments(getTypeArguments());
        return ptr;
    }
    
    public Declaration getOverloadedVersion() {
        return declaration;
    }
    
}
