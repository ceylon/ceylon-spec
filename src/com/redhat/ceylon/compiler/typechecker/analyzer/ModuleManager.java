package com.redhat.ceylon.compiler.typechecker.analyzer;

import static com.redhat.ceylon.compiler.typechecker.model.Util.formatPath;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.WeakHashMap;

import com.redhat.ceylon.cmr.api.ArtifactContext;
import com.redhat.ceylon.cmr.api.ArtifactResult;
import com.redhat.ceylon.cmr.api.RepositoryManager;
import com.redhat.ceylon.common.ModuleUtil;
import com.redhat.ceylon.compiler.typechecker.TypeChecker;
import com.redhat.ceylon.compiler.typechecker.context.Context;
import com.redhat.ceylon.compiler.typechecker.context.PhasedUnits;
import com.redhat.ceylon.compiler.typechecker.io.ClosableVirtualFile;
import com.redhat.ceylon.compiler.typechecker.model.Module;
import com.redhat.ceylon.compiler.typechecker.model.ModuleImport;
import com.redhat.ceylon.compiler.typechecker.model.Modules;
import com.redhat.ceylon.compiler.typechecker.model.Package;
import com.redhat.ceylon.compiler.typechecker.tree.Node;
import com.redhat.ceylon.compiler.typechecker.tree.Tree.ModuleDescriptor;

/**
 * Manager modules and packages (build, retrieve, handle errors etc)
 *
 * @author Emmanuel Bernard <emmanuel@hibernate.org>
 */
public class ModuleManager {
    public static class ModuleDependencyAnalysisError extends AnalysisError {
    
        public ModuleDependencyAnalysisError(Node treeNode, String message, int code) {
            super(treeNode, message, code);
        }
        
        public ModuleDependencyAnalysisError(Node treeNode, String message) {
            super(treeNode, message);
        }
    }

    public static final String MODULE_FILE = "module.ceylon";
    public static final String PACKAGE_FILE = "package.ceylon";
    private final Context context;
    private final LinkedList<Package> packageStack = new LinkedList<Package>();
    private Module currentModule;
    private Modules modules;
    private static Object PRESENT = new Object();
    private final Map<ModuleImport,WeakHashMap<Node, Object>> moduleImportToNode = new HashMap<ModuleImport, WeakHashMap<Node, Object>>();
    private Map<List<String>, Set<String>> topLevelErrorsPerModuleName = new HashMap<List<String>,Set<String>>();
    private Map<Module, Node> moduleToNode = new TreeMap<Module, Node>();

    public ModuleManager(Context context) {
        this.context = context;
    }
    
    protected Package createPackage(String pkgName, Module module) {
        final Package pkg = new Package();
        List<String> name = pkgName.isEmpty() ? Arrays.asList("") : splitModuleName(pkgName); 
        pkg.setName(name);
        if (module != null) {
            module.getPackages().add(pkg);
            pkg.setModule(module);
        }
        return pkg;
    }

    public void initCoreModules() {
        packageStack.clear();
        currentModule = null;
        
        modules = context.getModules();
        if ( modules == null ) {
            modules = new Modules();
            context.setModules(modules);
            //build empty package
            final Package emptyPackage = createPackage("", null);
            packageStack.addLast(emptyPackage);

            //build default module (module in which packages belong to when not explicitly under a module
            final List<String> defaultModuleName = Collections.singletonList(Module.DEFAULT_MODULE_NAME);
            final Module defaultModule = createModule(defaultModuleName, "unversioned");
            defaultModule.setDefault(true);
            defaultModule.setAvailable(true);
            bindPackageToModule(emptyPackage, defaultModule);
            modules.getListOfModules().add(defaultModule);
            modules.setDefaultModule(defaultModule);

            //create language module and add it as a dependency of defaultModule
            //since packages outside a module cannot declare dependencies
            final List<String> languageName = Arrays.asList("ceylon", "language");
            Module languageModule = createModule(languageName, TypeChecker.LANGUAGE_MODULE_VERSION);
            languageModule.setLanguageModule(languageModule);
            languageModule.setAvailable(false); //not available yet
            modules.setLanguageModule(languageModule);
            modules.getListOfModules().add(languageModule);
            defaultModule.addImport(new ModuleImport(languageModule, false, false));
            defaultModule.setLanguageModule(languageModule);
        }
        else {
            modules = context.getModules();
            packageStack.addLast( modules.getDefaultModule().getPackages().get(0) );
        }
    }

    protected Module createModule(List<String> moduleName, String version) {
		Module module = new Module();
		module.setName(moduleName);
		module.setVersion(version);
		return module;
	}

	public void push(String path) {
        createPackageAndAddToModule(path);
    }

    public void pop() {
        removeLastPackageAndModuleIfNecessary();
    }

    public Package getCurrentPackage() {
        return packageStack.peekLast();
    }

    /**
     * Get or create a module.
     * version == null is considered equal to any version.
     * Likewise a module with no version will match any version passed
     */
    public Module getOrCreateModule(List<String> moduleName, String version) {
        if (moduleName.size() == 0) {
            return null;
        }
        Module module = null;
        final Set<Module> moduleList = context.getModules().getListOfModules();
        for (Module current : moduleList) {
            final List<String> names = current.getName();
            if (moduleName.equals(names)
                    && compareVersions(current, version, current.getVersion())) {
                module = current;
                break;
            }
        }
        if (module == null) {
            module = createModule(moduleName, version);
            module.setLanguageModule(modules.getLanguageModule());
            moduleList.add(module);
        }
        return module;
    }

    protected boolean compareVersions(Module current, String version, String currentVersion) {
        return currentVersion == null || version == null || currentVersion.equals(version);
    }

    public void visitModuleFile() {
        if ( currentModule == null ) {
            final Package currentPkg = packageStack.peekLast();
            final List<String> moduleName = currentPkg.getName();
            //we don't know the version at this stage, will be filled later
            currentModule = getOrCreateModule(moduleName, null);
            if ( currentModule != null ) {
                currentModule.setAvailable(true); // TODO : not necessary anymore ? the phasedUnit will be added. And the buildModuleImport()
                                                  //        function (which calls module.setAvailable()) will be called by the typeChecker
                                                  //        BEFORE the ModuleValidator.verifyModuleDependencyTree() call that uses 
                                                  //        isAvailable()
                bindPackageToModule(currentPkg, currentModule);
            }
            else {
                addErrorToModule(new ArrayList<String>(), 
                        "module may not be defined at the top level of the hierarchy");
            }
        }
        else {
            StringBuilder error = new StringBuilder("two modules within the same hierarchy: '");
            error.append( formatPath( currentModule.getName() ) )
                .append( "' and '" )
                .append( formatPath( packageStack.peekLast().getName() ) )
                .append("'");
            addErrorToModule(currentModule.getName(), error.toString());
            addErrorToModule(packageStack.peekLast().getName(), error.toString());
        }
    }

    private void createPackageAndAddToModule(String path) {
        final Package lastPkg = packageStack.peekLast();
        List<String> parentName = lastPkg.getName();
        final ArrayList<String> name = new ArrayList<String>(parentName.size() + 1);
        name.addAll(parentName);
        name.add(path);
        
        Package pkg = createPackage(formatPath(name), 
                currentModule != null ? currentModule : modules.getDefaultModule());
        packageStack.addLast(pkg);
    }

    private void removeLastPackageAndModuleIfNecessary() {
        packageStack.pollLast();
        final boolean moveAboveModuleLevel = currentModule != null
                && currentModule.getName().size() > packageStack.size() -1; //first package is the empty package
        if (moveAboveModuleLevel) {
            currentModule = null;
        }
    }

    private void bindPackageToModule(Package pkg, Module module) {
        //undo nomodule setting if necessary
        if (pkg.getModule() != null) {
            pkg.getModule().getPackages().remove(pkg);
            pkg.setModule(null);
        }
        module.getPackages().add(pkg);
        pkg.setModule(module);
    }

    public void addModuleDependencyDefinition(ModuleImport moduleImport, Node definition) {
        WeakHashMap<Node, Object> moduleDepDefinition = moduleImportToNode.get(moduleImport);
        if (moduleDepDefinition == null) {
            moduleDepDefinition = new WeakHashMap<Node, Object>();
            moduleImportToNode.put(moduleImport, moduleDepDefinition);
        }
        moduleDepDefinition.put(definition, PRESENT);
    }

    public void attachErrorToDependencyDeclaration(ModuleImport moduleImport, List<Module> dependencyTree, String error) {
        if (!attachErrorToDependencyDeclaration(moduleImport, error)) {
            //This probably can happen if the missing dependency is found deep in the dependency structure (ie the binary version of a module)
            // in theory the first item in the dependency tree is the compiled module, and the second one is the import we have to add
            // the error to
            if(dependencyTree.size() >= 2){
                Module rootModule = dependencyTree.get(0);
                Module originalImportedModule = dependencyTree.get(1);
                // find the original import
                for(ModuleImport imp : rootModule.getImports()){
                    if(imp.getModule() == originalImportedModule){
                        // found it, try to attach the error
                        if(attachErrorToDependencyDeclaration(imp, error)){
                            // we're done
                            return;
                        }else{
                            // failed
                            break;
                        }
                    }
                }
            }
            System.err.println("This might be a type checker bug, please report. \nExpecting to add missing dependency error on non present definition: " + error);
        }
    }

    private boolean attachErrorToDependencyDeclaration(ModuleImport moduleImport, String error) {
        WeakHashMap<Node, Object> moduleDepError = moduleImportToNode.get(moduleImport);
        if (moduleDepError != null) {
            for ( Node definition :  moduleDepError.keySet() ) {
                definition.addError(new ModuleDependencyAnalysisError(definition, error));
            }
            return true;
        }
        return false;
    }

    public void attachErrorToOriginalModuleImport(Module module, String error){
        if(getCompiledModules().contains(module)){
            // we're compiling it, just add it to the module node then
            addErrorToModule(module, error);
        }else{
            // we must be importing it
            for(Entry<ModuleImport, WeakHashMap<Node, Object>> entry : moduleImportToNode.entrySet()){
                if(entry.getKey().getModule() == module){
                    for ( Node definition :  entry.getValue().keySet() ) {
                        definition.addError(new ModuleDependencyAnalysisError(definition, error));
                    }
                }
            }
        }
    }

    //must be used *after* addLinkBetweenModuleAndNode has been set ie post ModuleVisitor visit
    public void addErrorToModule(Module module, String error) {
        Node node = moduleToNode.get(module);
        if (node != null) {
            node.addError(new ModuleDependencyAnalysisError(node, error));
        }
        else {
            //might happen if the faulty module is a compiled module
            System.err.println("This is a type checker bug, please report. " +
                    "\nExpecting to add error on non present module node: " + module.toString() + ". Error " + error);
        }
    }

    //must be used *after* addLinkBetweenModuleAndNode has been set ie post ModuleVisitor visit
    public void addWarningToModule(Module module, Warning warningType, String error) {
        Node node = moduleToNode.get(module);
        if (node != null) {
            node.addUsageWarning(warningType, error);
            node.addError(new UsageWarning(node, error, ""));
        }
        else {
            //might happen if the faulty module is a compiled module
            System.err.println("This is a type checker bug, please report. " +
                    "\nExpecting to add error on non present module node: " + module.toString() + ". Error " + error);
        }
    }

    //only used if we really don't know the version
    protected void addErrorToModule(List<String> moduleName, String error) {
        Set<String> errors = topLevelErrorsPerModuleName.get(moduleName);
        if (errors == null) {
            errors = new HashSet<String>();
            topLevelErrorsPerModuleName.put(moduleName, errors);
        }
        errors.add(error);
    }

    public void addLinkBetweenModuleAndNode(Module module, ModuleDescriptor descriptor) {
        //keep link and display errors on modules where we don't know the version of
        Set<String> errors = topLevelErrorsPerModuleName.get(module.getName());
        if (errors != null) {
            for(String error : errors) {
                descriptor.addError(new ModuleDependencyAnalysisError(descriptor, error));
            }
            errors.clear();
        }
        moduleToNode.put(module,descriptor);
    }
    
    public Set<Module> getCompiledModules(){
        return moduleToNode.keySet();
    }

    public ModuleImport findImport(Module owner, Module dependency) {
        for (ModuleImport modImprt : owner.getImports()) {
            if (equalsForModules(modImprt.getModule(), dependency, true)) return modImprt;
        }
        return null;
    }

    public boolean equalsForModules(Module left, Module right, boolean exactVersionMatch) {
        if (left == right) return true;
        List<String> leftName = left.getName();
        List<String> rightName = right.getName();
        if (leftName.size() != rightName.size()) return false;
        for(int index = 0 ; index < leftName.size(); index++) {
            if (!leftName.get(index).equals(rightName.get(index))) return false;
        }
        if (exactVersionMatch && !left.getVersion().equals(right.getVersion())) return false;
        return true;
    }

    public Module findModule(Module module, List<Module> listOfModules, boolean exactVersionMatch) {
        for(Module current : listOfModules) {
            if (equalsForModules(module, current, exactVersionMatch)) return current;
        }
        return null;
    }

    public boolean similarForModules(Module left, Module right) {
        if (left == right) return true;
        String leftName = ModuleUtil.toCeylonModuleName(left.getNameAsString());
        String rightName = ModuleUtil.toCeylonModuleName(right.getNameAsString());
        return leftName.equals(rightName);
    }

    /**
     * This treats Maven and Ceylon modules as similar: com:foo and com.foo will match
     */
    public Module findSimilarModule(Module module, List<Module> listOfModules) {
        for(Module current : listOfModules) {
            if (similarForModules(module, current)) return current;
        }
        return null;
    }

    public Module findLoadedModule(String moduleName, String searchedVersion) {
        return findLoadedModule(moduleName, searchedVersion, modules);
    }
    
    public Module findLoadedModule(String moduleName, String searchedVersion, Modules modules) {
        for(Module module : modules.getListOfModules()){
            if(module.getNameAsString().equals(moduleName)) {
                if (searchedVersion != null && searchedVersion.equals(module.getVersion())){
                    return module;
                }
            }
        }
        return null;
    }

    public void resolveModule(ArtifactResult artifact, Module module, ModuleImport moduleImport, LinkedList<Module> dependencyTree, List<PhasedUnits> phasedUnitsOfDependencies, boolean forCompiledModule) {
        //This implementation relies on the ability to read the model from source
        //the compiler for example subclasses this to read lazily and from the compiled model
        ArtifactContext artifactContext = new ArtifactContext(module.getNameAsString(), module.getVersion(), ArtifactContext.SRC);
        RepositoryManager repositoryManager = context.getRepositoryManager();
        Exception exceptionOnGetArtifact = null;
        ArtifactResult sourceArtifact = null;
        try {
            sourceArtifact = repositoryManager.getArtifactResult(artifactContext);
        } catch (Exception e) {
            exceptionOnGetArtifact = e;
        }
        if ( sourceArtifact == null ) {
            ModuleHelper.buildErrorOnMissingArtifact(artifactContext, module, moduleImport, dependencyTree, exceptionOnGetArtifact, this);
        }
        else {
            
            PhasedUnits modulePhasedUnits = createPhasedUnits();
            ClosableVirtualFile virtualArtifact= null;
            try {
                virtualArtifact = context.getVfs().getFromZipFile(sourceArtifact.artifact());
                modulePhasedUnits.parseUnit(virtualArtifact);
                //populate module.getDependencies()
                modulePhasedUnits.visitModules();
                addToPhasedUnitsOfDependencies(modulePhasedUnits, phasedUnitsOfDependencies, module);
            } catch (Exception e) {
                StringBuilder error = new StringBuilder("unable to read source artifact for ");
                error.append(artifactContext.toString());
                error.append( "\ndue to connection error: ").append(e.getMessage());
                attachErrorToDependencyDeclaration(moduleImport, dependencyTree, error.toString());
            } finally {
                if (virtualArtifact != null) {
                    virtualArtifact.close();
                }
            }
        }
    }

    protected void addToPhasedUnitsOfDependencies(PhasedUnits modulePhasedUnits, List<PhasedUnits> phasedUnitsOfDependencies, Module module) {
        phasedUnitsOfDependencies.add(modulePhasedUnits);
    }
    
    protected PhasedUnits createPhasedUnits() {
        return new PhasedUnits(getContext());
    }

    public Iterable<String> getSearchedArtifactExtensions() {
        return Arrays.asList("src");
    }

    public static List<String> splitModuleName(String moduleName) {
        return Arrays.asList(moduleName.split("[\\.]"));
    }

    public Context getContext(){
        return context;
    }

    public void prepareForTypeChecking() {
        // to be overridden by subclasses
    }

    public void addImplicitImports() {
    }

    public void modulesVisited() {
        // to be overridden by subclasses
    }

    public void visitedModule(Module module, boolean forCompiledModule) {
        // to be overridden by subclasses
    }
}
