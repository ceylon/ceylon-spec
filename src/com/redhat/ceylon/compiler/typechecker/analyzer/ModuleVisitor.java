package com.redhat.ceylon.compiler.typechecker.analyzer;

import java.util.Arrays;

import com.redhat.ceylon.compiler.typechecker.model.Module;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.compiler.typechecker.tree.Visitor;

/**
 * Detect and populate the list of imports for modules.
 * In theory should only be called on module.ceylon files
 *
 * Put retrictions on how module.ceylon files are built today:
 *  - names and versions must be string literals or else the visitor cannot extract them
 *  - imports must be "explicitly" defined, ie not imported as List<Import> or else the module names cannot be extracted
 *
 * @author Emmanuel Bernard <emmanuel@hibernate.org>
 */
public class ModuleVisitor extends Visitor {
    /**
     * are we truly in a module?
     */
    private boolean isModule = false;
    /**
     * Instance of the visited module which will receive
     * the dependencies declaration
     */
    private Module mainModule;
	private String currentVersion;
	private Module currentModule;
    private final ModuleBuilder moduleBuilder;


    public ModuleVisitor(ModuleBuilder moduleBuilder) {
        this.moduleBuilder = moduleBuilder;
    }

    @Override
    public void visit(Tree.SimpleType that) {
        final Tree.Identifier identifier = that.getIdentifier();
        if ( identifier != null && "Module".equals( identifier.getText() ) ) {
            isModule = true;
        }
        super.visit(that); //is that right to call super after? I have no clue
    }

    @Override
    public void visit(Tree.SpecifiedArgument that) {
        //safety, if the object is not of type Module, ignore
        if (isModule) {
            final Tree.Identifier identifier = that.getIdentifier();
			String currentModuleName = getQuotedIfPresent( "name", that, identifier );			
			if ( currentModuleName != null) {
                final String[] splitModuleName = currentModuleName.split("[\\.]");
                currentModule = moduleBuilder.getOrCreateModule(Arrays.asList(splitModuleName));
                //main module definition
                if ( mainModule == null) {
                    mainModule = currentModule;
					setVersionToCurrentModule();
				}
                else {
                    mainModule.getDependencies().add(currentModule);
                }
            }
            if ( currentVersion == null && (currentModule == null || currentModule.getVersion() == null) ) {
                String currentModuleVersion = getQuotedIfPresent( "version", that, identifier );
                if ( currentModuleVersion != null) {
                    currentVersion = currentModuleVersion;
                    setVersionToCurrentModule();
                }
            }
        }
        super.visit(that); //is that right to call super after? I have no clue
    }

	private void setVersionToCurrentModule() {
		if ( currentModule != null && currentVersion != null ) {
		    currentModule.setVersion(currentVersion);
			currentVersion = null;
		}
	}

	private String getQuotedIfPresent(String quoted, Tree.SpecifiedArgument that, Tree.Identifier identifier) {
		String currentModuleName = null;
		if ( identifier != null && quoted.equals( identifier.getText() ) ) {
			final Tree.SpecifierExpression specifierExpression = that.getSpecifierExpression();
			final Tree.Expression expression =
					specifierExpression != null ? specifierExpression.getExpression() : null;
			final Tree.Term term = expression != null ? expression.getTerm() : null;
			currentModuleName = term != null ? term.getText() : null;
			if ( currentModuleName == null ) {
				that.getErrors().add( new AnalysisError(that, "Malformed module.ceylon: cannot extract module " + quoted) );
			}
			else if ( !currentModuleName.startsWith("'") || !currentModuleName.endsWith("'") ) {
				that.getErrors().add( new AnalysisError(that, "Malformed module.ceylon: module " + quoted + " must be a quoted identifier") );
			}
		}
		return currentModuleName != null ? currentModuleName.substring(1, currentModuleName.length() - 1) : null;
	}
}

