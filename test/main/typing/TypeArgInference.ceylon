class TypeArgInference() {
    
    class X<T>(T t, T s) {
        shared class Y<S>(S ss) {}
        shared R? opt<R>(R r) { return null; }
    }
    
    @type:"TypeArgInference.X<String>" X("hello", "world");
    @type:"TypeArgInference.X<Float|Integer>" X(1.0, 1000);
    @type:"TypeArgInference.X<String>.Y<Float>" X("hello", "world").Y(3.0);
    @type:"Null|String" X(1,2).opt("hello");
    
    @type:"TypeArgInference.X<String>" X{ t="hello"; s="world"; };
    @type:"TypeArgInference.X<Float|Integer>" X { t=1.0; s=1000; };
    @type:"TypeArgInference.X<String>.Y<Float>" X{ t="hello"; s="world"; }.Y { ss=3.0; };
    @type:"Null|String" X { t=1; s=2; }.opt{ r="hello"; };
    
    U first<U>(U u, U v) { return u; }

    @type:"String" first("hello", "world");
    @type:"Integer|TypeArgInference.X<String>" first(+1,X("hello", "world"));
    
    @type:"String" first{ u="hello"; v="world"; };
    @type:"Integer|TypeArgInference.X<String>" first{ u=+1; v=X{ t="hello"; s="world"; }; };
    
    @type:"TypeArgInference.X<Basic>" X { object t {} object s {} }; 
    @type:"TypeArgInference.X<Float>" X { Float t { return 1.0; } Float s { return -1.0; } }; 
    @type:"TypeArgInference.X<Boolean>" X(true, false);
    
    @error X x = X(1,2);
    
    //@error String firstString(String x, String y) = first;
    //String secondString(String x, String y) = first<String>;
        
    class Const<T,S>(T t) given T satisfies Numeric<T> {}
    @type:"TypeArgInference.Const<Integer,Nothing>" Const(1);
    @error Const("hello");
    
    @error first("hello");
    @error first("ullo", "ullo", "ullo");
    @error first { u="hi"; };
    
    T? firstElem<T>(T[]? sequence) {
        if (exists sequence) {
            return sequence.first;
        }
        else {
            return null;
        }
    }
    
    @type:"Null|Sequential<String>" String[]? strings = ["hello", "goodbye"];
    @type:"Null|String" value s = firstElem(strings);
    @type:"Null|String" value ss = firstElem { sequence=strings; };

    T corner<T>(Sequence<Sequence<T>> matrix) {
        return matrix.first.first;
    }
    
    @type:"Sequence<Sequence<Integer>>" value ints = [[-1].sequence()].sequence();
    @type:"Integer" value i = corner(ints);
    @type:"Integer" value ii = corner { matrix = ints; };
    
    T method<T>(String|Sequence<T> s) { throw; }
    @type:"String" method(["hello"]);
    @type:"Nothing" method("hello");
    @error:"String" method([]);
    
    T? firstElt<T>(T* args) {
        return args.sequence().first;
    }
    T? firstElt0<T>({T*} args) {
        return args.sequence().first;
    }
    @type:"Null|String" firstElt("hello", "world");
    @type:"Null|Sequence<String>" firstElt(["hello", "world"].sequence());
    @type:"Null|Tuple<String,String,Tuple<String,String,Empty>>" firstElt (["hello", "world"]);
    @type:"Null|String" firstElt(*["hello", "world"]);
    @type:"Null|String" firstElt0 { "hello", "world" };
    @type:"Null|Sequence<String>" firstElt0 {["hello", "world"].sequence()};
    firstElt { args = "hello".sequence(); @error args="world".sequence(); };
    @type:"Null|String" firstElt { args = ["hello","world"]; };
    @type:"Null|String" firstElt0 { args = {"hello", "world"}; };
    
    T? createNull<T>() {
        return null;
    }
    @type:"Null" value opt = createNull();
    
    void print(String s) {}
    print("Hello");
    @error print<String>("Hello");

    V f1<U,V>(U u) 
            given U satisfies V {
    	return u;
    }
    
    V f2<U,V>(U u) 
            given U satisfies Sequence<V> {
    	return u.first;
    }
    
    @type:"String" f1("hello");
    @type:"Integer" f2([1,2,3]);
    
    interface Z {}
    interface W {}
    object xzn extends X<Integer>(0, 1) satisfies Z&W {}
    Z&X<Integer> zxn = xzn;
    
    function g<T>(X<T>&Z x) { return x; }
    
    @type:"TypeArgInference.X<Integer>&TypeArgInference.Z" g(xzn);
    @type:"TypeArgInference.X<Integer>&TypeArgInference.Z" g(zxn);
    
    interface One<out T> {}
    interface Two<out T> {}
    
    interface A {}
    interface B {}
    
    object test0 satisfies One<A> & Two<B> {}
    object test1 satisfies One<Integer> & Two<Float> {}
    object test2 satisfies One<Number<Integer>> & Two<Integer&String> {}
    
    T? acceptOneTwo<T>(One<T>&Two<T> arg) => null;
    T? acceptOneOrTwo<T>(One<T>|Two<T> arg) => null;
    
    @type:"Null|TypeArgInference.A|TypeArgInference.B" acceptOneTwo(test0);
    @type:"Null|Integer|Float" acceptOneTwo(test1);
    @type:"Null|Number<Integer>" acceptOneTwo(test2);
    
    @type:"Null|TypeArgInference.A|TypeArgInference.B" acceptOneOrTwo(test0);
    @type:"Null|Integer|Float" acceptOneOrTwo(test1);
    @type:"Null|Number<Integer>" acceptOneOrTwo(test2);
    
    void higherAnything<X>(void f(X x)) {}
    higherAnything((String x) => print(x));
    higherAnything { void f(String x) { print(x); } };

    X|Y higher<X,Y>(X f(Y? y)) given Y satisfies Object => f(null);
    @type:"Integer|String" higher((String? y) => 1);
    @type:"Float|String" higher { function f(String? y) => 1.0; };
    function argfun(Integer? x) => x?.float;
    @type:"Null|Float|Integer" higher(argfun);
    
    @type:"Iterable<Integer,Nothing>" { "hello", "world" }.map((String s) => s.size);
    @type:"Iterable<String,Null>" { "hello", "world" }.filter((String s) => !s.empty);
    @type:"String" { "hello", "world" }.fold("")((String result, String s) => result+" "+s);
    @type:"Null|String" { null, "hello", "world" }.find((String? s) => s exists);

    @type:"Iterable<Integer,Nothing>" { "hello", "world" }.map { function collecting(String s) => s.size; };
    @type:"Iterable<String,Null>" { "hello", "world" }.filter { function selecting(String s) => !s.empty; };
    @type:"String" { "hello", "world" }.fold { initial=""; }((String result, String s) => result+" "+s);
    @type:"Null|String" { null, "hello", "world" }.find { function selecting(String? s) => s exists; };
    
    @type:"Tuple<Integer|String,Integer|String,Empty>" Tuple(true then "" else 1, []);
    @type:"Tuple<Integer|String|Float,Integer|String,Tuple<Float,Float,Empty>>" Tuple(true then "" else 1, [1.0]);
    
    class Something<X,Y,Z>(Y y, Z z) 
            given Y satisfies X 
            given Z satisfies X {}
    @type:"TypeArgInference.Something<String|Integer,String,Integer>" Something("",1); 
    @type:"TypeArgInference.Something<String|Float|Integer,String,Float|Integer>" Something("",true then 1 else 1.0); 
    @type:"TypeArgInference.Something<String|Null|Float|Integer,String,Null|Float|Integer>" Something("",false then (true then 1 else 1.0));
    
    @type:"Null" max {};
    @type:"Null" max({});
    @type:"Integer" max {1};
    @type:"Integer" max({1});

}

class QualifiedTypeArgInference() {
    class Outer<out T>(T t) {
        shared class Inner<out S>(S s) {}
    }
    function id<T,S>(Outer<T>.Inner<S> val) => val;
    @type:"QualifiedTypeArgInference.Outer<String>.Inner<Integer>"
    id(Outer("").Inner(1));
    function both<T,S>(Outer<T>.Inner<S> x, Outer<T>.Inner<S> y) => 1==1 then x else y;
    @type:"QualifiedTypeArgInference.Outer<String|Null>.Inner<Integer|Float>"
    both(Outer("").Inner(1), Outer(null).Inner(1.0));
}

void issue() {
    interface C<in A> {}
    interface S<E> {}

    void f<T>(S<T|Exception> s, C<S<T>> c) {}

    void g(S<Integer|Exception> s, C<S<Integer>> c) {
        f<Integer>(s, c); //works
        f(s, c); //doesn't
    }
    
    T t<T>(T? t) => t else nothing;
    @type:"Integer" t(true then 1);
}

class ArraySequence<X>({X*} xs) satisfies Iterable<X> {
    iterator() => xs.iterator();
}

void folding() {
    
    {String+} s2 = { "Hello", "World" };
    s2.fold(1)((Integer a, String b) => a+b.size);
    
    ArraySequence<String->Integer> m = ArraySequence { "a"->1, "b"->2, "c"->3 };
    m.fold(0)((Integer x, String->Integer e) => x+e.item);
    
    Map<String,Integer> m0 = nothing;
    m0.fold(0)((Integer x, String->Integer e) => x+e.item);
    
    Integer hashes0(String* objects) =>
            objects.fold(0)((Integer result, String obj) => result+obj.size);
    
    Integer hashes1(Object* objects) =>
            objects.fold(0)((Integer result, Object obj) => result+obj.hash);
    
}

void inferenceAndAliases() {
    class Test1<T>(Anything(T) x) {}
    @type:"Test1<Integer>" value test1 = Test1((Integer x) => x);
    @type:"Test1<Integer>" value test1x = Test1 { x = (Integer x) => x; };
    alias TestFun<in T> => Anything(T);
    class Test2<T>(TestFun<T> x) {}
    @type:"Test2<Integer>" value test2 = Test2((Integer x) => x);
    @type:"Test2<Integer>" value test2x = Test2 { x = (Integer x) => x; };
}

void inferenceAndSequencedAliases() {
    class Test1<T>(Anything(T)* x) {}
    @type:"Test1<Integer>" value test1 = Test1((Integer x) => x);
    @type:"Test1<Integer>" value test1c = Test1(for (i in 1..1) (Integer x) => x);
    @type:"Test1<Integer>" value test1x = Test1 { x = [(Integer x) => x]; };
    alias TestFun<in T> => Anything(T);
    class Test2<T>(TestFun<T>* x) {}
    @type:"Test2<Integer>" value test2 = Test2((Integer x) => x);
    @type:"Test1<Integer>" value test2c = Test1(for (i in 1..1) (Integer x) => x);
    @type:"Test2<Integer>" value test2x = Test2 { x = [(Integer x) => x]; };
}

void inferenceAndVariance() {
    class Consumer<in T>(void consume(T t)) {}
    class Producer<out T>(T t) {}
    class Inv<T>() {}
    class Test<in X, out Y>(
        Producer<X> px, Producer<Y> py, 
        Consumer<X> cx, Consumer<Y> cy) {}
    @type:"Test<Integer,Integer>" @error Test(Producer(1.0), Producer(1), 
        Consumer(void (Integer t) {}), Consumer(void (Float t) {}));
    class Test2<in X, out Y>(Inv<X> invx, Inv<Y> invy) {}
    @type:"Test2<Integer,Integer>" Test2(Inv<Integer>(), Inv<Integer>());
    class Weird<in T>(T t) {}
    @type:"Weird<Anything>" Weird("");
}

void inferenceForRecursiveFuns() {
    T? echo1<T>(T? val) => echo1(val);
    T? echo2<T>(T? val) => echo2<T>(val);
    T? echo3<T>(T? val) => echo2(val);
    T? echo4<T>(T? val) {
        if (exists val) {
            return echo4(val);
        } else {
            return null;
        }
    }
    class Foo<T>(shared T val) { }
    T? extract1<T>(Foo<T>? foo) => extract1(foo);
    T? extract2<T>(Foo<T>? foo) => extract1(foo);
    String|T echoST<T>(String|T val) => echoST(val);
}

void higherOrderFun<Bar>(void func(Bar b)) {} 
void testHigherOrderFun() {
    higherOrderFun<String>(void(b){ @type:"String" value bb=b; });
    higherOrderFun(void(String b){});
    @error higherOrderFun(void(b){});
}

Bar anotherHigherOrderFun<Bar>(Bar b, void func(Bar b)){return b;}
void testAnotherHigherOrderFun() {
    Integer result1
            = anotherHigherOrderFun(42, void(Integer b){});
    Integer result2 
            = anotherHigherOrderFun<Integer>(42, void(b){});
    @error Integer result3
            = anotherHigherOrderFun(42, void(b){});
}
