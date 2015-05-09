class OuterTypeArgs() {
	class Foo<T>() given T satisfies Object {
		shared default class Bar<S>() given S satisfies Object {
			Bar<S> b0 = Bar<S>();
			@error Bar<S> b1 = Foo<Integer>().Bar<S>();
			Bar<S> b2 = Foo<T>().Bar<S>();
			shared default Bar<S> get() {
				return this;
			}
			shared T getT() {
				throw;
			}
			shared class Qux<R>() {
				shared Bar<S> bar {
					return outer;
				}
				shared Entry<T,S> entry(T t, S s) {
					return t->s;
				}
			}
			void method() {
				class Inner() extends Qux<String>() {}
				Foo<T>.Bar<S>.Qux<String> good = Inner();
				Bar<S>.Qux<String> ok = Inner();
				Qux<String> stillok = Inner();
				@error Foo<String>.Bar<S>.Qux<String> bad = Inner();
				@error Foo<T>.Bar<Integer>.Qux<String> alsobad = Inner();
				Entry<T,S> entry(T t, S s) {
					return Inner().entry(t,s);
				}
			}
		}
	}
	class Baz<F>() extends Foo<F>() given F satisfies Object {}
	class Fum<T>() extends Foo<T>() given T satisfies Object {
		shared actual class Bar<S>() extends super.Bar<S>() 
		        given S satisfies Object {
			shared actual Bar<S> get() {
				return this;
			}
		}
	}
	Baz<String>.Bar<Integer> foobar = Baz<String>().Bar<Integer>();
	@type:"OuterTypeArgs.Foo<String>.Bar<Integer>" value fbg = foobar.get();
	@type:"String" value fbgt = foobar.getT();
	Baz<Float>.Bar<String>.Qux<Object> foobarqux = Baz<Float>().Bar<String>().Qux<Object>();
	@type:"OuterTypeArgs.Foo<Float>.Bar<String>" value fbqg = foobarqux.bar;
	@type:"Entry<Float,String>" foobarqux.entry(1.0, "hello");

	Fum<String>.Bar<Integer> fumbar = Fum<String>().Bar<Integer>();
	@type:"OuterTypeArgs.Fum<String>.Bar<Integer>" value fmbg = fumbar.get();
	Fum<String>.Bar<Integer>.Qux<Integer> fumbarqux = Fum<String>().Bar<Integer>().Qux<Integer>();
	@type:"OuterTypeArgs.Foo<String>.Bar<Integer>" value fmbqg = fumbarqux.bar;
	@type:"Entry<String,Integer>" fumbarqux.entry("given", 30);
	
	Foo<String>.Bar<Integer> fb1 = fmbg;
	Foo<String>.Bar<Integer> fb2 = fbg; 
	Fum<String>.Bar<Integer> fb3 = fmbg;
	@error Fum<String>.Bar<Integer> fb4 = fbg; 
	Baz<String>.Bar<Integer> fb5 = fmbg;
	Baz<String>.Bar<Integer> fb6 = fbg;
	
	Baz<Object>.Bar<String> bazbarobj = Baz<Object>().Bar<String>();
	@error Baz<Integer>.Bar<String> bazbarnat = bazbarobj;
	
	class Outer<out T>(T t) {
		shared class Inner<out S>(S s) {
			shared Outer<T>.Inner<S> bar() { return this; }
		}
	}
	
	@type:"OuterTypeArgs.Outer<String>.Inner<String>" Outer("hello").Inner("world");
	@type:"OuterTypeArgs.Outer<String>.Inner<String>" Outer("hello").Inner("world").bar();
	Outer<Object>.Inner<String> oiobj = Outer("hello").Inner("world");
	Outer<String>.Inner<Object> oiobj2 = Outer("hello").Inner("world");
	@error Outer<String>.Inner<String> oistr = oiobj;
	@error Outer<Integer>.Inner<String> oinat = Outer("hello").Inner("world");

	class Consumer<in T>(void consume(T t)) {
		shared class Inner<out S>(S s) {
			shared S foo(T t) { return s; }
		}
	}
	
	@type:"OuterTypeArgs.Consumer<String>.Inner<String>" Consumer(void (String s) {}).Inner("world");
	@type:"String" Consumer(void (String s) {}).Inner("world").foo("hello");
	Consumer<Nothing>.Inner<String> ciobj = Consumer(void (String s) {}).Inner("world");
	Consumer<String>.Inner<Object> ciobj2 = Consumer(void (String s) {}).Inner("world");
	@error Consumer<String>.Inner<String> cistr = ciobj;
	@error Consumer<Integer>.Inner<String> cinat = Consumer(void (String s) {}).Inner("world");
	
	class Contains<out T>() {
	    shared default class Contained() {}
	}
	class Extends1() extends Contains<String>() {}
    class Extends2() extends Contains<Object>() {}
    class Refines() extends Extends2() {
        shared actual class Contained() 
            extends super.Contained() {}
    }
    @error Refines.Contained rc = Extends1().Contained(); 
    Extends2.Contained ec = Extends1().Contained(); 
    @error Extends1.Contained eco = Extends2().Contained(); 

}

void qualifyingTypeParameters() {
    class X() {}
    class Y() extends X() {
        shared String hello = "hello";
    }
    
    class Sup() {
        shared default class XX() => X();
    }
    
    class Sub() extends Sup() {
        shared actual class XX() => Y();
    }
    
    @error S.XX fn<S>(S s) 
            given S satisfies Sup 
            => s.XX();
    
    void test() {
        @type:"Sup.XX" value supXX = fn(Sup());  //inferred type Sup.XX
        //@type:"Sup.XX" value subXX = fn(Sub());  //inferred type Sub.XX
        @error print(subXX.hello);  //error!
        @error Sub.XX err = subXX;  //error!
    }
}