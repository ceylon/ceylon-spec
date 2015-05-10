interface DefiniteSpecification {
    
    class X() {}
    void doSomething() {}
    void doSomethingElse() {}
    void doNull() {}
    void use(X x) {}
    Boolean testSomething()  { return 1>100; }
    
    //void methods:
    
    void goodMethodWithNoSpec() {
        X x;
        doSomething();
        doSomethingElse();
    }
    
    void badMethodWithNoSpec() {
        X x;
        doSomething();
        doSomethingElse();
        @error use(x);
    }
    
    void goodMethodWithSpec() {
        X x;
        doSomething();
        x = X();
        doSomethingElse();
        use(x);
    }
    
    void badMethodWithSpec() {
        X x;
        doSomething();
        @error use(x);
        doSomethingElse();
        x = X();
    }
    
    void badMethodWithRepeatedSpec() {
        X x;
        doSomething();
        x = X();
        doSomethingElse();
        @error x = X();
    }
    
    void goodMethodWithRecursiveSpec() {
        X x {
            @error return x;
        }
        doSomething();
        use(x);
        doSomethingElse();
    }
    
    void goodMethodWithRecursiveSpec2() {
        X x() {
            return x();
        }
        doSomething();
        use(x());
        doSomethingElse();
    }
    
    void badMethodWithRecursiveSpec() {
        X x;
        doSomething();
        @error x = x;
        doSomethingElse();
        use(x);
    }
    
    void badMethodWithRecursiveSpec2() {
        @error X x = x;
        doSomething();
        use(x);
    }
    
    void badMethodWithMutuallyRecursiveDef() {
        X y { @error return x; }
        X x { return y; }
        use(x);
    }
    
    void goodMethodWithRecursiveDef() {
        X y { return X(); }
        X x { return y; }
        use(x);
    }
    
    void goodMethodWithRecursiveSpec3() {
        X y = X();
        X x = y;
        use(x);
    }
    
    void goodMethodWithSpecInIf() {
        X x;
        if (testSomething()) {
            doSomething();
            x = X();
            doSomethingElse();
            use(x);
        }
        doNull();
    }
    
    void badMethodWithSpecInIf() {
        X x;
        if (testSomething()) {
            doSomething();
            x = X();
            doSomethingElse();
        }
        doNull();
        @error use(x);
    }
    
    void goodMethodWithSpecInIf2() {
        X x;
        if (testSomething()) {
            x = X();
            doSomething();
            use(x);
        }
        else {
            doSomethingElse();
        }
        doNull();
    }
    
    void badMethodWithSpecInIf2() {
        X x;
        if (testSomething()) {
            doSomething();
            x = X();
        }
        else {
            doSomethingElse();
            @error use(x);
        }
        doNull();
    }
    
    void badMethodWithSpecInIf3() {
        X x;
        if (testSomething()) {
            doSomething();
            x = X();
            use(x);
        }
        else {
            doSomethingElse();
        }
        doNull();
        @error x = X();
    }
    
    void badMethodWithSpecInIf4() {
        X x = X();
        doNull();
        if (testSomething()) {
            doSomething();
            @error x = X();
        }
    }
    
    void badMethodWithSpecInIf5() {
        X x;
        doNull();
        x = X();
        if (testSomething()) {
            doSomething();
            @error x = X();
        }
    }
    
    void badMethodWithSpecInIfAndElseOnly() {
        X x;
        if (testSomething()) {
            doSomething();
            x = X();
        }
        else if (3<20) {
            doSomethingElse();
        }
        else {
            x = X();
        }
        @error use(x);
    }
    
    void badMethodWithSpecInIfAndElseIfOnly() {
        X x;
        if (testSomething()) {
            doSomething();
            x = X();
        }
        else if (3<20) {
            x = X();
        }
        else {
            doSomethingElse();
        }
        @error use(x);
    }
    
    void goodMethodWithSpecInNestedIf() {
        X x;
        if (testSomething()) {
            x = X();
            if (testSomething()) {
                doSomething();
                use(x);
            }
            else {
                use(x);
            }
        }
        else {
            doSomethingElse();
        }
    }
    
    void badMethodWithSpecInNestedIf() {
        X x;
        if (testSomething()) {
            if (testSomething()) {
                x = X();
                doSomething();
                use(x);
            }
            else {
                @error use(x);
            }
        }
        else {
            doSomethingElse();
        }
    }
        
    void goodMethodWithSpecInElse() {
        X x;
        if (testSomething()) {
            doSomething();
        }
        else {
            x = X();
            doNull();
            use(x);
        }
        doSomethingElse();
    }
    
    void badMethodWithSpecInElse() {
        X x;
        if (testSomething()) {
            doSomething();
            @error use(x);
        }
        else {
            x = X();
            doNull();
            use(x);
        }
        doSomethingElse();
    }
    
    void badMethodWithSpecInElse2() {
        X x;
        if (testSomething()) {
            doSomething();
        }
        else {
            x = X();
            doNull();
            use(x);
        }
        doSomethingElse();
        @error x = X();
    }
    
    void badMethodWithSpecInElse3() {
        X x = X();
        doSomethingElse();
        if (testSomething()) {
            doSomething();
        }
        else {
            @error x = X();
            doNull();
        }
    }
    
    void badMethodWithSpecInElse4() {
        X x;
        doSomethingElse();
        x = X();
        if (testSomething()) {
            doSomething();
        }
        else {
            @error x = X();
            doNull();
        }
    }
    
    void goodMethodWithSpecInIfAndElse() {
        X x;
        if (testSomething()) {
            x = X();
            doSomething();
            use(x);
        }
        else {
            x = X();
            doSomethingElse();
            use(x);
        }
    }
    
    void goodMethodWithSpecInIfAndElse2() {
        X x;
        if (testSomething()) {
            doSomething();
            x = X();
        }
        else {
            doSomethingElse();
            x = X();
        }
        use(x);
    }
    
    void goodMethodWithSpecInIfAndElseAndElseIf() {
        X x;
        if (testSomething()) {
            doSomething();
            x = X();
        }
        else if (3<20) {
            x = X();
        }
        else {
            doSomethingElse();
            x = X();
        }
        use(x);
    }
    
    void goodMethodWithRecursiveSpecInIfAndElse() {
        X y;
        if (testSomething()) {
            y = X();
        }
        else {
            y = X();
            use(y);
        }
        X x = y;
        use(x);
    }
    
    void badMethodWithRecursiveSpecInIf() {
        X y;
        if (testSomething()) {
            y = X();
        }
        @error X x = y;
    }
    
    void goodMethodWithSpecInFor() {
        for (X x in {X()}) {
            X y;
            doSomething();
            y=X();
        }
        doNull();
    }
    
    void badMethodWithSpecInFor() {
        X y;
        for (X x in {X()}) {
            doSomething();
            @error y=X();
        }
        doNull();
    }
    
    void goodMethodWithSpecInFail() {
        X y;
        for (X x in {X()}) {
            doSomething();
        }
        else {
            doSomethingElse();
            y = X();
        }
        doNull();
        use (y);
    }
        
    void badMethodWithSpecInFail() {
        for (X x in {X()}) {
            X y;
            doSomething();
        }
        else {
            doSomethingElse();
            @error y = X();
        }
        doNull();
    }
    
    void badMethodWithSpecInFail2() {
        X y;
        for (X x in {X()}) {
            doSomething();
            break;
        }
        else {
            doSomethingElse();
            y = X();
        }
        doNull();
        @error use (y);
    }
    
    void goodMethodWithSpecInWhile() {
        while (testSomething()) {
            X x;
            doSomething();
            x = X();
        }
        doSomethingElse();
    }
    
    void badMethodWithSpecInWhile() {
        X x;
        while (testSomething()) {
            doSomething();
            @error x = X();
        }
        doSomethingElse();
    }
    
    /*void goodMethodWithSpecInDo() {
        do {
            X x;
            doSomething();
            x = X();
        }
        while (testSomething());
    }
    
    void badMethodWithSpecInDo() {
        X x;
        do {
            doSomething();
            @error x = X();
        }
        while (testSomething());
    }*/
    
    class E() extends Exception() {}
    
    void try1() {
        X x;
        try {
            x = X();
        }
        finally {}
        use(x);
    }

    void tryCatch1() {
        X x;
        try {
            x = X();
            use(x);
        }
        catch (Exception e) {
            @error x = X();
        }
        @error use(x);
    }

    void tryCatch2() {
        X x;
        try {
        }
        catch (Exception e) {
            x = X();
        }
        @error use(x);
    }

    void tryCatch3() {
        X x;
        try {
            x = X();
        }
        catch (Exception e) {
        }
        @error use(x);
    }

    void tryCatchCatch1() {
        X x;
        try {
            x = X();
        }
        catch (E e) {
            @error x = X();
        }
        catch (Exception e) {
            @error x = X();
        }
        @error use(x);
    }

    void tryCatchCatch2() {
        X x;
        try {
            x = X();
        }
        catch (E e) {
            @error x = X();
        }
        catch (Exception e) {
        }
        @error use(x);
    }

    void tryFinally1() {
        X x;
        try {
        }
        finally {
            x = X();
        }
        use(x);
    }

    void tryFinally2() {
        X x;
        try {
            x = X();
        }
        finally {
            @error x = X();
        }
        use(x);
    }

    void tryFinally3() {
        X x;
        try {
            x = X();
        }
        finally {
            @error use(x);
        }
        use(x);
    }

    void tryCatchFinally1() {
        X x;
        try {
        }
        catch (Exception e) {
        }
        finally {
            x = X();
        }
        use(x);
    }

    void tryCatchFinally2() {
        X x;
        try {
        }
        catch (Exception e) {
            x = X();
        }
        finally {
            @error x = X();
        }
        @error use(x);
    }
    
    void switchCase0() {
        Boolean b = true;
        String s;
        switch (b)
        case (true) {
            s = "hello";
        }
        case (false) {
            s = "there";
            print(s);
        }
        //TODO: remove
        else {
            s = "world";
        }
        print(s);
    }
    
    void switchCase1() {
        Boolean b = true;
        String s;
        switch (b)
        case (true) {
            s = "hello";
        }
        case (false) {
            return;
        }
        //TODO: remove
        else {
            s = "world";
        }
        print(s);
    }
    
    void switchCase2() {
        Boolean b = true;
        String s;
        switch (b)
        case (true) {
            s = "hello";
        }
        case (false) {
        }
        //TODO: remove
        else {
            s = "world";
        }
        @error print(s);
    }
    
    void switchCase3() {
        Boolean b = true;
        String s;
        switch (b)
        case (true) {
            s = "hello";
        }
        case (false) {
            s = "world";
        }
        //TODO: remove
        else {
        }
        @error print(s);
    }
    
    class Super(String s) {}
    @error class Sub() extends Super(name) {
        String name = "gavin";
    }

}

void localFatArrows() {
    @error Integer fi => foo;
    Integer fo { @error return foo; }
    @error Integer foo => foo;
    Integer bar() => bar();
    @error Integer baz = baz;
    @error Integer qux() = qux();
    Integer fee { @error return fee; }
    Integer fum() { return fum(); }
}
class LocalFatArrows() {
    @error Integer fi => foo;
    Integer fo { @error return foo; }
    @error Integer foo => foo;
    Integer bar() => bar();
    @error Integer baz = baz;
    @error Integer qux() = qux();
    Integer fee { @error return fee; }
    Integer fum() { return fum(); }
}

void moreDefiniteSpec() {
    class Count(shared Integer count){}
    void fold2() {}
    Integer integer=0;
    @error integer++;
    @error print(integer=10);
    @error print++;
    @error print=nothing;
    @error print(print=nothing);
    @error fold2++;
    @error fold2=nothing;
    @error print(fold2=nothing);
    @error Count(12).count++;
    @error Count(12).count=1;
    @error print(Count(12).count=1);

}

class Trompon() {
    shared String name;
    this.name = "Trompon";
    print(name);
    print(this.name);
    string=>name;
    shared String getName();
    this.getName() => name.uppercased;
}
