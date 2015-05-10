interface Emptyish {
    shared formal Boolean isEmpty;
}

interface Sized satisfies Emptyish&Category { 
    shared formal Integer elementCount;
}

interface Seq1<T> {
    shared formal T? first;
}
interface Seq2<T> satisfies Seq1<T> {
    shared actual formal T first;
}

class Is() {
    
    interface SimpleContainer<T> 
        given T satisfies Number<T> {}
    
    Seq1<String>&Sized strings = nothing;
    
    if (is Seq2<String> strings) {
        String s = strings.first;
    }
    @error if (is Seq2 strings) {}
    if (is SimpleContainer<String> strings) {}
    @error if (is Is.SimpleContainer strings) {}
    if (is Is.SimpleContainer<String> strings) {}
    @error if (is Is.SimpleContainer<String,Integer> strings) {}

    if (strings is Seq2<String>) {
        @error String s = strings.first;
    }
    @error if (strings is Seq2) {}
    @error if (strings is SimpleContainer<String>) {}
    @error if (strings is Is.SimpleContainer) {}
    @error if (strings is Is.SimpleContainer<String>) {}
    @error if (strings is Is.SimpleContainer<String,Integer>) {}

    void method<T>() {
        if (is T strings) {}
        if (strings is T) {}
    }
    
    Correspondence<Integer,String> c = nothing;
    if (is Sized c) {
        String? s = c[0];
        Integer elementCount = c.elementCount;
        Boolean isEmpty = c.isEmpty;
        if ("hello" in c) {}
        //@error for (String str in c) {}
        @type:"Correspondence<Integer,String>&Sized" value cc = c;
    }
    Correspondence<Integer,String> d = nothing;
    if (is Sized&Container<Anything> d) {
        String? s = d[0];
        @error Object? f1 = d.first;
        Anything f2 = d.first;
        @error Integer size = e.size;
        @error Boolean empty = e.empty;
        Integer elementCount = d.elementCount;
        Boolean isEmpty = d.isEmpty;
        if ("hello" in d) {}
        //@error for (String str in d) {}
        @type:"Correspondence<Integer,String>&Sized&Container<Anything,Null>" value dd = d;
    }
    if (is Emptyish&Container<String> d) {
        String? s = d[0];
        String? f1 = d.first;
        Anything f2 = d.first;
        @error Integer size = e.size;
        @error Boolean empty = e.empty;
        @error Integer elementCount = d.elementCount;
        Boolean isEmpty = d.isEmpty;
        if ("hello" in d) {}
        //@error for (String str in d) {}
        @type:"Correspondence<Integer,String>&Emptyish&Container<String,Null>" value dd = d;
    }
    if (is Sized&Category d) {
        String? s = d[0];
        @error Integer size = e.size;
        @error Boolean empty = e.empty;
        Integer elementCount = d.elementCount;
        Boolean isEmpty = d.isEmpty;
        if ("hello" in d) {}
        //@error for (String str in d) {}
        @type:"Correspondence<Integer,String>&Sized" value dd = d;
    }
    
    Correspondence<Integer,String> e = nothing;
    if (is Sized&Iterable<String> e) {
        String? s = e[0];
        Integer elementCount = e.elementCount;
        Boolean isEmpty = e.isEmpty;
        if ("hello" in e) {}
        for (String str in e) {}
        @type:"Correspondence<Integer,String>&Sized&Iterable<String,Null>" value ee = e;
    }
    if (is Sized&Category e) {
        String? s = e[0];
        Integer elementCount = e.elementCount;
        Boolean isEmpty = e.isEmpty;
        if ("hello" in e) {}
        //@error for (String str in e) {} 
        @type:"Correspondence<Integer,String>&Sized" value ee = e;
    }
    if (is Sized&{String*} e) {
        String? s = e[0];
        Integer size = e.size;
        Boolean empty = e.empty;
        Integer elementCount = e.elementCount;
        Boolean isEmpty = e.isEmpty;
        if ("hello" in e) {}
        for (String str in e) {} 
        @type:"Correspondence<Integer,String>&Sized&Iterable<String,Null>" value ee = e;
    }
    if (is String[] e) {
        String? s = e[0];
        Integer size = e.size;
        Boolean empty = e.empty;
        @error Integer elementCount = e.elementCount;
        @error Boolean isEmpty = e.isEmpty;
        if ("hello" in e) {} 
        for (String str in e) {}
        @type:"Sequential<String>" value ee = e;
    }
    
    //Boolean b1 = is Sized&Iterable<String> e;
    //Boolean b2 = is Sized|Category e;
    //Boolean b3 = is Sized&{String*} e;
    //Boolean b4 = is <Sized|Category>&Iterable<Object> e;
    //Boolean b5 = is [String*] e;
    //Boolean b6 = is String[] e;
    //Boolean b7 = is [String,Integer] e;
    //Boolean b8 = is String() e;
    //Boolean b9 = is String(Integer) e;
    //Boolean b10 = is String? e;
    //Boolean b11 = is <Sized|Category> e;
    
    Boolean c1 = e is Sized&Iterable<String>;
    Boolean c2 = e is Sized|Category;
    Boolean c3 = e is Sized&{String*};
    Boolean c4 = e is <Sized|Category>&Iterable<Object>;
    Boolean c5 = e is [String*];
    Boolean c6 = e is String[];
    Boolean c7 = e is [String,Integer];
    Boolean c8 = e is String();
    Boolean c9 = e is String(Integer);
    @error Boolean c10 = e is String?;
    Boolean c11 = e is <Sized|Category>;
    
    String? ss = null;
    
    switch (ss)
    case (is String) {
        process.writeLine(ss);
    }
    case (null) {}

    switch (ss)
    case (is String) {
        process.writeLine(ss);
    }
    else {}
    
    void m(String s) {
        @error if (is String s) { }
        @error if (is Integer s) { }
        @error value b = s is String;
        @error value c = s is Integer;
    }
    
    value next = "hello".iterator().next();
    if (!is Finished next) {
        Character char = next;
    }
    if (!is Finished ch = next) {
        Character char = ch;
    }
    @error if (!is Object next) {}
    @error if (!is Null next) {}
    
    Identifiable? i = null;
    if (is Category cat = i) {
        Identifiable&Category ic = cat;
    }
    
    void notIs<T>(T* ts) {
        value next = ts.iterator().next();
        if (!is Finished next) {
            T tt = next;
        }
        if (!is Finished t = next) {
            T tt = t;
        }
        if (!is T next) {
            Finished f = next;
        }
        if (!is T t = next) {
            Finished f = t;
        }
    }
}