class Intersection() {
    
    interface X {
        shared String hello {
            return "hello";
        }
    }
    
    interface Y {
        shared String goodbye {
            return "goodbye";
        }
    }
    
    class XY() satisfies X & Y {}
    
    X&Y xy = XY();
    @type["String"] value hi = xy.hello;
    @type["String"] value bye = xy.goodbye;
    X x = xy;
    Y y = xy;
    
    object xOnly satisfies X {}
    
    @error X&Y xyError = xOnly;
    
    @type["Intersection.X&Intersection.Y"] X&Object&Y xo = XY();
    @type["Bottom"] X&Bottom&Y xb;
    
    @type["Intersection.X&Intersection.Y"] function f(X&Y xy) {
        return xy;
    }
    
    f(xy);
    @error f(xOnly);
    
    class Super() {
        shared default X&Y get(X&Y&Object xy) {
            return xy;
        }
    }
    
    class Good1() extends Super() {
        shared actual X&Y get(X&Y&Object xy) {
            print(xy.hello);
            return xy;
        }
    }
    
    class Good2() extends Super() {
        shared actual X&Y&Object get(X&Y xy) {
            print(xy.goodbye);
            return xy;
        }
    }
    
    class Bad() extends Super() {
        @error shared actual X get(@error X&Object xy) {
            return xy;
        }
    }
    
    class Consumer<in T>() {
        shared void consume(T t) {}
    }
    
    Consumer<X>|Consumer<Y> consumer = Consumer<X>();
    Consumer<X&Y> c = consumer;
    consumer.consume(xy);
    @error consumer.consume(xOnly);
    @error Consumer<X>&Consumer<Y> errc = c;
    
    Sequence<X&Y> seqxy = { XY(), XY() };
    Sequence<X>&Sequence<Y> useq = seqxy;
    
    Consumer<X|Y> consxy = Consumer<X|Y>();
    Consumer<X>&Consumer<Y> ucons = consxy;
    
    interface WithParam<T> {
    	shared T get() { throw; }
    }
    class WithUnionArg<U,V>() satisfies WithParam<U|V> {}
    class WithIntersectionArg<U,V>() satisfies WithParam<U&V> {}
    class WithMoreIntersectionArg<U,V,W>() satisfies WithParam<U&V&W> {}
    WithParam<String&X|String&Y> wp1 = WithIntersectionArg<String,X|Y>();
    WithParam<String&X|String&Y> wp2 = WithUnionArg<String&X,String&Y>();
    WithParam<String&X|String&Y|Float&X|Float&Y> wp3 = WithIntersectionArg<String|Float,X|Y>();
    WithParam<String&X&Float|String&Y&Float|String&X&Integer|String&Y&Integer> wp4 = WithMoreIntersectionArg<String,X|Y,Float|Integer>();
    
    @type["String&Intersection.X|String&Intersection.Y"] WithIntersectionArg<String,X|Y>().get();
    @type["String&Intersection.X|String&Intersection.Y|Float&Intersection.X|Float&Intersection.Y"] WithIntersectionArg<String|Float,X|Y>().get();
    
    interface One {}
    interface Two {}
    object one satisfies One {}
    object onetwo satisfies One&Two {}
    
    @type["Sequence<Intersection.One&Intersection.Two>"] Sequence<One>&Sequence<Two> seq = { onetwo };
    @type["Nothing|Intersection.One&Intersection.Two"] value item = seq[0];
    @type["Intersection.One&Intersection.Two"] value fst = seq.first;
    @type["Iterator<Intersection.One&Intersection.Two>"] value itr = seq.iterator;
    
    @type["Intersection.Consumer<Intersection.One|Intersection.Two>"] Consumer<One>&Consumer<Two> cons = Consumer<One|Two>();
    cons.consume(onetwo);
    cons.consume(one);
    One|Two unk = one;
    cons.consume(unk);
    if (is One&Two unk) {
        //todo
        @type["Intersection.One&Intersection.Two"] value unkv = unk;
        Two two = unk;
    }
    
    class I<T>(T t) {} 
    
    A&B intersect<A,B>(A a, B b) { throw; }
    @type["Bottom"] intersect(1, "hello");
    @type["Bottom"] intersect(null, {"hello"});
    @type["Integer"] intersect(1, 3);
    @type["Integer&Sequence<String>"] intersect(1, {"hello"});
    @type["Bottom"] intersect(I({"hello"}), I({}));
    
    interface I1 {} 
    interface I2 {}

    void meth(I1[]&I2[] seq) {
        @type["Nothing|Intersection.I1&Intersection.I2"] value item = seq[4];
    }
    
    Integer m1 = max({1, 2, 3});
    Nothing m2 = max({});
    Integer? m3 = max(join({},{1, 2, 3}));
    Integer? m4 = max({1, 2, 3}.filter((Integer i) i>0));
    @type["Integer"] max({1, 2, 3});
    @type["Nothing"] max({});
    @type["Nothing|Integer"] max(join({},{1, 2, 3}));
    @type["Nothing|Integer"] max({1, 2, 3}.filter((Integer i) i>0));
    
}