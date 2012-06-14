package com.redhat.ceylon.compiler.typechecker.context;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.antlr.runtime.CommonToken;

import com.redhat.ceylon.compiler.typechecker.analyzer.ControlFlowVisitor;
import com.redhat.ceylon.compiler.typechecker.analyzer.DeclarationVisitor;
import com.redhat.ceylon.compiler.typechecker.analyzer.DependedUponVisitor;
import com.redhat.ceylon.compiler.typechecker.analyzer.ExpressionVisitor;
import com.redhat.ceylon.compiler.typechecker.analyzer.ModuleManager;
import com.redhat.ceylon.compiler.typechecker.analyzer.ModuleVisitor;
import com.redhat.ceylon.compiler.typechecker.analyzer.RefinementVisitor;
import com.redhat.ceylon.compiler.typechecker.analyzer.SelfReferenceVisitor;
import com.redhat.ceylon.compiler.typechecker.analyzer.SpecificationVisitor;
import com.redhat.ceylon.compiler.typechecker.analyzer.TypeArgumentVisitor;
import com.redhat.ceylon.compiler.typechecker.analyzer.TypeHierarchyVisitor;
import com.redhat.ceylon.compiler.typechecker.analyzer.TypeVisitor;
import com.redhat.ceylon.compiler.typechecker.analyzer.ValueVisitor;
import com.redhat.ceylon.compiler.typechecker.io.VirtualFile;
import com.redhat.ceylon.compiler.typechecker.io.impl.Helper;
import com.redhat.ceylon.compiler.typechecker.model.Declaration;
import com.redhat.ceylon.compiler.typechecker.model.Module;
import com.redhat.ceylon.compiler.typechecker.model.Package;
import com.redhat.ceylon.compiler.typechecker.model.Setter;
import com.redhat.ceylon.compiler.typechecker.model.TypeDeclaration;
import com.redhat.ceylon.compiler.typechecker.model.TypedDeclaration;
import com.redhat.ceylon.compiler.typechecker.model.Unit;
import com.redhat.ceylon.compiler.typechecker.tree.Tree;
import com.redhat.ceylon.compiler.typechecker.tree.Validator;
import com.redhat.ceylon.compiler.typechecker.util.AssertionVisitor;
import com.redhat.ceylon.compiler.typechecker.util.PrintVisitor;
import com.redhat.ceylon.compiler.typechecker.util.StatisticsVisitor;
import com.redhat.ceylon.compiler.typechecker.util.UsageVisitor;

/**
 * Represent a unit and each of the type checking phases
 *
 * @author Emmanuel Bernard <emmanuel@hibernate.org>
 */
public class PhasedUnit {
    private Tree.CompilationUnit compilationUnit;
    private Package pkg;
    private Unit unit;
    //must be the non qualified file name
    private String fileName;
    private final ModuleManager moduleManager;
    private final String pathRelativeToSrcDir;
    private VirtualFile unitFile;
    private List<CommonToken> tokens;
    private ModuleVisitor moduleVisitor;
    private VirtualFile srcDir;
    private boolean treeValidated = false;
    private boolean declarationsScanned = false;
    private boolean scanningDeclarations = false;
    private boolean typeDeclarationsScanned = false;
    private boolean refinementValidated = false;
    private boolean flowAnalyzed = false;
    private boolean fullyTyped = false;

    public VirtualFile getSrcDir() {
        return srcDir;
    }

    public PhasedUnit(VirtualFile unitFile, VirtualFile srcDir, Tree.CompilationUnit cu, 
            Package p, ModuleManager moduleManager, Context context, List<CommonToken> tokenStream) {
        this.compilationUnit = cu;
        this.pkg = p;
        this.unitFile = unitFile;
        this.srcDir = srcDir;
        this.fileName = unitFile.getName();
        this.pathRelativeToSrcDir = Helper.computeRelativePath(unitFile, srcDir);
        this.moduleManager = moduleManager;
        this.tokens = tokenStream;
        unit = new Unit();
        unit.setFilename(fileName);
        unit.setPackage(pkg);
        pkg.removeUnit(unit);
        pkg.addUnit(unit);
        cu.setUnit(unit);
    }

    public PhasedUnit(PhasedUnit other) {
        this.compilationUnit = other.compilationUnit;
        this.pkg = other.pkg;
        this.unit = other.unit;
        this.fileName = other.fileName;
        this.moduleManager = other.moduleManager;
        this.pathRelativeToSrcDir = other.pathRelativeToSrcDir;
        this.unitFile = other.unitFile;
        this.tokens = other.tokens;
        this.moduleVisitor = other.moduleVisitor;
        this.srcDir = other.srcDir;
        this.treeValidated = other.treeValidated;
        this.declarationsScanned = other.declarationsScanned;
        this.scanningDeclarations = other.scanningDeclarations;
        this.typeDeclarationsScanned = other.typeDeclarationsScanned;
        this.fullyTyped = other.fullyTyped;
        this.refinementValidated = other.refinementValidated;
        this.fullyTyped = other.fullyTyped;
        this.flowAnalyzed = other.flowAnalyzed;
    }

    @Deprecated
    protected PhasedUnit(VirtualFile unitFile, VirtualFile srcDir, Tree.CompilationUnit cu, 
            Package p, ModuleManager moduleManager, Context context) {
        this(unitFile, srcDir, cu, p, moduleManager, context, null);
    }
    
    public Module visitSrcModulePhase() {
        if ( ModuleManager.MODULE_FILE.equals(fileName) ||
                ModuleManager.PACKAGE_FILE.equals(fileName) ) {
            moduleVisitor = new ModuleVisitor(moduleManager, pkg);
            compilationUnit.visit(moduleVisitor);
            return moduleVisitor.getMainModule();
        }
        return null;
    }

    public void visitRemainingModulePhase() {
        if ( moduleVisitor != null ) {
            moduleVisitor.setPhase(ModuleVisitor.Phase.REMAINING);
            compilationUnit.visit(moduleVisitor);
        }
    }
    
    public boolean isFullyTyped() {
        return fullyTyped;
    }

    public void setFullyTyped(boolean fullyTyped) {
        this.fullyTyped = fullyTyped;
    }
    
    public boolean isFlowAnalyzed() {
        return flowAnalyzed;
    }

    public void setFlowAnalyzed(boolean flowAnalyzed) {
        this.flowAnalyzed = flowAnalyzed;
    }

    public boolean isTreeValidated() {
        return treeValidated;
    }

    public void setTreeValidated(boolean treeValidated) {
        this.treeValidated = treeValidated;
    }

    public boolean isDeclarationsScanned() {
        return declarationsScanned;
    }

    public void setDeclarationsScanned(boolean declarationsScanned) {
        this.declarationsScanned = declarationsScanned;
    }

    public boolean isTypeDeclarationsScanned() {
        return typeDeclarationsScanned;
    }

    public void setTypeDeclarationsScanned(boolean typeDeclarationsScanned) {
        this.typeDeclarationsScanned = typeDeclarationsScanned;
    }

    public boolean isRefinementValidated() {
        return refinementValidated;
    }

    public void setRefinementValidated(boolean refinementValidated) {
        this.refinementValidated = refinementValidated;
    }

    public void validateTree() {
        //System.out.println("Validating tree for " + fileName);
        if (!treeValidated) {
            compilationUnit.visit(new Validator());
            treeValidated = true;
        }
    }

    public void scanDeclarations() {
        if (!declarationsScanned) {
            scanningDeclarations = true;
            //System.out.println("Scan declarations for " + fileName);
            DeclarationVisitor dv = new DeclarationVisitor(pkg, fileName);
            compilationUnit.visit(dv);
            unit = dv.getCompilationUnit();
            declarationsScanned = true;
            scanningDeclarations = false;
        }
    }

    public void scanTypeDeclarations() {
        if (!typeDeclarationsScanned) {
            //System.out.println("Scan type declarations for " + fileName);
            compilationUnit.visit( new TypeVisitor() );
            typeDeclarationsScanned = true;
        }
    }

    public synchronized void validateRefinement() {
        if (! refinementValidated) {
            //System.out.println("Validate member refinement for " + fileName);
            compilationUnit.visit(new RefinementVisitor());
            refinementValidated = true;
        }
    }

    public synchronized void analyseTypes() {
        if (! fullyTyped) {
            //System.out.println("Run analysis phase for " + fileName);
            compilationUnit.visit(new ExpressionVisitor());
            compilationUnit.visit(new TypeArgumentVisitor());
            compilationUnit.visit(new TypeHierarchyVisitor());
            fullyTyped = true;
        }
    }

    public void analyseUsages() {
        compilationUnit.visit(new UsageVisitor());
    }
    
    public void collectUnitDependencies(PhasedUnits phasedUnits, List<PhasedUnits> phasedUnitsOfDependencies) {
        //System.out.println("Run collecting unit dependencies phase for " + fileName);
        compilationUnit.visit(new DependedUponVisitor(this, phasedUnits, phasedUnitsOfDependencies));
    }
    
    public synchronized void analyseFlow() {
        if (! flowAnalyzed) {
            //System.out.println("Validate control flow for " + fileName);
            compilationUnit.visit(new ControlFlowVisitor());
            //System.out.println("Validate self references for " + fileName);
            //System.out.println("Validate specification for " + fileName);
            for (Declaration d: unit.getDeclarations()) {
                compilationUnit.visit(new SpecificationVisitor(d));
                if (d instanceof TypedDeclaration && !(d instanceof Setter)) {
                    compilationUnit.visit(new ValueVisitor((TypedDeclaration) d));
                }
                else if (d instanceof TypeDeclaration) {
                    compilationUnit.visit(new SelfReferenceVisitor((TypeDeclaration) d));
                }
            }
            flowAnalyzed = true;
        }
    }

    public void generateStatistics(StatisticsVisitor statsVisitor) {
        compilationUnit.visit(statsVisitor);
    }
    
    public void runAssertions(AssertionVisitor av) {
        //System.out.println("Running assertions for " + fileName);
        compilationUnit.visit(av);
    }

    public void display() {
        System.out.println("Displaying " + fileName);
        compilationUnit.visit(new PrintVisitor());
    }
    
    public Package getPackage() {
        return pkg;
    }
    
    public Unit getUnit() {
        return unit;
    }

    public List<Declaration> getDeclarations() {
        if (!declarationsScanned) {
            scanDeclarations();
        }
        return unit.getDeclarations();
    }

    public String getPathRelativeToSrcDir() {
        return pathRelativeToSrcDir;
    }

    public VirtualFile getUnitFile() {
        return unitFile;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("PhasedUnit");
        sb.append("{filename=").append(fileName);
        sb.append(", compilationUnit=").append(unit);
        sb.append(", pkg=").append(pkg);
        sb.append('}');
        return sb.toString();
    }

    public Tree.CompilationUnit getCompilationUnit() {
        return compilationUnit;
    }

    public List<CommonToken> getTokens() {
        return tokens;
    }

    public boolean isScanningDeclarations() {
        return scanningDeclarations;
    }
}
