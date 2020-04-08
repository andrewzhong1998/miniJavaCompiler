/**
 * miniJava Abstract Syntax Tree classes
 * @author prins
 * @version COMP 520 (v2.2)
 */
package miniJava.AbstractSyntaxTrees;

import miniJava.SyntacticAnalyzer.SourcePosition;

public class BaseType extends TypeDenoter
{
    public BaseType(TypeKind t, SourcePosition posn){
        super(t, posn);
    }
    
    public <A,R> R visit(Visitor<A,R> v, A o) {
        return v.visitBaseType(this, o);
    }

    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }

        TypeDenoter type = (TypeDenoter) obj;
        if(type.typeKind == TypeKind.UNSUPPORTED || this.typeKind == TypeKind.UNSUPPORTED) {
            return false;
        }

         if (((TypeDenoter)obj).typeKind == TypeKind.ERROR || this.typeKind == TypeKind.ERROR) {
             return true;
         }

         if (type instanceof BaseType) {
             return this.typeKind == type.typeKind;
         }

         if (this.typeKind == TypeKind.ANY) {
             return type instanceof ClassType || type instanceof ArrayType;
         }

        return false;
    }
}
