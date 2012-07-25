package com.redhat.ceylon.compiler.typechecker.model;

import java.util.Collections;
import java.util.List;

public class TypeParameter extends TypeDeclaration implements Functional {

    private boolean covariant;
    private boolean contravariant;
    private boolean sequenced;
    private Declaration declaration;
    private ParameterList parameterList;
    private TypeDeclaration selfTypedDeclaration;

    public boolean isInvariant() {
    	return !covariant && !contravariant;
    }
    
    public boolean isCovariant() {
        return covariant;
    }

    public void setCovariant(boolean covariant) {
        this.covariant = covariant;
    }

    public boolean isContravariant() {
        return contravariant;
    }

    public void setContravariant(boolean contravariant) {
        this.contravariant = contravariant;
    }
    
    public boolean isSequenced() {
    	//TODO: the model loader does not yet support
    	//      sequenced type parameters, so hack it
    	//      in here
    	if (getName().equals("CallableArgument") /*&&
    			getDeclaration().getQualifiedNameString()
    			    .equals("ceylon.language.Callable")*/) {
    		return true;
    	}
		return sequenced;
	}
    
    public void setSequenced(boolean sequenced) {
		this.sequenced = sequenced;
	}
    
    @Override
    public boolean isOverloaded() {
    	return false;
    }
    
    public boolean isSelfType() {
        return selfTypedDeclaration!=null;
    }
    
    public TypeDeclaration getSelfTypedDeclaration() {
        return selfTypedDeclaration;
    }
    
    public void setSelfTypedDeclaration(TypeDeclaration selfTypedDeclaration) {
        this.selfTypedDeclaration = selfTypedDeclaration;
    }

    public Declaration getDeclaration() {
        return declaration;
    }

    public void setDeclaration(Declaration declaration) {
        this.declaration = declaration;
    }

    public ParameterList getParameterList() {
        return parameterList;
    }

    public void setParameterList(ParameterList parameterList) {
        this.parameterList = parameterList;
    }

    @Override
    public List<ParameterList> getParameterLists() {
        if (parameterList==null) {
            return Collections.emptyList();
        }
        else {
            return Collections.singletonList(parameterList);
        }
    }

    @Override
    public void addParameterList(ParameterList pl) {
        parameterList = pl;
    }

    @Override
    public String toString() {
        return super.toString().replace("[", 
        		"[" + declaration.getName() + "#");
    }
    
    @Override
    public DeclarationKind getDeclarationKind() {
        return DeclarationKind.TYPE_PARAMETER;
    }
    
    @Override
    public String getQualifiedNameString() {
    	return getName();
    }
    
    @Override
    public boolean isAbstraction() {
        return false;
    }
    
    @Override
    public List<Declaration> getOverloads() {
        return null;
    }
    
    @Override
    public Parameter getParameter(String name) {
        return null;
    }
    
    @Override
    public boolean isDeclaredVoid() {
        return false;
    }

}
