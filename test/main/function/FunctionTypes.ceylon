String upper(String s) {
   return s.uppercased;
}

void print(String s) {}

class X(String s) {
    shared class Y() {}
    shared void hello() {}
}

abstract class Z() {} 

void noop() {}

void higher1(String[] strings, Callable<Anything,[String]> f) {
    value g = f;
    Anything h(String s) => f(s);
    for (s in strings) {
        g(s); h(s);
    }
}

Y pass<X,Y>(Y f(X x), X x) {
    return f(x);
}

void higher2(String[] strings, void f(String str)) {
    for (s in strings) {
        f(s);
    }
    @error f(1);
    @error f();
    @error f("hello", 2);
}

Integer higher3(Integer f(Float x)) => f(0.0);

String str(Float f) => f.string;

Float curried(Integer x)(Float y) => x+y;

X->Y generic<X,Y>(Y f(X x), X x()) 
        given X satisfies Object 
        given Y satisfies Object { 
    X xx = x();
    return xx->f(xx);
}

T do<T>(T f()) { return f(); }

void method() {
    Callable<String,[String]> upperRef = upper;
    Callable<Anything,[String]> printRef = print;
    Callable<Anything,[Nothing]> printRefContra = print;
    Callable<X,[String]> xRef = X;
    Callable<Anything,Nothing> xRefContra = X;
    X x = X("hello");
    Callable<X.Y,[]> yRef = x.Y;
    Callable<Anything,[]> helloRef = x.hello;
    Callable<Anything,[]> noopRef = noop;
    
    higher1(["hello", "world"], print);
    higher1(["hello", "world"], upper);
    higher1(["hello", "world"], X);
    @error higher1(["hello", "world"], noop);
    @error higher1(["hello", "world"], str);
    
    higher2(["hello", "world"], print);
    higher2(["hello", "world"], upper);
    higher2(["hello", "world"], X);
    @error higher2(["hello", "world"], noop);
    @error higher2(["hello", "world"], str);
    
    @type:"String" function up(String s) => upper(s);
    Anything pr(String s) => print(s);
    Anything np() => noop();
    
    @type:"String" up("hello");
    @type:"Anything" pr("hello");
    @type:"Anything" np();
    
    @type:"X" function good(String s) => X(s);
    X better(String s) => X(s);
    @type:"X" @error function bad() => X();
    @type:"X" @error function badder(Integer n) => X(n);
    @error String worse(String s) => X;
    @error String worst() => X;
    void notbroke() => noop();
    @error Z moreBroke() => Z;
    @error do(Z);
    @error function z() => Z;
    
    String s1 = pass((String s) => s, "hello");
    String s2 = pass((Float f) => f.string, 1.0);
    @error String s3 = pass((Float f) => f.string, "hello");
    
    higher2 { 
        strings = ["goodbye"];
        void f(String s) { print(s); }
    };
    higher2 { 
        strings = ["goodbye"];
        Integer f(String s) { print(s); return s.size; }
    };
    higher2 {
        strings = ["goodbye"];
        @error void f(Integer n) { print(n.string); }
    };
    
    higher2 { 
        strings = ["goodbye"];
        Integer f(String s) { print(s); return s.size; }
    };
    higher2(["goodbye"], (String s) => print(s));
    higher2(["goodbye"], (String s) => print(s));
    
    higher2 { 
        strings = ["goodbye"];
        Integer f(String s) => s.size;
    };
    higher2 { 
        strings = ["goodbye"];
        Integer f(String s) => s.size;
    };
    
    higher2 { 
        strings = ["goodbye"];
        void f(String s) => print(s);
    };
    higher2 { 
        strings = ["goodbye"];
        Anything f(String s) => print(s);
    };
    higher2 { 
        strings = ["goodbye"];
        String f(String s) => s;
    };
    higher2 {
        strings = ["goodbye"];
        Integer f(String s) => s.size;
    };
    higher2 {
        strings = ["goodbye"];
        @error Integer f(String s) => print(s);
    };
    higher2 { 
        strings = ["goodbye"];
        @error Integer f(Integer i) => i;
    };
    higher2 { 
        strings = ["goodbye"];
        @error function f(Integer i) => print(i);
    };
    
    higher2 { 
        strings = ["goodbye"];
        @error f(String s) => s.size;
    };
    
    
    higher3 {
        function f(Float f) { return f.integer; }
    };
    higher3 {
        function f(Float f) => f.integer;
    };
    higher3 {
        Integer f(Float f) => f.integer;
    };
    higher3 {
        @error void f(Float f) {}
    };
    higher3 {
        @error function f(Float f) { return f; }
    };
    higher3 {
        @error function f(Float f) => f;
    };
    higher3 {
        @error Float f(Float f) => f;
    };
    higher3 {
        @error Integer f(Float f, String s) => f.integer;
    };
    higher3 {
        @error Integer f(String s) => s.size;
    };
    
    @error print(s);
    
    @type:"Callable<Float,Tuple<Float,Float,Empty>>" curried(1);
    Float plus1(Float x) => curried(1)(x);
    @type:"Callable<Float,Tuple<Float,Float,Empty>>" value p1 = curried(1);
    Float three = plus1(2.0);
    Float four = curried(2)(2.0);
    @error curried(2)("foo");
    @error curried("foo")(2.0);
    @error curried(2)();
    @error curried(2)(2.0, "foo");
    //Float t1 = p1(2.0);
    
    function str(Float f) => f.string;
    function zero() => 0.0;
    @type:"Entry<Float,String>" generic(str,zero);
    @type:"Entry<Float,String>" generic(str,nothing);
    @type:"Entry<Object,Object>" generic((Object obj) => obj, () => "hello");
    @type:"Entry<Object,String>" generic((Object obj) => obj.string, () => "hello");
    @type:"Entry<String,String>" generic((String str) => str, () => "hello");
    
    function fx(String g()) => do<String>;
    @error function fy(String g()) => do;
    value fw = do<String>;
    @error value fz = do;

    function sqrt(Float x) => x^0.5;
    value temp = sqrt;
    Float root(Float x) => temp(x);
    
    Callable<Anything> reallyBroken(void foo()) {
        /*@error*/ return foo;
    }
    
    Null foo(Integer* seq) => null;
    Null bar(Integer* ints) => foo(*ints);
    Null baz(Integer* seq); baz = foo;
    Null qux(Integer* ints) => baz(*ints);
    Null ok(Integer ints) => foo(ints);
    @error Null broke(Integer ints) => foo(*ints);
    Null notBroke(Integer ints); notBroke = foo;
    Null alsoBroke(Integer* ints); @error alsoBroke = ok;
    Null reallyBroke(Integer* ints); reallyBroke(@error Integer[] ints) => foo(*ints);
    Null badlyBroke(Integer* ints); badlyBroke(@error Integer[] ints) => ok(ints.first else 0);
    Null terrible(Integer* ints); @error terrible(Integer* ints) => foo;    
}

class Outer() {
    class Inner() {}
    Outer.Inner oi = Inner();
}

Comparison multiCompare()(Integer x, Integer y) {
    return x<=>y;
}
void testMultiCompare() {
    multiCompare()(1,1);
}

Callable<String,[]> tester() {
    String f() { return "ok"; }
    return f;
}
void runTester() {
    print(tester()());
}

void moreTests() {
    void callFunction(String f(Integer i)) {
        print(f(0));
    }
    function f(Integer i) => (i+12).string;
    callFunction(f);
    callFunction((Integer i) => (i*3).string);
    callFunction {
        function f(Integer i) { 
            return (i^2).string;
        }
    };
}

Sequence<String()> singletonStringFunc = Singleton<String()>(()=>"hello");
Sequence<Boolean()(String)> singletonBooleanFunc = Singleton<Boolean()(String)>((String s)()=>s=="hello");

void sequencedParams() {
    function string1(Character* chars) => String(chars);
    value str = string1;
    Anything(Character*) str0 = str;
    Anything(Character*) str0p = string1;
    Anything(Character) str1 = str;
    Anything(Character, Character) str2 = str;
    str(*"hello");
    str(*"hello".sequence());
    str();
    str('X');
    str('h', 'e', 'l', 'l', 'o');
    @error str(1);
    @error str("hello".characters);
    @error str(*'X');
}

 class Outer1() {
   shared class Inner() { }
 }
 Outer1? o = null;
 Outer1.Inner? i1 = o?.Inner();
 Outer1.Inner? cons() => o?.Inner();
 
void foo1(Integer* seq) {}
void bar1(Integer* ints) { foo1(*ints); }
Anything(Integer*) baz1 = bar1; 

Boolean foo2(Integer* seq) => true;
Boolean bar2(Integer* ints) => foo2(*ints);
Boolean(Integer*) baz2 = bar2; 

alias CSI => Callable<String,[Integer]>;
String callCSI(CSI csi) {
    return csi(1);
}

void handle(String s, Float x=0.0, Boolean b=false) {}
void useHandle() {
    value f = handle;
    @error handle();
    handle("");
    @error handle(1);
    handle("", 1.0);
    @error handle("", 1);
    handle("", 1.0, true);
    @error handle("", 1.0, 1);
}

void lazySpec() {
    String lazy1(String s, Float x=0.0, Boolean b=false);
    lazy1 = (String s, Float x, Boolean b) => b then s else x.string;
    String lazy2(String s, Float* x);
    lazy2 = (String s, Float* x) => s;
    String lazy3(String s, Float x=0.0);
    lazy3 = (String s, Float x, Boolean b=true) => b then s else x.string;
    String lazy4(String s, Float x, Float y);
    lazy4 = (String s, Float* x) => s;
    Anything x(String s="")(Integer i);
    x = (String s)(Integer i) => nothing;
}

void lazyLazySpec() {
    String lazy1(String s, Float x=0.0, Boolean b=false);
    lazy1(String s, Float x, Boolean b) => b then s else x.string;
    String lazy2(String s, Float* x);
    lazy2(String s, Float* x) => s;
    String lazy3(String s, Float x=0.0);
    @error lazy3(String s, Float x, Boolean b) => b then s else x.string;
    String lazy5(String s, Float x=0.0);
    lazy5(String s, @error Float x=1.0) => s + x.string;
    String lazy6(String s, Float x=0.0);
    lazy6(String s, @error Integer x) => s + x.string;
    String lazy4(String s, Float x, Float y);
    @error lazy4(String s, @error Float* x) => s;
    Anything x(String s="")(Integer i);
    x(String s)(Integer i) => nothing;
}

void defaulted() {
    function fun(Integer i, Float x = 0.0, String s = "") => s;
    @error fun();
    fun(1);
    fun(1, 0.0);
    fun(1, 0.0, "");
    String(Integer,Float=,String=) f = fun;
    f(1);
    f(1, 0.0);
    f(1, 0.0, "");    
    String(Integer,Float,String) g = fun;
    @error g(1);
    @error g(1, 0.0);
    g(1, 0.0, "");    
}

void bug() {
    Callable<Anything, [Integer, String=, Integer*]> defaultedVariadic = 
            function (Integer a, String b = "b", Integer* args) => a;
    defaultedVariadic(1);
    defaultedVariadic(1, "a");
    defaultedVariadic(1, "a", 1); // error
    defaultedVariadic(1, "a", 1, 2); // error
    defaultedVariadic(1, "a", *{}); // error
}

void parameterToFunction(void parameter(String s)) {
    void function1(String s) => parameter(s);
    Anything function2(String s) => parameter(s);
}

T indirectInvocationWhichIsNotReallyIndirect<T>(T t){
    @type:"Integer"
    indirectInvocationWhichIsNotReallyIndirect(2);
    @type:"Integer"
    (indirectInvocationWhichIsNotReallyIndirect)(2);
    @type:"Integer"
    (indirectInvocationWhichIsNotReallyIndirect){ t = 2; };
    return t;
}

void abbreviations<T>() given T satisfies Anything[] {
    @type:"Callable<String,T>" String(*T) f1;
    @type:"Callable<String,Tuple<T,T,Empty>>" String(T) f2;
    @type:"Callable<String,Tuple<String,String,Empty>>" String(String) f3;
    @error String(*String) f4;
    @type:"Callable<String,Tuple<String,String,Empty>>" String(*[String]) f5;
}