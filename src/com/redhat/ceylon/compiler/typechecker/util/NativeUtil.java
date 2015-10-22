package com.redhat.ceylon.compiler.typechecker.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import com.redhat.ceylon.common.Backend;
import com.redhat.ceylon.common.Backends;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.model.typechecker.model.Declaration;
import com.redhat.ceylon.model.typechecker.model.ModelUtil;

/** Certain convenience methods related to native types.
 * 
 * @author Enrique Zamudio
 */
public class NativeUtil {

    public static List<Tree.Statement> mergeStatements(Tree.Body body, Tree.Declaration header) {
        // And if the header exists we go through the declarations in
        // its body and add them to our list of statements as if they
        // were part of the native implementation when a) it has a
        // default implementation and b) we can't find a matching
        // declaration in our (original) list of statements
        List<Tree.Statement> hdrstmts;
        if (header instanceof Tree.ClassDefinition) {
            hdrstmts = ((Tree.ClassDefinition)header).getClassBody().getStatements();
        } else if (header instanceof Tree.ObjectDefinition) {
            hdrstmts = ((Tree.ObjectDefinition)header).getClassBody().getStatements();
        } else {
            hdrstmts = null;
        }
        List<Tree.Statement> stmts = body.getStatements();
        if (hdrstmts != null && !hdrstmts.isEmpty()) {
            LinkedHashMap<String,Tree.Statement> stmtsmap = new LinkedHashMap<String,Tree.Statement>();
            int idx = 0;
            for (Tree.Statement stmt : stmts) {
                if (stmt instanceof Tree.Declaration) {
                    Tree.Declaration decl = (Tree.Declaration)stmt;
                    Declaration m = decl.getDeclarationModel();
                    String key = m.getClass().getSimpleName() + "#" + m.getName();
                    stmtsmap.put(key, decl);
                } else {
                    stmtsmap.put("#" + (idx++), stmt);
                }
            }
            for (Tree.Statement stmt : hdrstmts) {
                if (stmt instanceof Tree.Declaration) {
                    Tree.Declaration decl = (Tree.Declaration)stmt;
                    Declaration m = decl.getDeclarationModel();
                    if (ModelUtil.isImplemented(m)) {
                        String key = m.getClass().getSimpleName() + "#" + m.getName();
                        if (!stmtsmap.containsKey(key)) {
                            stmtsmap.put(key, decl);
                        }
                    }
                } else {
                    // Headers cannot have statements!
                }
            }
            stmts = new ArrayList<Tree.Statement>(stmtsmap.values());
        }
        return stmts;
    }

    public static boolean isImplemented(Tree.Declaration decl) {
        return ModelUtil.isImplemented(decl.getDeclarationModel());
    }
    
    public static boolean isNative(Tree.Declaration decl) {
        return isNative(decl.getDeclarationModel());
    }
    
    public static boolean isNative(Declaration decl) {
        return decl.isNative();
    }
    
    public static boolean isNativeHeader(Tree.Declaration decl) {
        return isNativeHeader(decl.getDeclarationModel());
    }
    
    public static boolean isNativeHeader(Declaration decl) {
        return decl.isNativeHeader();
    }
    
    public static Backends getNative(Tree.Declaration decl) {
        return getNative(decl.getDeclarationModel());
    }
    
    public static Backends getNative(Declaration decl) {
        return decl.getNativeBackends();
    }
    
    /**
     * Checks that the declaration is marked "native" and has a Ceylon implementation
     * meant for the specified backend
     */
    public static boolean isForBackend(Tree.Declaration decl, Backend backend) {
        return isForBackend(decl.getDeclarationModel(), backend);
    }
    
    /**
     * Checks that the declaration is marked "native" and has a Ceylon implementation
     * meant for the specified backend
     */
    public static boolean isForBackend(Declaration decl, Backend backend) {
        Backends bs = getNative(decl);
        return bs.none() || bs.supports(backend.asSet());
    }
    
    public static boolean isHeaderWithoutBackend(Tree.Declaration decl, Backend backend) {
        return isHeaderWithoutBackend(decl.getDeclarationModel(), backend);
    }
    
    public static boolean isHeaderWithoutBackend(Declaration decl, Backend backend) {
        return decl.isNativeHeader()
                && (ModelUtil.getNativeDeclaration(decl, backend.asSet()) == null);
    }
    
}
