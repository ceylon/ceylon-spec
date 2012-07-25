/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.redhat.ceylon.compiler.typechecker.util;

import com.redhat.ceylon.compiler.typechecker.model.Declaration;
import com.redhat.ceylon.compiler.typechecker.model.Parameter;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.compiler.typechecker.tree.Visitor;

/**
 *
 * @author kulikov
 */
public class UsageVisitor extends Visitor {
    @Override
    public void visit(Tree.ImportMemberOrType that) {
        super.visit(that);
        Declaration d = that.getDeclarationModel();
        if (d != null && d.getRefCount() == 0) {
            that.addUsageWarning(String.format("Import is never used: %s", d.getName()));
        }
    }

    @Override
    public void visit(Tree.Declaration that) {
        super.visit(that);
        Declaration declaration = that.getDeclarationModel();
        if (declaration!=null && 
        		!declaration.isShared() && 
        		!declaration.isToplevel() && 
        		declaration.getRefCount() == 0 &&
        		!(declaration instanceof Parameter) &&
        		!(that instanceof Tree.Variable)) {
            that.addUsageWarning("declaration is never used: " + 
        		    declaration.getName());
        }
    }

}
