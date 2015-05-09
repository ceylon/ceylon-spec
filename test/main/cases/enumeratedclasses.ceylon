interface Sized { 
    shared formal Integer size;
    shared default Boolean empty => size==0;  
}

abstract class X<out T>() of Y<T> | Z<T> | W {}
class Y<out T>() extends X<T>() satisfies Sized {
    shared actual Integer size = 0;
}
class Z<out T>() extends X<T>() {
    shared String name = "gavin";
}
class W() extends X<Nothing>() {}

@error class A<T>() extends X<T>() {} 

void switchX(X<String> x) {
    void do(W w) {}
    switch (x) 
    case (is Y<String>) { print(x.size); }
    case (is Z<String>) { print(x.name); }
    case (is W) { do(x); }
}

@error abstract class Circ() of Circ {}
void switchCirc(Circ c) {
    switch (c)
    case (is Circ) {}
}

@error abstract class DupeEnum() of DupeCase|DupeCase {}
@error class DupeCase() extends DupeEnum() {}

interface DupeSuper {}
@error abstract class DupeSub() satisfies DupeSuper&DupeSuper {}


@error abstract class AaAa() of BbBb<Integer> {}
class BbBb<Type>(Type val) extends AaAa() {}

@error class WithUnionCase() of <Float&Object> {}
@error class WithIntersectionCase() of <Float&Object> {}
@error class WithNothingCase() of Nothing {}