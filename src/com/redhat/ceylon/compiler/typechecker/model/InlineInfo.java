package com.redhat.ceylon.compiler.typechecker.model;

import java.util.List;

import com.redhat.ceylon.compiler.typechecker.model.Declaration;
import com.redhat.ceylon.compiler.typechecker.model.Parameter;

public class InlineInfo {

    private Declaration primary;
    private List<InlineArgument> arguments;
    
    public abstract class InlineArgument {
        Parameter targetParameter;

        public Parameter getTargetParameter() {
            return targetParameter;
        }

        public void setTargetParameter(Parameter parameter) {
            this.targetParameter = parameter;
        }
        
    }
    
    public class ParameterArgument extends InlineArgument {
        private Parameter sourceParameter;
        private boolean spread;

        public Parameter getSourceParameter() {
            return sourceParameter;
        }

        public void setSourceParameter(Parameter sourceParameter) {
            this.sourceParameter = sourceParameter;
        }

        public boolean isSpread() {
            return spread;
        }

        public void setSpread(boolean spread) {
            this.spread = spread;
        }
    }
    
    
    public class LiteralArgument extends InlineArgument {
    }

    public Declaration getPrimary() {
        return primary;
    }

    public void setPrimary(Declaration primary) {
        this.primary = primary;
    }

    public List<InlineArgument> getArguments() {
        return arguments;
    }

    public void setArguments(List<InlineArgument> arguments) {
        this.arguments = arguments;
    }
    
}
