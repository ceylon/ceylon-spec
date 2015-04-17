@error class WithBothInitAndDefaultConst() {
    shared new WithBothInitAndDefaultConst() {}
}

class WithNeitherInitNorConst() {} //TODO: should be an error

class WithConst extends Basic {
    shared new Const() {}
}

@error class WithNoConst extends Basic() {}
@error class WithInit() extends Basic {}

@error class WithConstAndParams() {
    new Const() {}
}

class WithDefaultConst {
    shared new () {}
}

class ExtendsWithDefaultConstBroken extends WithDefaultConst {
    @error shared new () {}
}

class ExtendsWithDefaultConstOk extends WithDefaultConst {
    shared new () extends WithDefaultConst() {}
}

class ExtendsWithConstBroken extends WithConst {
    @error shared new () {}
}

class ExtendsWithConstOk extends WithConst {
    shared new () extends Const() {}
}

class WithConstAndDefaultConst {
    shared new () {}
    new Const() {}
}

class WithAttributes {
    String name = "Trompon";
    Integer init;
    variable Integer count;
    print(name);
    shared new () {
        count = 0;
        init = count;
    }
    new ConstWithParameter(Integer initial) {
        count = initial;
        init = initial;
    }
    void inc() {
        count++;
    }
    void reset() {
        count = init;
    }
}

class WithSharedAttributes {
    shared String name = "Trompon";
    shared Integer init;
    shared variable Integer count;
    print(name);
    shared new () {
        count = 0;
        init = count;
    }
    new ConstWithParameter(Integer initial) {
        count = initial;
        init = initial;
    }
    shared void inc() {
        count++;
    }
    shared void reset() {
        count = init;
    }
}

class BrokenWithAttributes {
    String name;
    variable Integer count;
    Integer init;
    shared new () {
        init = 0;
    }
    new ConstWithParameter(Integer initial) {
        count = initial;
    }
    void inc() {
        @error count++;
    }
    void reset() {
        @error count = init;
    }
}

class BrokenWithSharedAttributes {
    @error shared String name;
    @error shared variable Integer count;
    @error shared Integer init;
    shared new () {
        init = 0;
    }
    new ConstWithParameter(Integer initial) {
        count = initial;
    }
    shared void inc() {
        @error count++;
    }
    shared void reset() {
        @error count = init;
    }
}

class WithAttributesAndMisplacedStatement {
    String name = "Trompon";
    Integer init;
    variable Integer count;
    shared new () {
        count = 0;
        init = count;
    }
    new ConstWithParameter(Integer initial) {
        count = initial;
        init = initial;
    }
    @error print(name);
    void inc() {
        @error count++; //TODO: remove useless extra error
    }
    void reset() {
        @error count = init; //TODO: remove useless extra error
    }
}

class WithAttributesAndMispacedUsage {
    String name = "Trompon";
    Integer init;
    variable Integer count;
    print(name);
    @error print(init);
    shared new () {
        @error print(count);
        count = 0;
        init = count;
    }
    new ConstWithParameter(Integer initial) {
        @error print(init);
        count = initial;
        init = initial;
    }
    void inc() {
        count++;
    }
    void reset() {
        count = init;
    }
}

class Alias1() => WithDefaultConst();
class Alias2() => WithConst.Const();
@error class BrokenAlias1() => WithConst();
@error class BrokenAlias2() => WithConst;
@error class AliasWithNoParams => WithNeitherInitNorConst();

class Super {
    shared new New() {}
}

class Broken extends Super {
    @error shared new () extends Super() {}
}

class MoreBroken extends Super {
    @error new Broken() extends Basic() {}
}

class Sub1 extends Super {
    new New() extends Super.New() {}
}
class BrokenSub1 extends Super {
    @error new New() extends Super.New {}
}

class Sub2() extends Super.New() {}
@error class BrokenSub2() extends Super.New {}

class Alias() => Super.New();

class Sub3() extends Alias() {}
class Sub4 extends Alias {
    shared new () extends Alias() {}
}

class Silly extends Basic {
    shared new () extends Basic() {}
}

class Unshared() { }

shared class SharedWithConstructor {
    shared new (@error Unshared bar) { }
}

abstract class AbstractWithConstructor {
    shared new ConstructorForAbstract() {}
}

class InheritsAbstractWithConstructor1() 
        extends AbstractWithConstructor.ConstructorForAbstract() {
}

class InheritsAbstractWithConstructor2 
        extends AbstractWithConstructor {
    shared new Constructor() extends ConstructorForAbstract() {}
}

void testRefs<T>() {
    @error value val1 = WithConst;
    @error value val2 = T;
    @error value val3 = Identifiable;
    @error value val4 = AbstractWithConstructor.ConstructorForAbstract;
    
    @type:"InheritsAbstractWithConstructor1" 
    value new1 = InheritsAbstractWithConstructor1();
    
    @type:"InheritsAbstractWithConstructor2" 
    value new2 = InheritsAbstractWithConstructor2.Constructor();
}

shared class C<out X> {
    shared new ({X*} items) {}
    shared class B<out Y> {
        shared new (@error [X*] items) {}
        shared new Other([Y*] items) {}
    }
}

shared class Supertype(Integer x) {
    shared Integer zsub = 0;
}

shared class Subtype extends Supertype {
    Integer xsub = 1;
    shared Integer ysub = 1;
    
    @error shared new Subtype() extends Supertype(xsub) {}
    @error shared new New() extends Supertype(ysub) {}
    @error shared new Extra() extends Supertype(ysub) {}
}

class WithDupeConstructor {
    new Dupe() {}
    @error new Dupe(String string) {}
}
class WithDupeDefaultConstructor {
    shared new () {}
    @error new (String string) {}
}

class WithUnsharedDefaultConstructor {
    @error new () {}
}

shared class Thing {
    String name;
    
    sealed shared new(String name) {
        this.name = name;
    }
    
    sealed shared new Another(String name) {
        this.name = name;
    }
}