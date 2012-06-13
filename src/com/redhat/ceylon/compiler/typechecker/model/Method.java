package com.redhat.ceylon.compiler.typechecker.model;

import static com.redhat.ceylon.compiler.typechecker.model.Util.isNamed;
import static com.redhat.ceylon.compiler.typechecker.model.Util.isParameter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A method. Note that a method must have
 * at least one parameter list.
 *
 * @author Gavin King
 */
public class Method extends MethodOrValue implements Generic, Scope, Functional {

    //private boolean formal;

    private List<TypeParameter> typeParameters = Collections.emptyList();
    private List<ParameterList> parameterLists = new ArrayList<ParameterList>();
    private boolean overloaded;
    private boolean abstraction;
    private List<Declaration> overloads;
    private boolean declaredVoid;

    //list of identifiers declared somewhere and used inside this method
    private List<String> usages = new ArrayList();

    /*public boolean isFormal() {
         return formal;
     }

     public void setFormal(boolean formal) {
         this.formal = formal;
     }*/

    @Override
    public boolean isParameterized() {
        return !typeParameters.isEmpty();
    }

    public List<TypeParameter> getTypeParameters() {
        return typeParameters;
    }

    public void setTypeParameters(List<TypeParameter> typeParameters) {
        this.typeParameters = typeParameters;
    }

    @Override
    public List<ParameterList> getParameterLists() {
        return parameterLists;
    }

    @Override
    public void addParameterList(ParameterList pl) {
        parameterLists.add(pl);
    }

    @Override
    public boolean isOverloaded() {
    	return overloaded;
    }

    public void setOverloaded(boolean overloaded) {
		this.overloaded = overloaded;
	}

    public void setAbstraction(boolean abstraction) {
        this.abstraction = abstraction;
    }

    @Override
    public boolean isAbstraction() {
        return abstraction;
    }

    @Override
    public boolean isDeclaredVoid() {
        return declaredVoid;
    }

    public void setDeclaredVoid(boolean declaredVoid) {
        this.declaredVoid = declaredVoid;
    }

    @Override
    public List<Declaration> getOverloads() {
        return overloads;
    }

    public void setOverloads(List<Declaration> overloads) {
        this.overloads = overloads;
    }

    public Parameter getParameter(String name) {
        for (Declaration d : getMembers()) {
            if (isParameter(d) && isNamed(name, d)) {
                return (Parameter) d;
            }
        }
        return null;
    }

/**
     * Gets the list of identifiers used inside this method.
     *
     * @return list of identifiers.
     */
    public List<String> getUsages() {
        return usages;
    }

    /**
     * Checks usage of the given identifier.
     *
     * @param identifier the identifier to check.
     * @return true if the given identifier is used inside method and false otherwise.
     */
    public boolean isUsed(String identifier) {
        return usages.contains(identifier);
    }
}
