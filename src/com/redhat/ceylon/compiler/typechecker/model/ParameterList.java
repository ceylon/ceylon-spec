package com.redhat.ceylon.compiler.typechecker.model;

import java.util.ArrayList;
import java.util.List;

public class ParameterList {
    
    private List<Parameter> parameters = new ArrayList<Parameter>(2);
    private boolean supportsNamedParameters = true;
    private boolean supportsPositionalParameters = true;
    private boolean first;
    
    public List<Parameter> getParameters() {
        return parameters;
    }
    
    @Override
    public String toString() {
        return "ParameterList" + parameters.toString();
    }

    public boolean isNamedParametersSupported() {
        return supportsNamedParameters;
    }

    public void setNamedParametersSupported(boolean supportsNamedParameters) {
        this.supportsNamedParameters = supportsNamedParameters;
    }
    
    public boolean isPositionalParametersSupported() {
        return supportsPositionalParameters;
    }

    public void setPositionalParametersSupported(boolean supportsPositionalParameters) {
        this.supportsPositionalParameters = supportsPositionalParameters;
    }

    public boolean hasSequencedParameter() {
        return !parameters.isEmpty() && 
                parameters.get(parameters.size()-1).isSequenced();
    }
    
    public boolean isFirst() {
		return first;
	}
    
    public void setFirst(boolean first) {
		this.first = first;
	}
    
}
