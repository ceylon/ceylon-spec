package com.redhat.ceylon.compiler.typechecker;

import java.io.File;
import java.util.List;

import com.redhat.ceylon.compiler.typechecker.analyzer.ModuleValidator;
import com.redhat.ceylon.compiler.typechecker.context.Context;
import com.redhat.ceylon.compiler.typechecker.context.PhasedUnit;
import com.redhat.ceylon.compiler.typechecker.context.PhasedUnits;
import com.redhat.ceylon.compiler.typechecker.io.VFS;
import com.redhat.ceylon.compiler.typechecker.io.VirtualFile;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.compiler.typechecker.util.AssertionVisitor;
import com.redhat.ceylon.compiler.typechecker.util.StatisticsVisitor;

/**
 * Executes type checking upon construction and retrieve a CompilationUnit object for a given File.
 *
 * @author Emmanuel Bernard <emmanuel@hibernate.org>
 */
//TODO make an interface?
public class TypeChecker {

    private final boolean verbose;
    private final List<VirtualFile> srcDirectories;
    private final Context context;
    private final PhasedUnits phasedUnits;
    private List<PhasedUnits> phasedUnitsOfDependencies;
    private final boolean verifyDependencies;
    private final AssertionVisitor assertionVisitor;
    private final StatisticsVisitor statsVisitor;

    //package level
    TypeChecker(VFS vfs, List<VirtualFile> srcDirectories, boolean verifyDependencies, 
            AssertionVisitor assertionVisitor, boolean verbose) {
        long start = System.nanoTime();
        this.srcDirectories = srcDirectories;
        this.verbose = verbose;
        this.context = new Context(vfs);
        this.phasedUnits = new PhasedUnits(context);
        this.verifyDependencies = verifyDependencies;
        this.assertionVisitor = assertionVisitor;
        statsVisitor = new StatisticsVisitor();
        phasedUnits.parseUnits(srcDirectories);
        long time = System.nanoTime()-start;
        System.out.println("Parsed in " + time/1000000 + " ms");
    }
    
    public PhasedUnits getPhasedUnits() {
        return phasedUnits;
    }
    
    public Context getContext() {
        return context;
    }

    /**
     * Return the CompilationUnit for a given relative path.
     * The path is relative to the source directory
     * eg ceylon/language/Object.ceylon
     */
    public Tree.CompilationUnit getCompilationUnitFromRelativePath(String relativePath) {
        PhasedUnit phasedUnit = phasedUnits.getPhasedUnitFromRelativePath(relativePath);
        if (phasedUnit == null) {
            for (PhasedUnits units : phasedUnitsOfDependencies) {
                phasedUnit = units.getPhasedUnitFromRelativePath(relativePath);
                if (phasedUnit != null) {
                    break;
                }
            }
        }
        return phasedUnit == null ? null : phasedUnit.getCompilationUnit();
    }

    /**
     * Return the CompilationUnit for a given file.
     * May return null of the CompilationUnit has not been parsed.
     */
    public Tree.CompilationUnit getCompilationUnit(File file) {
        final PhasedUnit phasedUnit = phasedUnits.getPhasedUnit( context.getVfs().getFromFile(file) );
        return phasedUnit.getCompilationUnit();
    }

    public void process() throws RuntimeException {
        long start = System.nanoTime();
        executePhases(phasedUnits, false);
        long time = System.nanoTime()-start;
        System.out.println("Type checked in " + time/1000000 + " ms");
    }

    private void executePhases(PhasedUnits phasedUnits, boolean forceSilence) {
        final List<PhasedUnit> listOfUnits = phasedUnits.getPhasedUnits();
        for (PhasedUnit pu : listOfUnits) {
            pu.buildModuleImport();
        }

        final ModuleValidator moduleValidator = new ModuleValidator(context);
        if (verifyDependencies) {
            moduleValidator.verifyModuleDependencyTree();
        }
        phasedUnitsOfDependencies = moduleValidator.getPhasedUnitsOfDependencies();

        for (PhasedUnit pu : listOfUnits) {
            pu.validateTree();
            pu.scanDeclarations();
        }
        for (PhasedUnit pu : listOfUnits) {
            pu.scanTypeDeclarations();
        }
        for (PhasedUnit pu: listOfUnits) {
            pu.validateRefinement();
        }
        for (PhasedUnit pu : listOfUnits) {
            pu.analyseTypes();
        }
        for (PhasedUnit pu: listOfUnits) {
            pu.analyseFlow();
        }

        if (!forceSilence) {
            for (PhasedUnit pu : listOfUnits) {
                if (verbose) {
                    pu.display();
                }
                pu.generateStatistics(statsVisitor);
                pu.runAssertions(assertionVisitor);
            }
            statsVisitor.print();
            assertionVisitor.print();
        }
        
    }
}
