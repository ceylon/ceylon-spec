class Canicalization() {
    
    interface X<T>{
        shared formal Iterable<T&Container> get;
    }
    
    interface Y{}
    
    X<Y> xy = bottom;
    @type["Iterable<Canicalization.Y&Container>"] value v1 = xy.get;
    X<Bottom> xb = bottom;
    @type["Iterable<Bottom>"] value v2 = xb.get;
    X<String> xs = bottom;
    @type["Iterable<String>"] value v3 = xs.get;
    X<Integer|String> xios = bottom;
    @type["Iterable<Integer&Container|String>"] value v4 = xios.get;
    X<Integer&String> xias = bottom;
    @type["Iterable<Bottom>"] value v5 = xias.get;
    X<Y&String> xyas = bottom;
    @type["Iterable<Canicalization.Y&String>"] value v6 = xyas.get;
    
}