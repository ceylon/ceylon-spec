@error annotation class NonfinalAnnotation() 
        satisfies OptionalAnnotation<NonfinalAnnotation> {}
@error final annotation class GenericAnnotation<T>() 
        satisfies OptionalAnnotation<GenericAnnotation<T>> {}
@error final annotation class NonemptyAnnotation() 
        satisfies OptionalAnnotation<NonemptyAnnotation> { 
    print("hello");
}
@error final annotation class NonemptyAnnotationBug() 
        satisfies OptionalAnnotation<NonemptyAnnotationBug> { 
    String hello="hello";
}

final annotation class ParameterizedAnnotation
        (int,float,char,str,bool,ann,iter,seq) 
        satisfies OptionalAnnotation<ParameterizedAnnotation> {
    Integer int; Float float; Character char; String str; Boolean bool;
    ParameterizedAnnotation ann;
    {String*} iter;
    Float[] seq;
}

final annotation class BrokenParameterizedAnnotation1(@error Integer|Float num) 
        satisfies OptionalAnnotation<BrokenParameterizedAnnotation1> {}
final annotation class BrokenParameterizedAnnotation2(@error Object obj) 
        satisfies OptionalAnnotation<BrokenParameterizedAnnotation2> {}
final annotation class BrokenParameterizedAnnotation3(@error {String|Character*} iter) 
        satisfies OptionalAnnotation<BrokenParameterizedAnnotation3> {}
final annotation class BrokenParameterizedAnnotation4(@error [Float|Integer*] seq) 
        satisfies OptionalAnnotation<BrokenParameterizedAnnotation4> {}
final annotation class BrokenParameterizedAnnotation5(@error [Float,Integer] tup) 
        satisfies OptionalAnnotation<BrokenParameterizedAnnotation5> {}


@error annotation GenericAnnotation<T> genericAnnotation() 
        => GenericAnnotation<T>();
@error annotation NonemptyAnnotation nonemptyAnnotation() {
    print("hello"); 
    return NonemptyAnnotation();
}

@error annotation NonemptyAnnotationBug nonemptyAnnotationBug() {
    String hello = "hello"; 
    return NonemptyAnnotationBug();
}

annotation ParameterizedAnnotation parameterizedAnnotation1
        (int,float,char,str,bool,ann,iter,seq) {
    Integer int; Float float; Character char; String str; Boolean bool;
    ParameterizedAnnotation ann;
    {String*} iter;
    Float[] seq;
    return ParameterizedAnnotation(int,float,char,str,bool,ann,iter,seq);
}

annotation ParameterizedAnnotation parameterizedAnnotation2
        (Integer int,
        Float float, 
        Character char, 
        String str, 
        Boolean bool,
        ParameterizedAnnotation ann,
        {String*} iter, 
        Float[]seq) => 
        ParameterizedAnnotation(int,float,char,str,bool,ann,iter,seq);

annotation BrokenParameterizedAnnotation1
        brokenParameterizedAnnotation1(@error Integer|Float num) 
        => BrokenParameterizedAnnotation1(num);
annotation BrokenParameterizedAnnotation2
        brokenParameterizedAnnotation2(@error Object obj) 
        => BrokenParameterizedAnnotation2(obj);
annotation BrokenParameterizedAnnotation3
        brokenParameterizedAnnotation3(@error {String|Character*} iter) 
        => BrokenParameterizedAnnotation3(iter);
annotation BrokenParameterizedAnnotation4
        brokenParameterizedAnnotation4(@error [Float|Integer*] seq) 
       => BrokenParameterizedAnnotation4(seq);
annotation BrokenParameterizedAnnotation5
        brokenParameterizedAnnotation5(@error [Float,Integer] tup) 
        => BrokenParameterizedAnnotation5(tup);

Float float = 0.0;
final annotation class Ann(shared Float float) satisfies OptionalAnnotation<Ann> {}
annotation Ann ann1() => Ann(0.0);
@error annotation Ann broke1() => Ann(0.0/2.0);
@error annotation Ann broke2() => Ann(0.float);
@error annotation Ann broke3() => Ann(float);
@error annotation Ann broke4() => Ann(max({1.0}));
annotation Ann ann2(Float float = 0.0) => Ann(float);
annotation Ann broke5(@error Float float = 0.float) => Ann(float);

final annotation class AnnAnn(shared Ann ann) satisfies OptionalAnnotation<AnnAnn> {}
annotation AnnAnn ann3() => AnnAnn(Ann(0.0));
@error annotation AnnAnn broke6() => AnnAnn(Ann(0.0/2.0));
@error annotation AnnAnn broke7() => AnnAnn(Ann(0.float));
@error annotation AnnAnn broke8() => AnnAnn(Ann(float));
annotation AnnAnn ann4(Ann ann = Ann(0.0)) => AnnAnn(ann);
annotation AnnAnn broke9(@error Ann ann = Ann(float)) => AnnAnn(ann);


