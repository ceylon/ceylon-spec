
package com.redhat.ceylon.compiler.typechecker.util;

import com.redhat.ceylon.compiler.typechecker.model.Declaration;
import com.redhat.ceylon.compiler.typechecker.model.Method;
import com.redhat.ceylon.compiler.typechecker.model.Value;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.compiler.typechecker.tree.Visitor;
import java.util.List;

/**
 * Performs analysis of the usage of private attributes and methods.
 *
 * @author kulikov
 */
public class UsageVisitor extends Visitor {
@Override
    public void visit(Tree.AnyAttribute attribute) {
        super.visit(attribute);

        //Do not check usage of the shared attribute
        if (attribute.getDeclarationModel().isShared()) {
            return;
        }

        String name = attribute.getDeclarationModel().getName();
        Value v = (Value) attribute.getDeclarationModel();

        if (v.getContainer() instanceof com.redhat.ceylon.compiler.typechecker.model.Class) {
            if (!isUsed((com.redhat.ceylon.compiler.typechecker.model.Class)v.getContainer(), name)) {
                attribute.addWarning(String.format("Attribute %s declared but never used", name));
            }
        } else if (v.getContainer() instanceof Method) {
            if (!isUsed((Method)v.getContainer(), name)) {
                attribute.addWarning(String.format("Attribute %s declared but never used", name));
            }
        }
    }

    @Override
    public void visit(Tree.AnyMethod method) {
        super.visit(method);

        //do not check method usage if it is shared
        if (method.getDeclarationModel().isShared()) {
            return;
        }

        String name = method.getDeclarationModel().getName();
        if (!isUsed((com.redhat.ceylon.compiler.typechecker.model.Class)method.getDeclarationModel().getContainer(), name)) {
            method.addWarning(String.format("Method %s declared but never used", name));
        }

    }

    private boolean isUsed(com.redhat.ceylon.compiler.typechecker.model.Class cls, String identifier) {
        List<Declaration> members = cls.getMembers();

        for (Declaration d : members) {
            if (d instanceof Method) {
                if (isUsed((Method)d, identifier)) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean isUsed(Method method, String identifier) {
        return method.isUsed(identifier);
    }
}
