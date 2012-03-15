interface DefiniteReturn {
    
    void doSomething() {}
    void doSomethingElse() {}
    void doNothing() {}
    Boolean testSomething()  { return 1<2; }
    class X() {}
    
    //void methods:
    
    void voidMethodWithNoReturn() {
        doSomething();
        doSomethingElse();
    }
    
    void voidMethodWithReturn() {
        doSomething();
        return;
    }
    
    void voidMethodWithThrow() {
        doSomething();
        throw;
    }
    
    void voidMethodWithReturnInIf() {
        if (testSomething()) {
            doSomething();
            return;
        }
        doSomethingElse();
    }
    
    void voidMethodWithThrowInIf() {
        if (testSomething()) {
            doSomething();
            throw;
        }
        doSomethingElse();
    }
    
    void voidMethodWithReturnInIf2() {
        if (testSomething()) {
            doSomething();
            return;
        }
        else {
            doSomethingElse();
        }
        doNothing();
    }
    
    void voidMethodWithThrowInIf2() {
        if (testSomething()) {
            doSomething();
            throw;
        }
        else {
            doSomethingElse();
        }
        doNothing();
    }
    
    void voidMethodWithReturnInNestedIf() {
        if (testSomething()) {
            if (testSomething()) {
                doSomething();
                return;
            }
        }
        else {
            doSomethingElse();
            return;
        }
    }
    
    void voidMethodWithReturnInNestedIf2() {
        if (testSomething()) {
            if (testSomething()) {
                doSomething();
                return;
            }
        }
        else {
            doSomethingElse();
        }
        return;
    }
    
    void voidMethodWithReturnInNestedIf3() {
        if (testSomething()) {
            if (testSomething()) {
                doSomething();
                return;
            }
            else {
                doNothing();
                return;
            }
        }
        else {
            doSomethingElse();
            return;
        }
    }
    
    void voidMethodWithReturnInElse() {
        if (testSomething()) {
            doSomething();
        }
        else {
            doNothing();
            return;
        }
        doSomethingElse();
    }
    
    void voidMethodWithThrowInElse() {
        if (testSomething()) {
            doSomething();
        }
        else {
            doNothing();
            throw;
        }
        doSomethingElse();
    }
    
    void voidMethodWithReturnInIfAndElse() {
        if (testSomething()) {
            doSomething();
            return;
        }
        else {
            doSomethingElse();
            return;
        }
    }
    
    void voidMethodWithThrowInIfAndElse() {
        if (testSomething()) {
            doSomething();
            throw;
        }
        else {
            doSomethingElse();
            throw;
        }
    }
    
    void voidMethodWithStatementAfterDefiniteReturn() {
        if (testSomething()) {
            doSomething();
            return;
        }
        else {
            doSomethingElse();
            return;
        }
        @error doNothing();
    }
    
    void voidMethodWithStatementAfterDefiniteThrow() {
        if (testSomething()) {
            doSomething();
            throw;
        }
        else {
            doSomethingElse();
            throw;
        }
        @error doNothing();
    }
    
    void voidMethodWithReturnInFor() {
        for (X x in {X()}) {
            doSomething();
            return;
        }
        doNothing();
    }
    
    void voidMethodWithReturnInFor2() {
        for (X x in {X()}) {
            doSomething();
            return;
        }
        else {
            doSomethingElse();
        }
        doNothing();
    }
    
    void voidMethodWithReturnInForAndFail() {
        for (X x in {X()}) {
            doSomething();
            return;
        }
        else {
            doSomethingElse();
            return;
        }
    }
    
    void voidMethodWithReturnInWhile() {
        while (testSomething()) {
            doSomething();
            return;
        }
        doSomethingElse();
    }
    
    void voidMethodWithReturnInWhile2() {
        while (testSomething()) {
            doSomething();
            return;
        }
        doSomethingElse();
        return;
    }
    
    /*void voidMethodWithReturnInDo() {
        do {
            doSomething();
            return;
        }
        while (testSomething());
    }
    
    void voidMethodWithStatementAfterReturnInDo() {
        do {
            doSomething();
            return;
        }
        while (testSomething());
        @error doSomethingElse();
    }*/
    
    //non-void methods
    
    @error X methodWithNoReturn() {
        doSomething();
        doSomethingElse();
    }
    
    X methodWithReturn() {
        doSomething();
        return X();
    }
    
    X methodWithThrow() {
        doSomething();
        throw;
    }
    
    @error X methodWithReturnInIf() {
        if (testSomething()) {
            doSomething();
            return X();
        }
        doSomethingElse();
    }
    
    @error X methodWithThrowInIf() {
        if (testSomething()) {
            doSomething();
            throw;
        }
        doSomethingElse();
    }
    
    @error X methodWithReturnInIf2() {
        if (testSomething()) {
            doSomething();
            return X();
        }
        else {
            doSomethingElse();
        }
        doNothing();
    }
    
    @error X methodWithThrowInIf2() {
        if (testSomething()) {
            doSomething();
            throw;
        }
        else {
            doSomethingElse();
        }
        doNothing();
    }
    
    X methodWithReturnInIf3() {
        if (testSomething()) {
            doSomething();
            return X();
        }
        else {
            doSomethingElse();
        }
        doNothing();
        return X();
    }
    
    X methodWithThrowInIf3() {
        if (testSomething()) {
            doSomething();
            throw;
        }
        else {
            doSomethingElse();
        }
        doNothing();
        throw;
    }
    
    @error X methodWithReturnInNestedIf() {
        if (testSomething()) {
            if (testSomething()) {
                doSomething();
                return X();
            }
        }
        else {
            doSomethingElse();
            return X();
        }
    }
    
    X methodWithReturnInNestedIf2() {
        if (testSomething()) {
            if (testSomething()) {
                doSomething();
                return X();
            }
        }
        else {
            doSomethingElse();
        }
        return X();
    }
    
    X methodWithReturnInNestedIf3() {
        if (testSomething()) {
            if (testSomething()) {
                doSomething();
                return X();
            }
            else {
                doNothing();
                return X();
            }
        }
        else {
            doSomethingElse();
            return X();
        }
    }
    
    @error X methodWithReturnInElse() {
        if (testSomething()) {
            doSomething();
        }
        else {
            doNothing();
            return X();
        }
        doSomethingElse();
    }
    
    @error X methodWithThrowInElse() {
        if (testSomething()) {
            doSomething();
        }
        else {
            doNothing();
            throw;
        }
        doSomethingElse();
    }
    
    X methodWithReturnInIfAndElse() {
        if (testSomething()) {
            doSomething();
            return X();
        }
        else {
            doSomethingElse();
            return X();
        }
    }
    
    X methodWithThrowInIfAndElse() {
        if (testSomething()) {
            doSomething();
            throw;
        }
        else {
            doSomethingElse();
            throw;
        }
    }
    
    X methodWithReturnInIfAndElseAndElseIf() {
        if (testSomething()) {
            doSomething();
            return X();
        }
        else if (20>23) {
            return X();
        }
        else {
            doSomethingElse();
            return X();
        }
    }
    
    @error X methodWithReturnInIfAndElseIfOnly() {
        if (testSomething()) {
            doSomething();
            return X();
        }
        else if (20>23) {
            return X();
        }
        else {
            doSomethingElse();
        }
    }
    
    @error X methodWithReturnInIfAndElseOnly() {
        if (testSomething()) {
            doSomething();
            return X();
        }
        else if (20>23) {
            doSomethingElse();
        }
        else {
            return X();
        }
    }
    
    X methodWithStatementAfterDefiniteReturn() {
        if (testSomething()) {
            doSomething();
            return X();
        }
        else {
            doSomethingElse();
            return X();
        }
        @error doNothing();
    }
    
    X methodWithStatementAfterDefiniteThrow() {
        if (testSomething()) {
            doSomething();
            throw;
        }
        else {
            doSomethingElse();
            throw;
        }
        @error doNothing();
    }
    
    @error X methodWithReturnInFor() {
        for (X x in {X()}) {
            doSomething();
            return X();
        }
        doNothing();
    }
    
    X methodWithReturnInFail() {
        for (X x in {X()}) {
            doSomething();
        }
        else {
            doNothing();
            return X();
        }
    }
    
    @error X methodWithReturnInFail2() {
        for (X x in {X()}) {
            doSomething();
            break;
        }
        else {
            return X();
        }
        doNothing();
    }
    
    @error X methodWithReturnInFor2() {
        for (X x in {X()}) {
            doSomething();
            return X();
        }
        else {
            doSomethingElse();
        }
        doNothing();
    }
    
    X methodWithReturnInFor3() {
        for (X x in {X()}) {
            doSomething();
            return X();
        }
        else {
            doSomethingElse();
        }
        doNothing();
        return X();
    }
    
    X methodWithReturnInForAndFail() {
        for (X x in {X()}) {
            doSomething();
            return X();
        }
        else {
            doSomethingElse();
            return X();
        }
    }
    
    @error X methodWithReturnInWhile() {
        while (testSomething()) {
            doSomething();
            return X();
        }
        doSomethingElse();
    }
    
    X methodWithReturnInWhile2() {
        while (testSomething()) {
            doSomething();
            return X();
        }
        doSomethingElse();
        return X();
    }
    
    /*X methodWithReturnInDo() {
        do {
            doSomething();
            return X();
        }
        while (testSomething());
    }
    
    X methodWithStatementAfterReturnInDo() {
        do {
            doSomething();
            return X();
        }
        while (testSomething());
        @error doSomethingElse();
    }*/
    
    //getters
    
    @error X getterWithNoReturn {
        doSomething();
        doSomethingElse();
    }
    
    X getterWithReturn {
        doSomething();
        return X();
    }
    
    X getterWithThrow {
        doSomething();
        throw;
    }
    
    @error X getterWithReturnInIf {
        if (testSomething()) {
            doSomething();
            return X();
        }
        doSomethingElse();
    }
    
    @error X getterWithThrowInIf {
        if (testSomething()) {
            doSomething();
            throw;
        }
        doSomethingElse();
    }
    
    @error X getterWithReturnInIf2 {
        if (testSomething()) {
            doSomething();
            return X();
        }
        else {
            doSomethingElse();
        }
        doNothing();
    }
    
    @error X getterWithThrowInIf2 {
        if (testSomething()) {
            doSomething();
            throw;
        }
        else {
            doSomethingElse();
        }
        doNothing();
    }
    
    @error X getterWithReturnInNestedIf {
        if (testSomething()) {
            if (testSomething()) {
                doSomething();
                return X();
            }
        }
        else {
            doSomethingElse();
            return X();
        }
    }
    
    X getterWithReturnInNestedIf2 {
        if (testSomething()) {
            if (testSomething()) {
                doSomething();
                return X();
            }
        }
        else {
            doSomethingElse();
        }
        return X();
    }
    
    X getterWithReturnInNestedIf3 {
        if (testSomething()) {
            if (testSomething()) {
                doSomething();
                return X();
            }
            else {
                doNothing();
                return X();
            }
        }
        else {
            doSomethingElse();
            return X();
        }
    }
    
    @error X getterWithReturnInElse {
        if (testSomething()) {
            doSomething();
        }
        else {
            doNothing();
            return X();
        }
        doSomethingElse();
    }
    
    @error X getterWithThrowInElse {
        if (testSomething()) {
            doSomething();
        }
        else {
            doNothing();
            throw;
        }
        doSomethingElse();
    }
    
    X getterWithReturnInIfAndElse {
        if (testSomething()) {
            doSomething();
            return X();
        }
        else {
            doSomethingElse();
            return X();
        }
    }
    
    X getterWithThrowInIfAndElse {
        if (testSomething()) {
            doSomething();
            throw;
        }
        else {
            doSomethingElse();
            throw;
        }
    }
    
    X getterWithStatementAfterDefiniteReturn {
        if (testSomething()) {
            doSomething();
            return X();
        }
        else {
            doSomethingElse();
            return X();
        }
        @error doNothing();
    }
    
    X getterWithStatementAfterDefiniteThrow {
        if (testSomething()) {
            doSomething();
            throw;
        }
        else {
            doSomethingElse();
            throw;
        }
        @error doNothing();
    }
    
    @error X getterWithReturnInFor {
        for (X x in {X()}) {
            doSomething();
            return X();
        }
        doNothing();
    }
    
    @error X getterWithReturnInFor2 {
        for (X x in {X()}) {
            doSomething();
            return X();
        }
        else {
            doSomethingElse();
        }
        doNothing();
    }
    
    X getterWithReturnInFor3 {
        for (X x in {X()}) {
            doSomething();
            return X();
        }
        else {
            doSomethingElse();
        }
        doNothing();
        return X();
    }
    
    X getterWithReturnInForAndFail {
        for (X x in {X()}) {
            doSomething();
            return X();
        }
        else {
            doSomethingElse();
            return X();
        }
    }
    
    @error X getterWithReturnInWhile {
        while (testSomething()) {
            doSomething();
            return X();
        }
        doSomethingElse();
    }
    
    X getterWithReturnInWhile2 {
        while (testSomething()) {
            doSomething();
            return X();
        }
        doSomethingElse();
        return X();
    }
    
    /*X getterWithReturnInDo {
        do {
            doSomething();
            return X();
        }
        while (testSomething());
    }
    
    X getterWithStatementAfterReturnInDo {
        do {
            doSomething();
            return X();
        }
        while (testSomething());
        @error doSomethingElse();
    }*/
    
    //setters
    
    assign getterWithNoReturn {
        doSomething();
        doSomethingElse();
    }
    
    assign getterWithReturn {
        doSomething();
        return;
    }
    
    assign getterWithThrow {
        doSomething();
        throw;
    }
    
    assign getterWithReturnInIf {
        if (testSomething()) {
            doSomething();
            return;
        }
        doSomethingElse();
    }
    
    assign getterWithThrowInIf {
        if (testSomething()) {
            doSomething();
            throw;
        }
        doSomethingElse();
    }
    
    assign getterWithReturnInIf2 {
        if (testSomething()) {
            doSomething();
            return;
        }
        else {
            doSomethingElse();
        }
        doNothing();
    }
    
    assign getterWithThrowInIf2 {
        if (testSomething()) {
            doSomething();
            throw;
        }
        else {
            doSomethingElse();
        }
        doNothing();
    }
    
    assign getterWithReturnInNestedIf {
        if (testSomething()) {
            if (testSomething()) {
                doSomething();
                return;
            }
        }
        else {
            doSomethingElse();
            return;
        }
    }
    
    assign getterWithReturnInNestedIf2 {
        if (testSomething()) {
            if (testSomething()) {
                doSomething();
                return;
            }
        }
        else {
            doSomethingElse();
        }
        return;
    }
    
    assign getterWithReturnInNestedIf3 {
        if (testSomething()) {
            if (testSomething()) {
                doSomething();
                return;
            }
            else {
                doNothing();
                return;
            }
        }
        else {
            doSomethingElse();
            return;
        }
    }
    
    assign getterWithReturnInElse {
        if (testSomething()) {
            doSomething();
        }
        else {
            doNothing();
            return;
        }
        doSomethingElse();
    }
    
    assign getterWithThrowInElse {
        if (testSomething()) {
            doSomething();
        }
        else {
            doNothing();
            throw;
        }
        doSomethingElse();
    }
    
    assign getterWithReturnInIfAndElse {
        if (testSomething()) {
            doSomething();
            return;
        }
        else {
            doSomethingElse();
            return;
        }
    }
    
    assign getterWithThrowInIfAndElse {
        if (testSomething()) {
            doSomething();
            throw;
        }
        else {
            doSomethingElse();
            throw;
        }
    }
    
    assign getterWithStatementAfterDefiniteReturn {
        if (testSomething()) {
            doSomething();
            return;
        }
        else {
            doSomethingElse();
            return;
        }
        @error doNothing();
    }
    
    assign getterWithStatementAfterDefiniteThrow {
        if (testSomething()) {
            doSomething();
            throw;
        }
        else {
            doSomethingElse();
            throw;
        }
        @error doNothing();
    }
    
    assign getterWithReturnInFor {
        for (X x in {X()}) {
            doSomething();
            return;
        }
        doNothing();
    }
    
    assign getterWithReturnInFor2 {
        for (X x in {X()}) {
            doSomething();
            return;
        }
        else {
            doSomethingElse();
        }
        doNothing();
    }
    
    assign getterWithReturnInForAndFail {
        for (X x in {X()}) {
            doSomething();
            return;
        }
        else {
            doSomethingElse();
            return;
        }
    }
    
    assign getterWithReturnInWhile {
        while (testSomething()) {
            doSomething();
            return;
        }
        doSomethingElse();
    }
    
    assign getterWithReturnInWhile2 {
        while (testSomething()) {
            doSomething();
            return;
        }
        doSomethingElse();
        return;
    }
    
    /*assign getterWithReturnInDo {
        do {
            doSomething();
            return;
        }
        while (testSomething());
    }
    
    assign getterWithStatementAfterReturnInDo {
        do {
            doSomething();
            return;
        }
        while (testSomething());
        @error doSomethingElse();
    }*/
    
    //misc combinations
    
    class ClassWithReturn() {
        if (testSomething()) {
            return;
        }
        else {
            doSomething();
        }
        doSomethingElse();
        return;
        ClassWithReturn member() {
            return this;
        }
    }
    
    class ClassWithThrow() {
        if (testSomething()) {
            doSomething();
        }
        else {
            throw;
        }
        doSomethingElse();
        throw;
        ClassWithThrow member() {
            return this;
        }
    }
    
    class ClassWithReturns() {
        if (testSomething()) {
            return;
        }
        else {
            return;
        }
        @error doSomething();
        void member() {}
    }
    
    class ClassWithThrows() {
        if (testSomething()) {
            throw;
        }
        else {
            throw;
        }
        void member() {}
        @error return;
    }
    
    X methodWithNestedMethod() {
        function nestedMethod() {
            return X();
        }
        return nestedMethod();
    }
    
    X getterWithNestedGetter {
        value nestedGetter {
            return X();
        }
        return nestedGetter;
    }
    
    X methodWithNestedMethodWithThrow() {
        X nestedMethod() {
            throw;
        }
        return nestedMethod();
    }
    
    X getterWithNestedGetterWithThrow {
        X nestedGetter {
            throw;
        }
        return nestedGetter;
    }
    
    X methodWithNestedClass() {
        class Nested() {
            if (testSomething()) {
                @error return X();
            }
            else {
                return;
            }
        }
        return X();
    }
    
    X getterWithNestedClass {
        class Nested() {
            if (testSomething()) {
                @error return X();
            }
            else {
                return;
            }
        }
        return X();
    }
    
    X methodWithNestedClassWithThrow() {
        class Nested() {
            if (testSomething()) {
                throw;
            }
        }
        return X();
    }
    
    X getterWithNestedClassWithThrow {
        class Nested() {
            if (testSomething()) {
                throw;
            }
        }
        return X();
    }
    
    class E() extends Exception() {}
    
    void try1() {
        try {
            return;
        }
        @error testSomething();
    }
    
    void try2() {
        try {
        }
        testSomething();
    }
    
    void tryFinally1() {
        try {
        }
        catch (Exception e) {
        }
        finally {
            return;
        }
        @error testSomething();
    }
    
    void tryFinally2() {
        try {
        }
        catch (Exception e) {
            return;
        }
        finally {
        }
        testSomething();
    }
    
    void tryCatch1() {
        try {
            return;
        }
        catch (Exception e) {
            return;
        }
        @error testSomething();
    }
    
    void tryCatch2() {
        try {
        }
        catch (Exception e) {
            return;
        }
        testSomething();
    }
    
    void tryCatch3() {
        try {
            return;
        }
        catch (Exception e) {
        }
        testSomething();
    }
    
    void tryCatchCatch1() {
        try {
            return;
        }
        catch (E e) {
            return;
        }
        catch (Exception e) {
            return;
        }
        @error testSomething();
    }
    
    void tryCatchCatch2() {
        try {
            return;
        }
        catch (E e) {
        }
        catch (Exception e) {
            return;
        }
        testSomething();
    }
    
    void switchCase1() {
        Boolean b = true;
        switch (b)
        case (true) {
            return;
        }
        case (false) {
            return;
        }
        //TODO: remove
        else {
            return;
        }
        @error testSomething();
    }
    
    void switchCase2() {
        Boolean b = true;
        switch (b)
        case (true) {
            return;
        }
        case (false) {
        }
        //TODO: remove
        else {
            return;
        }
        testSomething();
    }
    
    void switchCase3() {
        Boolean b = true;
        switch (b)
        case (true) {
            return;
        }
        case (false) {
            return;
        }
        //TODO: remove
        else {
        }
        testSomething();
    }
    
}