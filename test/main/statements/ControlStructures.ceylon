class ControlStructures() {
    Object something = "hello";
    String? name = null;
    String[] names = {};
    Entry<String,String>[] entries = { Entry("hello", "world") };
    
    void print(String name) {}
    
    if (exists name) {
        print(name);
    }

    if (exists n = name) {
        print(n);
    }
    
    @error if (is Void something) {}
    @error if (is Object something) {}
    @error if (is String s = "hello") {}
    @error if (is Object s = "hello") {}
    
    variable String? var := "gavin"; 
    @error if (exists var) {}
    if (exists v = var) {}
    
    if (nonempty names) {
        print(names.first);
        for (n in names) {
            print(n);
        }
    }

    if (nonempty ns = names) {
        print(ns.first);
        for (n in ns) {
            print(n);
        }
    }
    
    variable String[] varseq := {};
    @error if (nonempty varseq) {}
    if (nonempty vs = varseq) {}
    
    if (is String something) {
        print(something);
    }

    if (is String string = something) {
        print(string);
    }
    
    variable Object o := "hello";
    @error if (is String o) {}

    for (n in names) {
        print(n);
    }
    
    /*for (value n in names) {
        print(n);
    }*/
    
    /*for (@error function n in names) {
        print(n);
    }*/
    
    for (key->item in entries) {
        print(key + "->" + item);
    }
    
    /*for (value key->value item in entries) {
        print(key + "->" + item);
    }*/
    
    class Transaction() satisfies Closeable {
        shared void rollbackOnly() {}
        shared actual void open() {}
        shared actual void close(Exception? e) {}
    }

    try (Transaction()) {}

    try (tx = Transaction()) {
        tx.rollbackOnly();
    }
    
    Transaction tx = Transaction();
    function trans() { return tx; }
    try (tx) {}
    try (t = tx) {}
    try (Transaction t = tx) {}
    try (Transaction()) {}
    try (t = Transaction()) {}
    try (Transaction t = Transaction()) {}
    @error try (trans()) {}
    try (t = trans()) {}
    try (Transaction t = trans()) {}
    
    try {
        print("hello");
    }
    catch (e) {
        @type["String"] value msg = e.message;
        @type["Nothing|Exception"] value cause = e.cause;
    }

    class Exception1() extends Exception() {}
    class Exception2() extends Exception() {}
    
    try {
        print("hello");
    }
    catch (@type["ControlStructures.Exception1|ControlStructures.Exception2"] Exception1|Exception2 e) {
        @type["String"] value msg = e.message;
        @type["Nothing|Exception"] value cause = e.cause;
    }
    catch (@error String s) {
        
    }
    
    try {}
    catch (Exception1 e1) {}
    catch (Exception2 e2) {}
    catch (Exception e) {}
    
    try {}
    catch (@type["ControlStructures.Exception1"] Exception1 e1) {}
    catch (@error Exception1 e2) {}
    
    try {}
    catch (Exception1 e1) {}
    catch (Exception e) {}
    catch (@error Exception2 e2) {}
    
    try {}
    catch (Exception1|Exception2 e) {}
    catch (@error Exception2 e2) {}
    
    try {}
    catch (Exception1 e1) {}
    catch (@error Exception1|Exception2 e) {}
    
    @error try ("hello") {}
    @error try (Exception()) {}
    try (@error s = "hello") {}
    try (@error e = Exception()) {}
    try (@error Object t = Transaction()) {}
    try (@error Transaction trx) {}
    
    try (t = Transaction()) {
    	//do something
    	t.rollbackOnly();
    }
    catch (Exception e) {
    	@error t.rollbackOnly();
    }
    finally {
    	@error t.rollbackOnly();
    }
    
    @error while ("hello") {}
    
    /*do {
        Boolean test = false;
    }
    while (test); //TODO: is this allowed?*/
    
    /*variable Boolean test;
    do {
        test := false;
    }
    while (test);*/

    /*Boolean test2;
    @error do {
        @error test2 = false;
    }
    while (test2);*/
    
    Void v = null;
    
    switch (v)
    case (is Object) {}
    case (is Nothing) {}
    
    switch (v)
    case (is Object) {}
    case (null) {}
    
    switch (v)
    case (is Object) {}
    else {}

    switch (v)
    case (is Object|Nothing) {}

    @error switch (v)
    case (is Object|Nothing) {}
    case (null) {}

    @error switch (v)
    case (is Object|Nothing) {}
    case (is Nothing) {}

    switch (v)
    case (is Void) {}

    @error switch (v)
    case (is Object) {}
    
    Boolean b = true;
    
    switch (b)
    case (true) {}
    case (false) {}
    
    switch (b)
    case (true, false) {}
    
    switch (b)
    case (true) {}
    else {}

    @error switch (b)
    case (true) {}
    
    switch (b)
    case (is Boolean) {}
    
    String? s = null;
    
    switch (s)
    case (is String) {}
    case (is Nothing) {}
    
    @error switch (s)
    case (is Object) {}
    case (is Nothing) {}
    
    switch (s)
    case (is String) {}
    case (null) {}
    
    @error switch (s)
    case (is Nothing) {}
    case (is String) {}
    case (null) {}
    
    switch (s)
    case (is String) {}
    else {}

    @error switch (s)
    case (is String) {}

}