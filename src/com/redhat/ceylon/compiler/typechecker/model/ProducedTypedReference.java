package com.redhat.ceylon.compiler.typechecker.model;



/**
 * A produced reference to a method or 
 * attribute with actual type arguments.
 * 
 * @author Gavin King
 *
 */
public class ProducedTypedReference extends ProducedReference {
    
    private final boolean covariant;
    private final boolean contravariant;

    ProducedTypedReference(boolean covariant, boolean contravariant) {
        this.covariant = covariant;
        this.contravariant = contravariant;
    }
    
    @Override
    public TypedDeclaration getDeclaration() {
        return (TypedDeclaration) super.getDeclaration();
    }
    
    public ProducedType getType() {
        TypedDeclaration dec = getDeclaration();
        ProducedType type = dec==null ? null : dec.getType();
        if (type==null) {
            return null;
        }
        else {
            ProducedType qt = getQualifyingType();
            if (qt!=null) {
                type = qt.applyVarianceOverrides(type, 
                        covariant, contravariant);
            }
            return type.substitute(getTypeArguments()); //the type arguments to the member
        }
    }
    
}
