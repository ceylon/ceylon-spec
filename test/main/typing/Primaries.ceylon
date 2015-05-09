class Primaries() {
    
    class C() extends Basic() {}
    
    class B() extends Basic() {
        shared C c() { return C(); }
    }
    
    class A() extends Basic() {
        shared B b = B();
    }

    @type:"Primaries.A" value x1 = A();
    
    @type:"Sequence<Primaries.A>" value p2 = [ A(), A() ].sequence();
    
    @error value p3 = A().x;
    
    @error value p4 = A().c();
    
    @type:"Primaries.A" value p5 = (A());
    
    @error value p6 = A()();
    
    @type:"Primaries.A" A aa = A();
    
    @error value p7 = aa.c();
    
    @error value p8 = aa.b();

    @type:"Primaries.B" value p9 = aa.b;
    
    @type:"Primaries.B" value bb = aa.b;
    
    @error value p10 = bb.b;
    
    @type:"Primaries.C" value p11 = bb.c();
    
    @error value p12 = bb.c()();
    
    @type:"Primaries.C" value p13 = aa.b.c();
    
    @type:"Primaries.C" value p14 = (aa.b).c();
    
    @type:"Primaries.C" value cc { return bb.c(); }
    
    @type:"Primaries.C" value ccc { return aa.b.c(); }
    
    @type:"Primaries.A" value p15 = this.A();
    
    //A().this;
    //A().super;
    
    @type:"Primaries.A" value p16 = Primaries().A();
    
    @type:"String" value p17 = "Hello";
    
    class Inner(A arg) {
        Integer n = 0;
        @type:"Primaries.A" value p1 = arg;
        @type:"Primaries.A" value p2 = aa;
        @type:"Primaries.C" value p3 = cc;
        @type:"Primaries.A" value p4 = outer.x1;
        void inner() {
            @type:"Primaries.Inner" value p5 = this;
        }
        shared class Inner2() {
            @type:"Integer" value p7 = outer.n;
            @type:"Primaries.Inner" value p8 = outer;
            void inner() {
                @type:"Primaries.Inner.Inner2" value p6 = this;
            }
            //@type:"Primaries" value p8 = outer.outer;
        }
    }
    
    @type:"Primaries.Inner.Inner2" value p18 = Inner(aa).Inner2();
    //@type:"Primaries.Inner" value p19 = Inner(A()).Inner2().outer;
    //@type:"Primaries" value p20 = Inner(aa).Inner2().outer.outer;
    //@type:"A" Inner(A()).aa;
    
    void method(A arg1, B arg2) {
        @type:"Primaries.A" value p1 = arg1;
        @type:"Primaries.B" value p2 = arg2;
        @type:"Primaries.A" value p3 = aa;
        @type:"Primaries.C" value p4 = cc;
        @type:"Primaries.A" value p5 = this.x1;
        class Inner3() {
            String s = "hello";
            @type:"String" value p6 = this.s;
            @type:"Primaries.A" value p7 = outer.x1;
            void inner() {
                @type:"Inner3" value t = this;
            }
        }
        @type:"Inner3" value p8 = Inner3();
        //@type:"Primaries" value t = this;
        //@type:"Primaries" value p9 = Inner3().outer;
    }
    
    interface G {
        shared String getIt() {
            return "Hello";
        }
    }
    interface H {
        shared void doIt() {}
    }
    class S() extends B() satisfies G & H {}
    class T() extends Basic() satisfies G & H {}
    
    @type:"Sequence<Primaries.B>" value p21 = [S(), B()].sequence();
    @type:"Sequence<Primaries.B>" value p22 = [B(), S()].sequence();
    @type:"Sequence<Primaries.S|Primaries.T>" value p23 = [S(), T()].sequence();
    @type:"Sequence<Primaries.T|Primaries.S>" value p24 = [T(), S()].sequence();
    {T(), S()}*.doIt();
    @type:"Sequence<String>" value p25 = [S(), T()]*.getIt();
    B[] bs1 = [S(), B()];
    B[] bs2 = [B(), S()];
    //H[] hs = [S(), T()];
    //G[] gs = [T(), S()];
    @type:"Sequence<Primaries.A|Primaries.B|String|Sequence<Integer>>" value p26 = [A(),B(),"Hello",A(),[1,2,3].sequence(),S()].sequence();
    //Object[] stuff = [A(),B(),"Hello",[1,2,3]];
    value objects = [A(),B(),"Hello",[1,2,3].sequence()].sequence();
    //Object[] things = objects;
    @type:"Null|Primaries.A|Primaries.B|String|Sequence<Integer>" value p27 = objects[1];
    @type:"Null|String" value p28 = objects[1]?.string;
    if (exists o = objects[1]) {
        @type:"Primaries.A|Primaries.B|String|Sequence<Integer>" value p29 = o;
        String s = o.string;
        @type:"Sequence<Primaries.A|Primaries.B|String|Sequence<Integer>|Float>" value p30 = [o, "foo", 3.1, A()].sequence();
    }
    @type:"Sequential<String>" String[] noStrings = [];
    @type:"Empty" value none = [];
    
    @type:"Sequential<Boolean>" value p100 = { true }.sequence();
    @type:"Sequential<Boolean>" value p101 = { true, false }.sequence();
    @type:"Sequential<Null|Boolean>" value p102 = { null, true, false }.sequence();
    @type:"Sequence<Boolean>" value p100s = [ true ].sequence();
    @type:"Sequence<Boolean>" value p101s = [ true, false ].sequence();
    @type:"Sequence<Null|Boolean>" value p102s = [ null, true, false ].sequence();
    
    object idobj satisfies G&H {}
    object obj extends Object() satisfies G&H {
        shared actual Boolean equals(Object other) {
            return false;
        }
        shared actual Integer hash = 0;
        shared actual String string = "";
    }
    
    @type:"Sequence<Basic&Primaries.G&Primaries.H>" value p103 = [ idobj ].sequence();
    @type:"Sequence<Primaries.G&Primaries.H>" value p104 = [ obj ].sequence();
    @type:"Sequence<Primaries.G&Primaries.H>" value p105 = [ obj, idobj ].sequence();
    
    String angstroms = "\{#00E5}ngstr\{#00F6}ms";
    
    value i1 = 1_123;
    @error value i2 = 1_12;
    @error value i3 = 1_1234;
    @error value i4 = 1_1234_123;
    @error value i5 = 1_123_1234;
    value i6 = 1_123_123;
    
    value f1 = 1_123.0;
    @error value f2 = 1_12.0;
    @error value f3 = 1_1234.0;
    @error value f4 = 1_123_1234.0;
    @error value f5 = 1_1234_123.0;
    value f6 = 1_123_123.0;
    value f7 = 1.123_1;
    @error value f8 = 1.12_1;
    @error value f9 = 1.1234_1;
    @error value f10 = 1.123_1234;
    value f11 = 1.123_123;
    
    {B*} beez = p2*.b;
    {String*} stringz = {"hello", "world"}.map((String s) => s.uppercased);
        
    see {@error `Float`} shared {Float*} floats = {};
    
    String interpolated0 = "hello`` "ABC123".count(function (Character c) => c.digit) ``world";
    String interpolated1 = "hello`` { "ABC`` 1+1 ``123" } ``world";
    String interpolated2 = "hello`` Singleton("ABC`` 1+1 ``123") ``world";
    String interpolated3 = "hello`` Singleton { element="ABC`` 1+1 ``123"; } ``world";
    String interpolated4 = "hello`` ("ABC`` 1+1 ``123") ``world";
    String interpolated5 = "hello`` ["ABC`` 1+1 ``123"] ``world";
    String interpolated6 = "hello`` -10 ``world";
    String interpolated7 = "hello`` +10 ``world";
    String interpolated8 = "hello`` "ABC123".size ``world";
    
    List<Character> list=[' '];
    @type:"Sequential<Integer>" value xxxx = list*.integer;
    Exception("Expecting an Array but got: `` true then "x" else "null" ``");    
    
}
