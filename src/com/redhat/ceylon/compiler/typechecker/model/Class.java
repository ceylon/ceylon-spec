package com.redhat.ceylon.compiler.typechecker.model;

import com.redhat.ceylon.compiler.typechecker.model.Util;

import java.util.Collections;
import java.util.List;


public class Class extends ClassOrInterface implements Functional {
    
    private boolean constructors;
    private boolean abstr;
    private ParameterList parameterList;
    private boolean overloaded;
    private boolean abstraction;
    private boolean anonymous;
    private boolean javaEnum;
    private boolean named = true;
    private boolean fin;
    private boolean serializable;
    private List<Declaration> overloads;
    private List<ProducedReference> unimplementedFormals = 
            Collections.<ProducedReference>emptyList();

    public boolean hasConstructors() {
        return constructors;
    }
    
    public void setConstructors(boolean constructors) {
        this.constructors = constructors;
    }
    
    @Override
    public boolean isAnonymous() {
        return anonymous;
    }

    public void setAnonymous(boolean anonymous) {
        this.anonymous = anonymous;
    }

    /**
     * Return true if we have are anonymous and have a name which is not system-generated. Currently
     * only object expressions have no name.
     */
    @Override
    public boolean isNamed() {
        return named;
    }
    
    public void setNamed(boolean named){
        this.named = named;
    }
    
    @Override
    public boolean isAbstract() {
        return abstr;
    }

    public void setAbstract(boolean isAbstract) {
        this.abstr = isAbstract;
    }
    
    public Constructor getDefaultConstructor() {
        if (constructors) {
            for (Declaration dec: getMembers()) {
                if (dec instanceof Constructor &&
                        dec.getName()==null) {
                    return (Constructor) dec;
                }
            }
            return null;
        }
        else {
            return null;
        }
    }

    public ParameterList getParameterList() {
        if (constructors) {
            Constructor defaultConstructor = getDefaultConstructor();
            if (defaultConstructor==null || 
                    defaultConstructor.getParameterLists().isEmpty()) {
                return null;
            }
            else {
                return defaultConstructor.getParameterLists().get(0);
            }
        }
        else {
            return parameterList;
        }
    }

    public void setParameterList(ParameterList parameterList) {
        this.parameterList = parameterList;
    }

    @Override
    public List<ParameterList> getParameterLists() {
        ParameterList parameterList = getParameterList();
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

    public Parameter getParameter(String name) {
        for (Declaration d : getMembers()) {
            if (d.isParameter() && Util.isNamed(name, d)) {
                return ((MethodOrValue)d).getInitializerParameter();
            }
        }
        return null;
    }
    
    @Override
    public boolean isOverloaded() {
    	return overloaded;
    }
    
    public void setOverloaded(boolean overloaded) {
		this.overloaded = overloaded;
	}
    
    @Override
    public Class getExtendedTypeDeclaration() {
        return (Class) super.getExtendedTypeDeclaration();
    }
    
    public void setAbstraction(boolean abstraction) {
        this.abstraction = abstraction;
    }
    
    @Override
    public boolean isAbstraction() {
        return abstraction;
    }
    
    @Override
    public boolean isFinal() {
		return fin||anonymous;
	}
    
    public void setFinal(boolean fin) {
		this.fin = fin;
	}

    @Override
    public List<Declaration> getOverloads() {
        return overloads;
    }
    
    public void setOverloads(List<Declaration> overloads) {
        this.overloads = overloads;
    }
    
    @Override
    public boolean isDeclaredVoid() {
        return false;
    }

    @Override
    public boolean isFunctional() {
        return true;
    }
    
    public List<ProducedReference> getUnimplementedFormals() {
        return unimplementedFormals;
    }
    
    public void setUnimplementedFormals(
            List<ProducedReference> unimplementedFormals) {
        this.unimplementedFormals = unimplementedFormals;
    }

    public boolean isSerializable() {
        return serializable;
    }

    public void setSerializable(boolean serializable) {
        this.serializable = serializable;
    }

    public boolean isJavaEnum() {
        return javaEnum;
    }

    public void setJavaEnum(boolean javaEnum) {
        this.javaEnum = javaEnum;
    }
    

}
