void thisSuperOuter() {
    
    @error print(super.string);
    @error print(this.string);
    @error print(outer.string);
    @error value s = super;
    @error value o = outer;
    @error value t = this;
    
    class OuterClass() {
        void method() {
            print(super.string);
            print(this.string);
            @error print(outer.string);
            @error value ss = super;
            @error value oo = outer;
            @type:"OuterClass" value tt = this;
        }
        class InnerClass() {
            void method() {
                print(super.string);
                print(this.string);
                print(outer.string);
                @error value ss = super;
                @type:"OuterClass" value oo = outer;
                @type:"OuterClass.InnerClass" value tt = this;
            }
        }
    }

}

abstract class LeaksThis() extends Exception() 
satisfies Summable<LeaksThis>&Iterable<String> {
    
    @error string => (super of Exception).string;
    
    @error value r2 = this;
    @error print(this);
    print { @error val=this; };
    @error value r1 = (this of Object);
    @error print(this of Object);
    print { @error val=(this of Object); };
    
    value iterable1 = {@error this, this};
    value iterable2 = [@error this, this];
    value iterable4 = [@error *this];
    value iterable5 = [@error for (x in "") this];
    value iterable6 = [@error for (x in this) this];
    
    @error value f = () => this;
    @error function g() => this;
    function h() { @error return this of Object; }
    @error value z => (this);
    
    @error value xx = this+this;
    
    @error value str = "``this``";
    
    throw this;
}

abstract class LeaksSuper() extends Exception() 
satisfies Summable<LeaksSuper>&Iterable<String> {
    
    @error string => (super of Exception).string;
    
    @error value r2 = super;
    @error print(super);
    print { @error val=super; };
    @error value r1 = (super of Object);
    @error print(super of Object);
    print { @error val=(super of Object); };
    
    value iterable1 = {@error super, super};
    value iterable2 = [@error super, super];
    value iterable4 = [@error *super];
    value iterable5 = [@error for (x in "") super];
    value iterable6 = [@error for (x in super) super];
    
    @error value f = () => super;
    @error function g() => super;
    function h() { @error return super of Object; }
    @error value z => (super);
    
    @error value xx = super+super;
    
    @error value str = "``super``";

    @error throw super;
}