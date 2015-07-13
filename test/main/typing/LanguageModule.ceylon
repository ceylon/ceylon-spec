class LanguageModule() {
    
    @type:"Iterable<Entry<Integer,String>,Nothing>" value e1 = {"hello", "world"}.indexed;
    @type:"Iterable<Entry<Integer,String>,Nothing>" value e2 = ["hello", "world"].indexed;
    for (Integer i->String s in {"hello", "world", "!"}.indexed) {}
    
    //print(append({"one", "two" , "three"}, "four").size==4);
    print(["one", "two" , "three"].withTrailing("four").size==4);
    
    if ("hello".sequence() nonempty) {}
    List<Character> chars = "hello";
    
    [Integer*] ints2 = [];
    {Integer*} ints1 = {};
    
    Integer m1 = min { 1, 2 };
    Null n1 = min {};
    @type:"Null|Integer" min {*ints1};
    @type:"Null|Integer" min {*ints2};
    
    Integer m2 = min([1, 2]);
    Null n2 = min([]);
    Integer m3 = min({1, 2});
    Null n3 = min({});
    @type:"Null|Integer" min(ints1);
    @type:"Null|Integer" min(ints2);
    
    @type:"Empty|Tuple<Integer,Integer,Empty>" emptyOrSingleton(1);
    @type:"Empty|Tuple<Nothing,Nothing,Empty>" emptyOrSingleton(null);
    @type:"Empty|Tuple<Integer,Integer,Empty>" emptyOrSingleton(1==1 then 1);

}

@error class Dumbthing() extends Anything() {}
@error class Numbthing() extends Null() {}
