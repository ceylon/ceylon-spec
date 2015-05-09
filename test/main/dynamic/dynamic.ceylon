void members() {
    dynamic {
        dynamic it = blahblah;
        dynamic member = it.foos;
        dynamic safe = it?.foos;
        @error dynamic spread = it*.foos;
        dynamic element = it[1];
        dynamic subspan = it[1..2];
        dynamic sum = it+it;
        dynamic product = it*2;
        dynamic eq = it==1;
        dynamic compare = it>it;
        @error dynamic measure = it:3;
        @error dynamic span = it..it;
        @error dynamic entry = it->it;
        @error dynamic iter = { it, it };
        @error dynamic tuple = [it, it];
        dynamic b1 = a.B();
        dynamic b2 = A.B();        
    }
}

void test() {
    class Foo() {
        dynamic bar() { 
            dynamic { 
                return something; 
            } 
        }
        dynamic baz;
        dynamic {
            baz = whatever;
        } 
    }
    dynamic {
        dynamic x = dynamic [ y="hello"; ];
        //@error dynamic x0 = value { "hello"; };
        x.w = "goodbye";
        dynamic f(dynamic z) => x.y + " " + z;
        dynamic s = f(name);
        print(s.uppercased);
        for (c in s) {
            print(c.integer);
        }
        dynamic xs = dynamic [ 1, 2, 3 ];
        for (i in xs) {
            print(i^2);
        }
        String sx = x;
    }
}

T foo<T>(T t) given T satisfies Object => t;

void run() {
    dynamic {
        dynamic x = dynamic [];
        @error value f1 = foo(x);
        value f2 = foo<Integer>(x);
        value f3 = foo(x of Integer);
        
        for (xx in x) {}
        
        switch (x)
        case (is String) {}
        case (is Integer) {}
        
        if (is String x) {}
        
        @error value sing = Singleton(x);
        @error value iter = {x};
        @error value seq = [x];
    }
}

void newit() {
    dynamic {
        dynamic nuevo = dynamic [
            x="hello";
            y="world";
            bar(dynamic name) => "oops ``name``";
        ];
        print(nuevo.x + " " + nuevo.y);
        print(nuevo.bar("dynamic"));
        dynamic aa = dynamic [ a=1; b=2; 5,6,7,8 ];
    }
}

variable String val = "";
void fun(String o) {}
String leak() {
  dynamic {
    dynamic x = dynamic [ x=1; ];
    val=x;
    fun(x);
    return x;
  }
}

void control() {
    dynamic {
        if (xyz) {}
        if (is String xyz) {
            @type:"String" value v = xyz;
        }
        if (!is String xyz) {
            @type:"unknown" dynamic v = xyz;
        }
        if (exists xyz) {
            @type:"unknown" dynamic v = xyz;
        }
        if (nonempty xyz) {
            @type:"unknown" dynamic v = xyz;
        }
        for (i in xyz) {
            @type:"unknown" dynamic v = i;
        }
        for (i->Object j in xyz) {
            @type:"unknown" dynamic v = i;
            @type:"Object" value w = j;
        }
        for (String i in xyz) {
            @type:"String" value v = i;
        }
        switch (xyz)
        case (true) {}
        case (is String) {
             @type:"String" value v = xyz;
        }
    }
}

void sanity() {
    @type:"unknown" dynamic str1 = "hello";
    @type:"unknown" dynamic str2 => "hello";
    @type:"unknown" dynamic str3 { return "hello"; }
    @type:"unknown" dynamic str4() => "hello";
    @type:"unknown" dynamic str5() { return "hello"; }
    dynamic unk() { dynamic { return whatever; } }
    dynamic unk0() { @error return whatever; }

    dynamic {
        @error value unk1 = whatever;
        @error value unk2 => whatever;
        @error value unk3 { return whatever; }
        @error function unk4() => whatever;
        @error function unk5() { return whatever; }
    }
    
    dynamic fun1(@error value val) { dynamic { return val; } }
    dynamic fun2(dynamic val) { dynamic { return val; } }
    
    @error dynamic res1 = str1;
    @error dynamic res2 = str4();
    @error dynamic res3 = str5;
    
    dynamic {
        dynamic res4 = str1;
        dynamic res5 = str4();
        dynamic res6 = str5;        
    }

    dynamic res7 { @error return str1; }
    dynamic res8 { @error return str4(); }
    dynamic res9 { @error return str5; }
    
    dynamic {
        dynamic res10 { return str1; }
        dynamic res11 { return str4(); }
        dynamic res12 { return str5; }
    }
    
    @error dynamic fes1() => str1;
    @error dynamic fes2() => str4();
    @error dynamic fes3() => str5;
    
    dynamic {
        dynamic fes4() => str1;
        dynamic fes5() => str4();
        dynamic fes6() => str5;        
    }

    dynamic fes7() { @error return str1; }
    dynamic fes8() { @error return str4(); }
    dynamic fes9() { @error return str5; }
    
    dynamic {
        dynamic fes10() { return str1; }
        dynamic fes11() { return str4(); }
        dynamic fes12() { return str5; }
    }
    
    @error value ves1 = str1;
    @error value ves2 = str4();
    @error value ves3 = str5;
    
    dynamic {
        @error value ves4 = str1;
        @error value ves5 = str4();
        @error value ves6 = str5;        
    }

    @error value ves7 { @error return str1; }
    @error value ves8 { @error return str4(); }
    @error value ves9 { @error return str5; }
    
    dynamic {
        @error value ves10 { return str1; }
        @error value ves11 { return str4(); }
        @error value ves12 { return str5; }
    }

    @error function wes1() => str1;
    @error function wes2() => str4();
    @error function wes3() => str5;
    
    dynamic {
        @error function wes4() => str1;
        @error function wes5() => str4();
        @error function wes6() => str5;        
    }

    @error function wes7() { @error return str1; }
    @error function wes8() { @error return str4(); }
    @error function wes9() { @error return str5; }
    
    dynamic {
        @error function wes10() { return str1; }
        @error function wes11() { return str4(); }
        @error function wes12() { return str5; }
    }
}

void okrefinement() {
    abstract class NodeAbs() {
        shared formal dynamic native;
        shared formal dynamic f(dynamic str);
    }
    
    class Node(dynamic n) extends NodeAbs() {
        shared actual dynamic native;
        shared actual dynamic f(dynamic str) => "";
        dynamic {
            native = n;
        }
    }
}

void badrefinement() {
    abstract class NodeAbs() {
        shared formal dynamic native;
        shared formal dynamic f(dynamic str);
    }
    
    class Node(dynamic n) extends NodeAbs() {
        @error shared actual String native;
        shared actual dynamic f(@error String str) => "";
        dynamic {
            native = n;
        }
    }
}

void worserefinement() {
    abstract class NodeAbs() {
        shared formal String native;
        shared formal dynamic f(String str);
    }
    
    class Node(dynamic n) extends NodeAbs() {
        @error shared actual dynamic native;
        shared actual dynamic f(@error dynamic str) => "";
        dynamic {
            native = n;
        }
    }
}

shared void printVariadic(dynamic args) {
     dynamic {
          printVaradic(*args);
     }
}

dynamic jsfun(dynamic arg(dynamic a)) {
    dynamic { 
        return arg(1).something;
    }
}

void calljsfun() {
    dynamic {
        dynamic anonfun = (dynamic x)=>x+1;
        dynamic result = anonfun(3);
        jsfun((a) => a.whatever);
    }
}