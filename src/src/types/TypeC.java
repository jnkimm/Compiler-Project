package types;

public abstract class TypeC {

    public String message;
    
    public TypeC (String m) {
    	message = m;
    }

	// arithmetic
    public TypeC mul (TypeC that) {
        return new ErrorType("Cannot multiply " + this.message  + " with " + that.message  + ".");
    }

    public TypeC div (TypeC that) {
        return new ErrorType("Cannot divide " + this.message  + " by " + that.message  + ".");
    }

    public TypeC add (TypeC that) {
        return new ErrorType("Cannot add " + this.message  + " to " + that.message  + ".");
    }

    public TypeC sub (TypeC that) {
        return new ErrorType("Cannot subtract " + that.message  + " from " + this.message  + ".");
    }

    public TypeC pow (TypeC that) {
        return new ErrorType("Cannot take " + this.message  + " to the power of " + that.message  + ".");
    }

    // boolean
    public TypeC and (TypeC that) {
        return new ErrorType("Cannot compute " + this.message  + " and " + that.message  + ".");
    }

    public TypeC or (TypeC that) {
        return new ErrorType("Cannot compute " + this.message  + " or " + that.message  + ".");
    }

    public TypeC not () {
        return new ErrorType("Cannot negate " + this.message  + ".");
    }

    // relational
    public TypeC compare (TypeC that) {
        return new ErrorType("Cannot compare " + this.message  + " with " + that.message  + ".");
    }

    // designator
    public TypeC deref () {
        return new ErrorType("Cannot dereference " + this.message );
    }

    public TypeC index (TypeC that) {
        return new ErrorType("Cannot index " + this.message  + " with " + that.message  + ".");
    }

    // statements
    public TypeC assign (TypeC source) {
        return new ErrorType("Cannot assign " + this.message  + " to " + source.message  + ".");
    }

    public TypeC call (TypeC args) {
        return new ErrorType("Cannot call " + this.message  + " using " + args.message  + ".");
    }
    
    public TypeC mod (TypeC that) {
    	return new ErrorType("Cannot mod " + this.message  + " by " + that.message  + ".");
    }

}
